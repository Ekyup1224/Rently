'use client'

import Link from 'next/link'
import { useAuth } from './AuthProvider'

/** Placeholder for the Step 2 booking list, gated on being signed in. */
export function TripsView() {
  const { user, loading } = useAuth()

  if (loading) {
    return <p className="muted">Loading…</p>
  }

  if (!user) {
    return (
      <div className="card">
        <p style={{ marginTop: 0 }}>Sign in to see your bookings.</p>
        <Link href="/login" className="button">Sign in</Link>
      </div>
    )
  }

  return (
    <div className="card">
      <h2 className="card__title">No bookings yet</h2>
      <p className="muted small" style={{ marginTop: 0, marginBottom: 0 }}>
        Once booking goes live in Step 2, upcoming and past stays appear here with
        their price breakdown, cancellation window and host messages.
      </p>
    </div>
  )
}
