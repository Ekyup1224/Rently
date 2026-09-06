'use client'

import Link from 'next/link'
import { useAuth } from './AuthProvider'

/** Greets a signed-in guest, or nudges an anonymous one toward signing in. */
export function WelcomeBanner() {
  const { user, loading } = useAuth()

  if (loading || !user) {
    return null
  }

  return (
    <div className="alert alert--info">
      Signed in as <strong>{user.fullName ?? user.phone}</strong>.{' '}
      <Link href="/trips">View your trips</Link>.
    </div>
  )
}
