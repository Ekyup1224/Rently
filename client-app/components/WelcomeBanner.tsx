'use client'

import Link from 'next/link'
import { useAuth } from './AuthProvider'
import { useT } from '@/lib/i18n'

/** Greets a signed-in guest, or nudges an anonymous one toward signing in. */
export function WelcomeBanner() {
  const { user, loading } = useAuth()
  const t = useT()

  if (loading || !user) {
    return null
  }

  return (
    <div className="alert alert--info">
      {t('home.welcome', { name: user.fullName ?? user.phone })}{' '}
      <Link href="/trips">{t('home.viewTrips')}</Link>
    </div>
  )
}
