'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { api, describeError } from '@/lib/api'
import { formatDateRange, formatDateTime, formatMoney } from '@/lib/format'
import type { Booking, Payment } from '@/lib/types'
import { useLanguage } from '@/lib/i18n'
import { useAuth } from './AuthProvider'

/** How often to ask whether an out-of-band QR payment has settled. */
const POLL_INTERVAL_MS = 3000

/**
 * Payment for a booking.
 *
 * <p>QR rails settle out of band: the guest pays in their banking app and the
 * provider tells the server, not the browser. So this opens an invoice, shows the
 * QR material, and polls until the payment settles — which is also why a
 * countdown matters, since the hold on the dates expires.
 */
export function CheckoutView({ bookingId }: { bookingId: string }) {
  const { user, loading: authLoading } = useAuth()
  const { t, locale } = useLanguage()
  const [booking, setBooking] = useState<Booking | null>(null)
  const [payment, setPayment] = useState<Payment | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [settling, setSettling] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  useEffect(() => {
    if (authLoading || !user) {
      return
    }
    let cancelled = false

    async function load() {
      try {
        const [loadedBooking, loadedPayments] = await Promise.all([
          api.booking(bookingId),
          api.payments(bookingId),
        ])
        if (cancelled) {
          return
        }
        setBooking(loadedBooking)
        // The open charge, if there is one; otherwise the most recent attempt.
        setPayment(loadedPayments.find(
          (row) => row.intent === 'CHARGE' && row.status === 'PENDING')
          ?? loadedPayments.find((row) => row.intent === 'CHARGE')
          ?? null)
      } catch (failure) {
        if (!cancelled) {
          setError(describeError(failure))
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [bookingId, user, authLoading, reloadToken])

  // Poll while a charge is open. The provider notifies the server, so the browser
  // has no other way to learn that the guest has paid.
  useEffect(() => {
    if (!payment || payment.status !== 'PENDING' || booking?.status !== 'PENDING_PAYMENT') {
      return
    }
    const timer = setInterval(async () => {
      try {
        const latest = await api.payment(bookingId, payment.id)
        setPayment(latest)
        if (latest.status !== 'PENDING') {
          setBooking(await api.booking(bookingId))
        }
      } catch {
        // A transient failure should not break the page; the next tick retries.
      }
    }, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [bookingId, payment, booking?.status])

  async function startPayment() {
    setStarting(true)
    setError(null)
    try {
      setPayment(await api.startPayment(bookingId))
    } catch (failure) {
      setError(describeError(failure))
    } finally {
      setStarting(false)
    }
  }

  /** Development affordance: stands in for paying in a banking app. */
  async function simulate() {
    if (!payment) {
      return
    }
    setSettling(true)
    try {
      await api.simulateSettlement(bookingId, payment.id)
      reload()
    } catch (failure) {
      setError(describeError(failure))
    } finally {
      setSettling(false)
    }
  }

  if (authLoading) {
    return <p className="muted">{t('common.loading')}</p>
  }
  if (!user) {
    return (
      <div className="card">
        <p style={{ marginTop: 0 }}>{t('checkout.signInBody')}</p>
        <Link href="/login" className="button">{t('nav.signIn')}</Link>
      </div>
    )
  }
  if (error && !booking) {
    return <div className="alert alert--error">{error}</div>
  }
  if (!booking) {
    return <p className="muted">{t('checkout.loadingBooking')}</p>
  }

  const paid = booking.status === 'CONFIRMED' || booking.paymentStatus === 'PAID';
  const qrText = payment?.checkout?.['qrText'] as string | undefined
  const deeplinks = (payment?.checkout?.['deeplinks'] ?? []) as { name: string; link: string }[]
  const simulated = payment?.provider === 'SIMULATED'

  return (
    <>
      <h1>{paid ? t('checkout.booked') : t('checkout.confirmAndPay')}</h1>

      <div className="card">
        <h2 className="card__title">{booking.listing?.title ?? t('trips.yourStay')}</h2>
        <dl className="definition">
          <dt>{t('checkout.dates')}</dt>
          <dd>{formatDateRange(booking.checkIn, booking.checkOut, locale)}
            {' · '}{booking.nights} night{booking.nights > 1 ? 's' : ''}</dd>
          <dt>{t('search.guests')}</dt>
          <dd>{booking.guestCount}</dd>
          <dt>{t('checkout.reference')}</dt>
          <dd>{booking.reference}</dd>
          <dt>{t('book.total')}</dt>
          <dd><strong>{formatMoney(booking.total, booking.currency)}</strong></dd>
        </dl>
      </div>

      {paid && (
        <div className="card">
          <div className="alert alert--info" style={{ marginBottom: 12 }}>
            {t('checkout.paymentReceived')}
          </div>
          <Link href="/trips" className="button">{t('home.viewTrips')}</Link>
        </div>
      )}

      {!paid && booking.status !== 'PENDING_PAYMENT' && (
        <div className="card">
          <div className="alert alert--error" style={{ marginBottom: 0 }}>
            {t('checkout.notAwaiting', { status: t(`status.${booking.status}`) })}
          </div>
        </div>
      )}

      {!paid && booking.status === 'PENDING_PAYMENT' && (
        <div className="card">
          {booking.expiresAt && (
            <p className="muted small" style={{ marginTop: 0 }}>
              {t('checkout.heldUntil')}{' '}
              {formatDateTime(booking.expiresAt, locale)}.
            </p>
          )}

          {error && <div className="alert alert--error">{error}</div>}

          {!payment && (
            <button type="button" className="button button--block"
                    disabled={starting} onClick={startPayment}>
              {starting
                ? t('checkout.opening')
                : t('checkout.payAmount', {
                  amount: formatMoney(booking.total, booking.currency),
                })}
            </button>
          )}

          {payment?.status === 'PENDING' && (
            <>
              <h2 className="card__title">{t('checkout.scanToPay')}</h2>
              <p className="muted small" style={{ marginTop: 0 }}>
                {t('checkout.scanHelp')}
              </p>

              {qrText && (
                <pre
                  style={{ background: 'var(--ground)', border: '1px solid var(--line)',
                           borderRadius: 10, padding: 12, fontSize: 11, overflowX: 'auto' }}
                >
                  {qrText}
                </pre>
              )}

              {deeplinks.length > 0 && (
                <div className="row" style={{ marginBottom: 12 }}>
                  {deeplinks.map((bank) => (
                    <span key={bank.name} className="tag">{bank.name}</span>
                  ))}
                </div>
              )}

              <p className="muted small">{t('checkout.waiting')}</p>

              {simulated && (
                <>
                  <div className="alert alert--info">
                    {t('checkout.simulatedNotice')}
                  </div>
                  <button type="button" className="button button--block"
                          disabled={settling} onClick={simulate}>
                    {settling ? t('checkout.settling') : t('checkout.simulatePay')}
                  </button>
                </>
              )}
            </>
          )}

          {payment && payment.status === 'FAILED' && (
            <>
              <div className="alert alert--error">
                That payment did not go through{payment.failureMessage
                  ? `: ${payment.failureMessage}` : '.'}
              </div>
              <button type="button" className="button button--block" onClick={reload}>
                Try again
              </button>
            </>
          )}
        </div>
      )}
    </>
  )
}
