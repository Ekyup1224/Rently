'use client'

import Image from 'next/image'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useAuth } from './AuthProvider'
import { LanguageSwitch } from './LanguageSwitch'
import { useT } from '@/lib/i18n'

export function SiteHeader() {
  const { user, loading, signOut } = useAuth()
  const router = useRouter()
  const t = useT()

  return (
    <header className="site-header">
      <div className="site-header__inner">
        <Link href="/" className="brand">
          <Image src="/icon-192.png" alt="" width={28} height={28} className="brand__mark" priority />
          <span>Stay</span>
        </Link>

        <nav className="nav">
          <LanguageSwitch />
          {loading ? (
            <span className="muted small">…</span>
          ) : user ? (
            <>
              <Link href="/trips" className="button button--link">{t('nav.trips')}</Link>
              <Link href="/messages" className="button button--link">{t('nav.messages')}</Link>
              <Link href="/account" className="button button--link">{t('nav.account')}</Link>
              <button
                type="button"
                className="button button--ghost"
                onClick={async () => {
                  await signOut()
                  router.push('/')
                }}
              >
                {t('nav.signOut')}
              </button>
            </>
          ) : (
            <Link href="/login" className="button">{t('nav.signIn')}</Link>
          )}
        </nav>
      </div>
    </header>
  )
}
