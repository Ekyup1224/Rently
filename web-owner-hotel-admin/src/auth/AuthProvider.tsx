import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { auth } from '../api/endpoints'
import {
  getRefreshToken, refreshSession, setAccessToken, setRefreshToken, setSessionLostHandler,
} from '../api/client'
import type { AuthSession, Role, User } from '../types'

interface AuthState {
  user: User | null
  /** True until the stored refresh token has been checked, so routes do not flash. */
  initializing: boolean
  roles: Role[]
  hasRole: (...candidates: Role[]) => boolean
  adopt: (session: AuthSession) => void
  signOut: () => Promise<void>
  reload: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [initializing, setInitializing] = useState(true)

  const clearSession = useCallback(() => {
    setAccessToken(null)
    setRefreshToken(null)
    setUser(null)
  }, [])

  // Resume the session on a page load: the access token lives only in memory, so a
  // reload always starts by trading the stored refresh token for a new one.
  useEffect(() => {
    let cancelled = false

    async function resume() {
      if (!getRefreshToken()) {
        setInitializing(false)
        return
      }
      try {
        const refreshed = await refreshSession()
        if (!cancelled && refreshed) {
          setUser(await auth.me())
        } else if (!cancelled) {
          clearSession()
        }
      } catch {
        if (!cancelled) {
          clearSession()
        }
      } finally {
        if (!cancelled) {
          setInitializing(false)
        }
      }
    }

    resume()
    return () => {
      cancelled = true
    }
  }, [clearSession])

  // A refresh failure anywhere in the app drops us back to signed-out state.
  useEffect(() => {
    setSessionLostHandler(() => clearSession())
    return () => setSessionLostHandler(null)
  }, [clearSession])

  const adopt = useCallback((session: AuthSession) => {
    setAccessToken(session.accessToken)
    setRefreshToken(session.refreshToken)
    setUser(session.user)
  }, [])

  const signOut = useCallback(async () => {
    const refreshToken = getRefreshToken()
    if (refreshToken) {
      // Best effort: the local session is dropped even if the server call fails.
      try {
        await auth.logout(refreshToken)
      } catch {
        /* ignored */
      }
    }
    clearSession()
  }, [clearSession])

  const reload = useCallback(async () => {
    setUser(await auth.me())
  }, [])

  const value = useMemo<AuthState>(() => {
    const roles = user?.roles.map((grant) => grant.role) ?? []
    return {
      user,
      initializing,
      roles,
      hasRole: (...candidates: Role[]) => candidates.some((role) => roles.includes(role)),
      adopt,
      signOut,
      reload,
    }
  }, [user, initializing, adopt, signOut, reload])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
