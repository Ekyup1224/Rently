'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { api, describeError } from '@/lib/api'
import { addDays, formatMoney, nightsBetween, todayInUlaanbaatar } from '@/lib/format'
import type { ListingDetail, Quote } from '@/lib/types'
import { useT } from '@/lib/i18n'
import { useAuth } from './AuthProvider'
import { QuoteBreakdown } from './QuoteBreakdown'

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
  const t = useT()
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
          <span className="field__label">{t('search.checkIn')}</span>
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
          <span className="field__label">{t('search.checkOut')}</span>
          <input
            type="date"
            min={checkIn ? addDays(checkIn, 1) : addDays(today, 1)}
            value={checkOut}
            onChange={(event) => setCheckOut(event.target.value)}
          />
        </label>
      </div>

      <label className="field">
        <span className="field__label">{t('search.guests')}</span>
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
          <span className="field__label">{t('book.messageHost')}</span>
          <textarea
            rows={3}
            maxLength={2000}
            placeholder={t('book.messageHostPlaceholder')}
            value={message}
            onChange={(event) => setMessage(event.target.value)}
          />
        </label>
      )}

      {quoting && <p className="muted small">{t('book.checking')}</p>}
      {quoteError && <div className="alert alert--error">{quoteError}</div>}

      {quote && <QuoteBreakdown quote={quote} />}

      {bookingError && <div className="alert alert--error">{bookingError}</div>}

      {user ? (
        <button
          type="button"
          className="button button--block"
          disabled={!quote || booking || quoting}
          onClick={submit}
        >
          {booking
            ? t('book.working')
            : listing.instantBook ? t('book.bookNow') : t('book.request')}
        </button>
      ) : (
        <Link href="/login" className="button button--block">{t('book.signInToBook')}</Link>
      )}

      <p className="muted small" style={{ marginTop: 8, marginBottom: 0 }}>
        {listing.instantBook
          ? t('book.payNextStep')
          : t('book.hostHas24h')}
      </p>
    </div>
  )
}
