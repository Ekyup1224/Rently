'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { api, describeError } from '@/lib/api'
import { addDays, formatMoney, nightsBetween, todayInUlaanbaatar } from '@/lib/format'
import type { ListingDetail, Quote } from '@/lib/types'
import { useAuth } from './AuthProvider'

/**
 * Date selection, live pricing and booking.
 *
 * <p>The total comes from the server's quote endpoint, never from arithmetic here:
 * per-night overrides, fees and the commission rule all live server-side, and the
 * same code prices the booking itself. Quoting is also what validates the stay, so
 * a minimum-stay or availability problem surfaces before the guest commits.
 */
export function BookingWidget({
  listing, initialCheckIn, initialCheckOut, initialGuests,
}: {
  listing: ListingDetail
  initialCheckIn: string
  initialCheckOut: string
  initialGuests: number
}) {
  const { user } = useAuth()
  const router = useRouter()
  const today = todayInUlaanbaatar()

  const [checkIn, setCheckIn] = useState(initialCheckIn)
  const [checkOut, setCheckOut] = useState(initialCheckOut)
  const [guests, setGuests] = useState(Math.min(initialGuests, listing.maxGuests))
  const [message, setMessage] = useState('')
  const [quote, setQuote] = useState<Quote | null>(null)
  const [quoteError, setQuoteError] = useState<string | null>(null)
  const [quoting, setQuoting] = useState(false)
  const [booking, setBooking] = useState(false)
  const [bookingError, setBookingError] = useState<string | null>(null)

  const nights = checkIn && checkOut ? nightsBetween(checkIn, checkOut) : 0
  const mixedNightlyRates = quote
    ? new Set(quote.nightlyRates.map((rate) => rate.amount)).size > 1
    : false

  // Re-quote whenever the stay changes. The server is the only thing that knows
  // what these dates cost, or whether they are even bookable.
  useEffect(() => {
    let cancelled = false

    async function loadQuote() {
      if (!checkIn || !checkOut || nights < 1) {
        setQuote(null)
        setQuoteError(null)
        return
      }
      setQuoting(true)
      setQuoteError(null)
      try {
        const priced = await api.quote(listing.id, { checkIn, checkOut, guests })
        if (!cancelled) {
          setQuote(priced)
        }
      } catch (failure) {
        if (!cancelled) {
          setQuote(null)
          setQuoteError(describeError(failure))
        }
      } finally {
        if (!cancelled) {
          setQuoting(false)
        }
      }
    }

    loadQuote()
    return () => {
      cancelled = true
    }
  }, [listing.id, checkIn, checkOut, guests, nights])

  async function submit() {
    setBooking(true)
    setBookingError(null)
    try {
      const created = await api.book({
        propertyId: listing.id,
        checkIn,
        checkOut,
        guests,
        message: message || undefined,
      })
      // Instant-book goes straight to payment; a request waits for the host, and
      // the trips page is where its progress lives.
      router.push(created.status === 'PENDING_PAYMENT'
        ? `/bookings/${created.id}/checkout`
        : '/trips')
    } catch (failure) {
      setBookingError(describeError(failure))
    } finally {
      setBooking(false)
    }
  }

  return (
    <div className="card">
      <h2 className="card__title">
        {formatMoney(listing.nightlyFrom, listing.currency)}
        <span className="muted small" style={{ fontWeight: 400 }}> / night</span>
      </h2>

      <div className="grid-2">
        <label className="field">
          <span className="field__label">Check in</span>
          <input
            type="date"
            min={today}
            value={checkIn}
            onChange={(event) => {
              const value = event.target.value
              setCheckIn(value)
              if (value && (!checkOut || checkOut <= value)) {
                setCheckOut(addDays(value, Math.max(1, listing.minStayNights)))
              }
            }}
          />
        </label>
        <label className="field">
          <span className="field__label">Check out</span>
          <input
            type="date"
            min={checkIn ? addDays(checkIn, 1) : addDays(today, 1)}
            value={checkOut}
            onChange={(event) => setCheckOut(event.target.value)}
          />
        </label>
      </div>

      <label className="field">
        <span className="field__label">Guests</span>
        <select value={guests} onChange={(event) => setGuests(Number(event.target.value))}>
          {Array.from({ length: listing.maxGuests }, (_, index) => index + 1).map((count) => (
            <option key={count} value={count}>
              {count} guest{count > 1 ? 's' : ''}
            </option>
          ))}
        </select>
      </label>

      {!listing.instantBook && (
        <label className="field">
          <span className="field__label">Message to the host (optional)</span>
          <textarea
            rows={3}
            maxLength={2000}
            placeholder="Tell them a little about your trip"
            value={message}
            onChange={(event) => setMessage(event.target.value)}
          />
        </label>
      )}

      {quoting && <p className="muted small">Checking availability…</p>}
      {quoteError && <div className="alert alert--error">{quoteError}</div>}

      {quote && (
        <div style={{ borderTop: '1px solid var(--line)', paddingTop: 12, marginBottom: 12 }}>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <span>
              {mixedNightlyRates
                // Averaging would print a multiplication that does not equal the
                // subtotal, which is worse than not showing one. The per-night
                // list below carries the detail instead.
                ? `${quote.nights} night${quote.nights > 1 ? 's' : ''}`
                : `${formatMoney(quote.nightlyRates[0].amount, quote.currency)} × `
                  + `${quote.nights} night${quote.nights > 1 ? 's' : ''}`}
            </span>
            <span>{formatMoney(quote.nightlySubtotal, quote.currency)}</span>
          </div>

          {mixedNightlyRates && (
            <details style={{ marginTop: 6 }}>
              <summary className="muted small" style={{ cursor: 'pointer' }}>
                Some nights are priced differently
              </summary>
              <ul className="small muted" style={{ paddingInlineStart: 18, margin: '4px 0 0' }}>
                {quote.nightlyRates.map((rate) => (
                  <li key={rate.date}>
                    {new Date(rate.date + 'T00:00:00').toLocaleDateString('en-US',
                      { weekday: 'short', day: 'numeric', month: 'short' })}
                    {' — '}{formatMoney(rate.amount, quote.currency)}
                    {rate.overridden ? ' (special rate)' : ''}
                  </li>
                ))}
              </ul>
            </details>
          )}
          {quote.cleaningFee > 0 && (
            <div className="row" style={{ justifyContent: 'space-between', marginTop: 6 }}>
              <span>Cleaning fee</span>
              <span>{formatMoney(quote.cleaningFee, quote.currency)}</span>
            </div>
          )}
          {quote.guestServiceFee > 0 && (
            <div className="row" style={{ justifyContent: 'space-between', marginTop: 6 }}>
              <span>Service fee</span>
              <span>{formatMoney(quote.guestServiceFee, quote.currency)}</span>
            </div>
          )}
          {quote.tax > 0 && (
            <div className="row" style={{ justifyContent: 'space-between', marginTop: 6 }}>
              <span>Tax</span>
              <span>{formatMoney(quote.tax, quote.currency)}</span>
            </div>
          )}
          <div
            className="row"
            style={{ justifyContent: 'space-between', marginTop: 10, paddingTop: 10,
                     borderTop: '1px solid var(--line)', fontWeight: 650 }}
          >
            <span>Total</span>
            <span>{formatMoney(quote.total, quote.currency)}</span>
          </div>

          <details style={{ marginTop: 10 }}>
            <summary className="muted small" style={{ cursor: 'pointer' }}>
              Cancellation terms ({quote.cancellationPolicy.toLowerCase()})
            </summary>
            <ul className="small muted" style={{ paddingInlineStart: 18, marginBottom: 0 }}>
              {quote.refundSchedule.map((window) => (
                <li key={window.cancelBefore}>
                  {window.description} — {formatMoney(window.refundAmount, quote.currency)} back
                </li>
              ))}
            </ul>
          </details>
        </div>
      )}

      {bookingError && <div className="alert alert--error">{bookingError}</div>}

      {user ? (
        <button
          type="button"
          className="button button--block"
          disabled={!quote || booking || quoting}
          onClick={submit}
        >
          {booking
            ? 'Working…'
            : listing.instantBook ? 'Book now' : 'Request to book'}
        </button>
      ) : (
        <Link href="/login" className="button button--block">Sign in to book</Link>
      )}

      <p className="muted small" style={{ marginTop: 8, marginBottom: 0 }}>
        {listing.instantBook
          ? 'You will pay in the next step. Nothing is charged until then.'
          : 'The host has 24 hours to respond. You only pay once they accept.'}
      </p>
    </div>
  )
}
