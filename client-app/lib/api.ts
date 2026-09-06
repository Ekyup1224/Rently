import type { ApiError, AuthSession, HostApplication, OtpChallenge, User } from './types'

const BASE = '/api/v1'
const REFRESH_STORAGE_KEY = 'stay.refreshToken'

/**
 * Access token in memory, refresh token in localStorage.
 *
 * <p>Keeping the access token out of storage means a stolen storage dump is not a
 * live session. The refresh token has to outlive a reload, so it is XSS-reachable;
 * server-side rotation and replay detection are what limit the damage. An HttpOnly
 * cookie is the next hardening step.
 */
let accessToken: string | null = null
let onSessionLost: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getRefreshToken(): string | null {
  // Guards against server-side rendering, where localStorage does not exist.
  return typeof window === 'undefined' ? null : window.localStorage.getItem(REFRESH_STORAGE_KEY)
}

export function setRefreshToken(token: string | null) {
  if (typeof window === 'undefined') {
    return
  }
  if (token) {
    window.localStorage.setItem(REFRESH_STORAGE_KEY, token)
  } else {
    window.localStorage.removeItem(REFRESH_STORAGE_KEY)
  }
}

export function setSessionLostHandler(handler: (() => void) | null) {
  onSessionLost = handler
}

export class RequestError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, error: ApiError) {
    super(error.message || 'Request failed')
    this.status = status
    this.code = error.code || 'unknown_error'
  }
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

// Concurrent refreshes would rotate the token repeatedly and trip the server's
// replay detection, so callers share one in-flight attempt.
let refreshInFlight: Promise<boolean> | null = null

export function refreshSession(): Promise<boolean> {
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
    const session = (await response.json()) as AuthSession
    setAccessToken(session.accessToken)
    setRefreshToken(session.refreshToken)
    return true
  })().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; anonymous?: boolean } = {},
): Promise<T> {
  const init: RequestInit = {
    method: options.method ?? 'GET',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  }
  const withAuth = !options.anonymous

  let response = await send(path, init, withAuth)

  if (response.status === 401 && withAuth && getRefreshToken()) {
    if (await refreshSession()) {
      response = await send(path, init, true)
    } else {
      onSessionLost?.()
    }
  }

  if (!response.ok) {
    if (response.status === 401 && withAuth) {
      onSessionLost?.()
    }
    let body: ApiError
    try {
      body = (await response.json()) as ApiError
    } catch {
      body = { code: 'unknown_error', message: response.statusText, timestamp: '' }
    }
    throw new RequestError(response.status, body)
  }

  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

const DEVICE_LABEL = 'Guest app'

export const api = {
  requestOtp: (phone: string, locale = 'mn') =>
    request<OtpChallenge>('/auth/otp/request', {
      method: 'POST', body: { phone, locale }, anonymous: true,
    }),

  verifyOtp: (phone: string, code: string) =>
    request<AuthSession>('/auth/otp/verify', {
      method: 'POST', body: { phone, code, deviceLabel: DEVICE_LABEL }, anonymous: true,
    }),

  logout: (refreshToken: string) =>
    request<void>('/auth/logout', { method: 'POST', body: { refreshToken } }),

  me: () => request<User>('/auth/me'),

  updateProfile: (patch: { fullName?: string; email?: string; locale?: string }) =>
    request<User>('/users/me', { method: 'PATCH', body: patch }),

  listHostApplications: () => request<HostApplication[]>('/users/me/host-applications'),

  applyAsHost: (body: {
    requestedRole: 'HOUSE_OWNER' | 'HOTEL_MANAGER'
    organizationName?: string
    organizationRegistrationNo?: string
    note?: string
  }) => request<HostApplication>('/users/me/host-applications', { method: 'POST', body }),

  withdrawHostApplication: (applicationId: string) =>
    request<HostApplication>(`/users/me/host-applications/${applicationId}/withdraw`, {
      method: 'POST',
    }),
}

/** Maps a backend error code to guest-facing copy. */
export function describeError(failure: unknown): string {
  if (!(failure instanceof RequestError)) {
    return 'Could not reach the server. Check your connection and try again.'
  }
  switch (failure.code) {
    case 'invalid_phone':
      return 'That does not look like a valid phone number.'
    case 'otp_invalid':
      return 'That code is incorrect.'
    case 'otp_expired':
      return 'That code has expired. Request a new one.'
    case 'otp_cooldown':
      return 'A code was just sent. Wait a moment before asking for another.'
    case 'otp_rate_limited':
      return 'Too many codes requested for this number. Try again in an hour.'
    case 'otp_locked':
      return 'Too many incorrect codes. This number is locked for a short while.'
    case 'account_not_active':
      return 'This account cannot sign in. Contact support.'
    case 'email_taken':
      return 'That email is already in use.'
    case 'application_pending':
      return 'You already have an application under review.'
    case 'role_already_held':
      return 'You already have this role.'
    case 'organization_name_required':
      return 'Enter the registered business name.'
    default:
      return failure.message
  }
}
