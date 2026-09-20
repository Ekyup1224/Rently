'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { WriteReview } from './WriteReview'
import { useLanguage } from '@/lib/i18n'
import { api, describeError } from '@/lib/api'
import { formatDateRange, formatMoney } from '@/lib/format'
import type { Booking, BookingStatus } from '@/lib/types'
import { useAuth } from './AuthProvider'

const SCOPES = ['upcoming', 'past', 'cancelled', 'all'] as const

/** A guest's bookings, with whatever action each one is waiting on. */
export function TripsView() {
  const { user, loading } = useAuth()
  const { t, locale } = useLanguage()
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
    return <p className="muted">{t('common.loading')}</p>
  }

  if (!user) {
    return (
      <div className="card">
        <p style={{ marginTop: 0 }}>{t('trips.signInBody')}</p>
        <Link href="/login" className="button">{t('nav.signIn')}</Link>
      </div>
    )
  }

  return (
    <>
      <h1>{t('trips.title')}</h1>
      <div className="segmented" role="group" aria-label={t('trips.which')}>
        {SCOPES.map((option) => (
          <button
            key={option}
            type="button"
            aria-pressed={scope === option}
            onClick={() => setScope(option)}
          >
            {t(`trips.${option}`)}
          </button>
        ))}
      </div>

      {error && <div className="alert alert--error">{error}</div>}
      {trips === null && <p className="muted">{t('common.loading')}</p>}

      {trips?.length === 0 && (
        <div className="card">
          <h2 className="card__title">{t('trips.nothingHere')}</h2>
          <p className="muted small" style={{ marginTop: 0, marginBottom: 12 }}>
            {scope === 'upcoming' ? t('trips.noUpcoming') : t('trips.noneInList')}
          </p>
          <Link href="/search" className="button">{t('trips.find')}</Link>
        </div>
      )}

      {trips?.map((trip) => {
        const cancellable = ['PENDING_HOST_APPROVAL', 'PENDING_PAYMENT', 'CONFIRMED']
          .includes(trip.status)
        const reviewable = ['CHECKED_OUT', 'COMPLETED'].includes(trip.status)
        // A thread only helps once there is a stay to talk about.
        const messageable = !['PENDING_PAYMENT', 'DECLINED', 'EXPIRED'].includes(trip.status)

        return (
          <div className="card" key={trip.id}>
            <div className="row" style={{ justifyContent: 'space-between', gap: 12 }}>
              <div style={{ minWidth: 0 }}>
                <div className="row" style={{ gap: 8, alignItems: 'center' }}>
                  <strong>{trip.listing?.title ?? t('trips.yourStay')}</strong>
                  <span className="tag">{t(`status.${trip.status}`)}</span>
                </div>
                {trip.listing?.roomTypeName && (
                  <p className="small" style={{ margin: '4px 0 0' }}>
                    {trip.listing.roomTypeName}
                    {trip.listing.rooms && trip.listing.rooms > 1
                      && ` · ${t('trips.roomsCount', { count: trip.listing.rooms })}`}
                  </p>
                )}
                <p className="muted small" style={{ margin: '4px 0' }}>
                  {formatDateRange(trip.checkIn, trip.checkOut, locale)}
                  {' · '}{trip.nights === 1
                    ? t('common.night_one') : t('common.nights', { count: trip.nights })}
                  {' · '}{trip.guestCount === 1
                    ? t('common.guest_one') : t('common.guests', { count: trip.guestCount })}
                </p>
                <p className="small" style={{ margin: 0 }}>
                  {formatMoney(trip.total, trip.currency)}
                  <span className="muted"> · {trip.reference}</span>
                </p>
                {trip.hostResponseNote && (
                  <p className="muted small" style={{ margin: '6px 0 0' }}>
                    {t('trips.hostSaid', { note: trip.hostResponseNote })}
                  </p>
                )}
                {trip.refundAmount != null && trip.refundAmount > 0 && (
                  <p className="muted small" style={{ margin: '6px 0 0' }}>
                    {t('trips.refunded', {
                      amount: formatMoney(trip.refundAmount, trip.currency),
                    })}
                  </p>
                )}
              </div>

              <div className="row" style={{ flexDirection: 'column', gap: 6, minWidth: 150 }}>
                {trip.status === 'PENDING_PAYMENT' && (
                  <Link href={`/bookings/${trip.id}/checkout`}
                        className="button button--block">
                    {t('trips.payNow')}
                  </Link>
                )}
                {trip.listing && (
                  <Link
                    href={trip.listing.supplyType === 'HOTEL'
                      ? `/hotels/${trip.listing.id}`
                      : `/listings/${trip.listing.id}`}
                    className="button button--ghost button--block"
                  >
                    {trip.listing.supplyType === 'HOTEL'
                      ? t('trips.viewHotel') : t('trips.viewListing')}
                  </Link>
                )}
                {messageable && (
                  <Link
                    href={`/messages?booking=${trip.id}`}
                    className="button button--ghost button--block"
                  >
                    {t('messages.open')}
                  </Link>
                )}
                {cancellable && (
                  <button
                    type="button"
                    className="button button--ghost button--block"
                    disabled={busyId === trip.id}
                    onClick={() => cancel(trip)}
                  >
                    {busyId === trip.id ? t('trips.cancelling') : t('trips.cancel')}
                  </button>
                )}
              </div>
            </div>
            {reviewable && <WriteReview bookingId={trip.id} />}
          </div>
        )
      })}
    </>
  )
}
