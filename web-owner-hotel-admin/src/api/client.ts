import type { ApiError } from '../types'

const BASE = '/api/v1'
const REFRESH_STORAGE_KEY = 'stay.refreshToken'

/**
 * The access token is held in memory only, so a stolen localStorage dump cannot
 * be replayed as a live session. The refresh token does have to survive a reload,
 * which means it is XSS-reachable; that is the standard SPA trade-off, and it is
 * mitigated by rotation plus replay detection on the server. Moving it to an
 * HttpOnly cookie is the next step up in hardening.
 */
let accessToken: string | null = null

/** Set by the auth provider so a failed refresh can bounce the user to the login screen. */
let onSessionLost: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_STORAGE_KEY)
}

export function setRefreshToken(token: string | null) {
  if (token) {
    localStorage.setItem(REFRESH_STORAGE_KEY, token)
  } else {
    localStorage.removeItem(REFRESH_STORAGE_KEY)
  }
}

export function setSessionLostHandler(handler: (() => void) | null) {
  onSessionLost = handler
}

/** Thrown for any non-2xx response, carrying the backend's error code. */
export class RequestError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors?: { field: string; message: string }[]

  constructor(status: number, error: ApiError) {
    super(error.message || 'Request failed')
    this.status = status
    this.code = error.code || 'unknown_error'
    this.fieldErrors = error.fieldErrors
  }
}

async function parseError(response: Response): Promise<RequestError> {
  let body: ApiError
  try {
    body = (await response.json()) as ApiError
  } catch {
    body = { code: 'unknown_error', message: response.statusText, timestamp: '' }
  }
  return new RequestError(response.status, body)
}

async function send(path: string, init: RequestInit, withAuth: boolean): Promise<Response> {
  const headers = new Headers(init.headers)
  if (init.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (withAuth && accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return fetch(BASE + path, { ...init, headers })
}

/**
 * Exchanges the stored refresh token for a new pair. Concurrent callers share one
 * in-flight request: firing several would rotate the token repeatedly and trip the
 * server's replay detection, killing the session we are trying to keep alive.
 */
let refreshInFlight: Promise<boolean> | null = null

function refreshSession(): Promise<boolean> {
  if (refreshInFlight) {
    return refreshInFlight
  }
  refreshInFlight = (async () => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      return false
    }
    const response = await send('/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    }, false)
    if (!response.ok) {
      setAccessToken(null)
      setRefreshToken(null)
      return false
    }
    const session = await response.json()
    setAccessToken(session.accessToken)
    setRefreshToken(session.refreshToken)
    return true
  })().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

interface RequestOptions {
  method?: string
  body?: unknown
  /** Skip the bearer token and the refresh-and-retry behaviour (used by the auth calls). */
  anonymous?: boolean
}

/**
 * Performs an API call, transparently refreshing an expired access token once.
 *
 * @throws RequestError on any non-2xx response
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const init: RequestInit = {
    method: options.method ?? 'GET',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  }
  const withAuth = !options.anonymous

  let response = await send(path, init, withAuth)

  if (response.status === 401 && withAuth && getRefreshToken()) {
    const refreshed = await refreshSession()
    if (refreshed) {
      response = await send(path, init, true)
    } else {
      onSessionLost?.()
    }
  }

  if (!response.ok) {
    if (response.status === 401 && withAuth) {
      onSessionLost?.()
    }
    throw await parseError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export { refreshSession }
