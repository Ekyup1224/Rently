'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { api, describeError } from '@/lib/api'
import { formatDateRange, formatMoney } from '@/lib/format'
import type { Booking, BookingStatus } from '@/lib/types'
import { useAuth } from './AuthProvider'

const SCOPES = [
  { value: 'upcoming', label: 'Upcoming' },
  { value: 'past', label: 'Past' },
  { value: 'cancelled', label: 'Cancelled' },
  { value: 'all', label: 'All' },
]

const STATUS_COPY: Record<BookingStatus, string> = {
  PENDING_HOST_APPROVAL: 'Waiting for the host',
  PENDING_PAYMENT: 'Payment needed',
  CONFIRMED: 'Confirmed',
  CHECKED_IN: 'Checked in',
  CHECKED_OUT: 'Checked out',
  COMPLETED: 'Completed',
  DECLINED: 'Declined by host',
  EXPIRED: 'Expired',
  CANCELLED_BY_GUEST: 'You cancelled',
  CANCELLED_BY_HOST: 'Host cancelled',
}

/** A guest's bookings, with whatever action each one is waiting on. */
export function TripsView() {
  const { user, loading } = useAuth()
  const [scope, setScope] = useState('upcoming')
  const [trips, setTrips] = useState<Booking[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  useEffect(() => {
    if (loading || !user) {
      return
    }
    let cancelled = false

    async function load() {
      try {
        const page = await api.bookings(scope)
        if (!cancelled) {
          setTrips(page.rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setTrips([])
          setError(describeError(failure))
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [scope, user, loading, reloadToken])

  async function cancel(booking: Booking) {
    setBusyId(booking.id)
    setError(null)
    try {
      await api.cancelBooking(booking.id, 'Cancelled from trips')
      reload()
    } catch (failure) {
      setError(describeError(failure))
    } finally {
      setBusyId(null)
    }
  }

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
    <>
      <div className="segmented" role="group" aria-label="Which trips">
        {SCOPES.map((option) => (
          <button
            key={option.value}
            type="button"
            aria-pressed={scope === option.value}
            onClick={() => setScope(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>

      {error && <div className="alert alert--error">{error}</div>}
      {trips === null && <p className="muted">Loading…</p>}

      {trips?.length === 0 && (
        <div className="card">
          <h2 className="card__title">Nothing here</h2>
          <p className="muted small" style={{ marginTop: 0, marginBottom: 12 }}>
            {scope === 'upcoming'
              ? 'You have no upcoming stays.'
              : 'Nothing in this list yet.'}
          </p>
          <Link href="/search" className="button">Find a stay</Link>
        </div>
      )}

      {trips?.map((trip) => {
        const cancellable = ['PENDING_HOST_APPROVAL', 'PENDING_PAYMENT', 'CONFIRMED']
          .includes(trip.status)

        return (
          <div className="card" key={trip.id}>
            <div className="row" style={{ justifyContent: 'space-between', gap: 12 }}>
              <div style={{ minWidth: 0 }}>
                <div className="row" style={{ gap: 8, alignItems: 'center' }}>
                  <strong>{trip.listing?.title ?? 'Your stay'}</strong>
                  <span className="tag">{STATUS_COPY[trip.status]}</span>
                </div>
                <p className="muted small" style={{ margin: '4px 0' }}>
                  {formatDateRange(trip.checkIn, trip.checkOut)} · {trip.nights} night
                  {trip.nights > 1 ? 's' : ''} · {trip.guestCount} guest
                  {trip.guestCount > 1 ? 's' : ''}
                </p>
                <p className="small" style={{ margin: 0 }}>
                  {formatMoney(trip.total, trip.currency)}
                  <span className="muted"> · {trip.reference}</span>
                </p>
                {trip.hostResponseNote && (
                  <p className="muted small" style={{ margin: '6px 0 0' }}>
                    Host: “{trip.hostResponseNote}”
                  </p>
                )}
                {trip.refundAmount != null && trip.refundAmount > 0 && (
                  <p className="muted small" style={{ margin: '6px 0 0' }}>
                    Refunded {formatMoney(trip.refundAmount, trip.currency)}
                  </p>
                )}
              </div>

              <div className="row" style={{ flexDirection: 'column', gap: 6, minWidth: 150 }}>
                {trip.status === 'PENDING_PAYMENT' && (
                  <Link href={`/bookings/${trip.id}/checkout`}
                        className="button button--block">
                    Pay now
                  </Link>
                )}
                {trip.listing && (
                  <Link href={`/listings/${trip.listing.id}`}
                        className="button button--ghost button--block">
                    View listing
                  </Link>
                )}
                {cancellable && (
                  <button
                    type="button"
                    className="button button--ghost button--block"
                    disabled={busyId === trip.id}
                    onClick={() => cancel(trip)}
                  >
                    {busyId === trip.id ? 'Cancelling…' : 'Cancel'}
                  </button>
                )}
              </div>
            </div>
          </div>
        )
      })}
    </>
  )
}
