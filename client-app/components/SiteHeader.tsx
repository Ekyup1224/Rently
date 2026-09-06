'use client'

import Image from 'next/image'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useAuth } from './AuthProvider'

export function SiteHeader() {
  const { user, loading, signOut } = useAuth()
  const router = useRouter()

  return (
    <header className="site-header">
      <div className="site-header__inner">
        <Link href="/" className="brand">
          <Image src="/icon-192.png" alt="" width={28} height={28} className="brand__mark" priority />
          <span>Stay</span>
        </Link>

        <nav className="nav">
          {loading ? (
            <span className="muted small">…</span>
          ) : user ? (
            <>
              <Link href="/trips" className="button button--link">Trips</Link>
              <Link href="/account" className="button button--link">Account</Link>
              <button
                type="button"
                className="button button--ghost"
                onClick={async () => {
                  await signOut()
                  router.push('/')
                }}
              >
                Sign out
              </button>
            </>
          ) : (
            <Link href="/login" className="button">Sign in</Link>
          )}
        </nav>
      </div>
    </header>
  )
}
