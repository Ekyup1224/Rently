'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { api, describeError } from '@/lib/api'
import { formatDateRange, formatMoney } from '@/lib/format'
import type { Booking, Payment } from '@/lib/types'
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
    return <p className="muted">Loading…</p>
  }
  if (!user) {
    return (
      <div className="card">
        <p style={{ marginTop: 0 }}>Sign in to complete this booking.</p>
        <Link href="/login" className="button">Sign in</Link>
      </div>
    )
  }
  if (error && !booking) {
    return <div className="alert alert--error">{error}</div>
  }
  if (!booking) {
    return <p className="muted">Loading your booking…</p>
  }

  const paid = booking.status === 'CONFIRMED' || booking.paymentStatus === 'PAID';
  const qrText = payment?.checkout?.['qrText'] as string | undefined
  const deeplinks = (payment?.checkout?.['deeplinks'] ?? []) as { name: string; link: string }[]
  const simulated = payment?.provider === 'SIMULATED'

  return (
    <>
      <h1>{paid ? 'You are booked' : 'Confirm and pay'}</h1>

      <div className="card">
        <h2 className="card__title">{booking.listing?.title ?? 'Your stay'}</h2>
        <dl className="definition">
          <dt>Dates</dt>
          <dd>{formatDateRange(booking.checkIn, booking.checkOut)}
            {' · '}{booking.nights} night{booking.nights > 1 ? 's' : ''}</dd>
          <dt>Guests</dt>
          <dd>{booking.guestCount}</dd>
          <dt>Reference</dt>
          <dd>{booking.reference}</dd>
          <dt>Total</dt>
          <dd><strong>{formatMoney(booking.total, booking.currency)}</strong></dd>
        </dl>
      </div>

      {paid && (
        <div className="card">
          <div className="alert alert--info" style={{ marginBottom: 12 }}>
            Payment received. Your stay is confirmed and the host has been told.
          </div>
          <Link href="/trips" className="button">View your trips</Link>
        </div>
      )}

      {!paid && booking.status !== 'PENDING_PAYMENT' && (
        <div className="card">
          <div className="alert alert--error" style={{ marginBottom: 0 }}>
            This booking is no longer awaiting payment (it is {booking.status
              .toLowerCase().replace(/_/g, ' ')}). Nothing has been charged.
          </div>
        </div>
      )}

      {!paid && booking.status === 'PENDING_PAYMENT' && (
        <div className="card">
          {booking.expiresAt && (
            <p className="muted small" style={{ marginTop: 0 }}>
              These dates are held for you until{' '}
              {new Date(booking.expiresAt).toLocaleString()}.
            </p>
          )}

          {error && <div className="alert alert--error">{error}</div>}

          {!payment && (
            <button type="button" className="button button--block"
                    disabled={starting} onClick={startPayment}>
              {starting ? 'Opening…' : `Pay ${formatMoney(booking.total, booking.currency)}`}
            </button>
          )}

          {payment?.status === 'PENDING' && (
            <>
              <h2 className="card__title">Scan to pay</h2>
              <p className="muted small" style={{ marginTop: 0 }}>
                Open your banking app and scan, or pick your bank below. This page
                updates by itself once the payment lands.
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

              <p className="muted small">Waiting for payment…</p>

              {simulated && (
                <>
                  <div className="alert alert--info">
                    This is a simulated payment provider, standing in until the QPay
                    merchant account is live. No money moves.
                  </div>
                  <button type="button" className="button button--block"
                          disabled={settling} onClick={simulate}>
                    {settling ? 'Settling…' : 'Simulate a successful payment'}
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
