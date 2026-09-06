'use client'

import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
} from 'react'
import type { ReactNode } from 'react'
import {
  api, getRefreshToken, refreshSession, setAccessToken, setRefreshToken, setSessionLostHandler,
} from '@/lib/api'
import type { AuthSession, User } from '@/lib/types'

interface AuthState {
  user: User | null
  /** True until the stored refresh token has been checked on load. */
  loading: boolean
  adopt: (session: AuthSession) => void
  signOut: () => Promise<void>
  reload: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const clear = useCallback(() => {
    setAccessToken(null)
    setRefreshToken(null)
    setUser(null)
  }, [])

  // The access token is memory-only, so every page load starts by trading the
  // stored refresh token for a fresh pair.
  useEffect(() => {
    let cancelled = false

    async function resume() {
      if (!getRefreshToken()) {
        setLoading(false)
        return
      }
      try {
        if (await refreshSession()) {
          const me = await api.me()
          if (!cancelled) {
            setUser(me)
          }
        } else if (!cancelled) {
          clear()
        }
      } catch {
        if (!cancelled) {
          clear()
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    resume()
    return () => {
      cancelled = true
    }
  }, [clear])

  useEffect(() => {
    setSessionLostHandler(clear)
    return () => setSessionLostHandler(null)
  }, [clear])

  const adopt = useCallback((session: AuthSession) => {
    setAccessToken(session.accessToken)
    setRefreshToken(session.refreshToken)
    setUser(session.user)
  }, [])

  const signOut = useCallback(async () => {
    const refreshToken = getRefreshToken()
    if (refreshToken) {
      try {
        await api.logout(refreshToken)
      } catch {
        // The local session is dropped regardless.
      }
    }
    clear()
  }, [clear])

  const reload = useCallback(async () => {
    setUser(await api.me())
  }, [])

  const value = useMemo<AuthState>(
    () => ({ user, loading, adopt, signOut, reload }),
    [user, loading, adopt, signOut, reload],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
