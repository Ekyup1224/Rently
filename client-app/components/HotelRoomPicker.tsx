'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { api, describeError } from '@/lib/api'
import { formatDateRange, formatMoney, nightsBetween } from '@/lib/format'
import type { HotelDetail, Quote, RoomTypeAvailability } from '@/lib/types'
import { useLanguage } from '@/lib/i18n'
import { AvailabilityCalendar } from './AvailabilityCalendar'
import { useAuth } from './AuthProvider'
import { QuoteBreakdown } from './QuoteBreakdown'

/**
 * Choosing dates, then a room type, then reserving.
 *
 * <p>A hotel differs from a house in what the guest picks: the stay is chosen
 * first and the room type second, because which rooms are sellable depends on the
 * dates. So this asks for dates up front, then lists every room type with what
 * the server says about those nights — how many rooms are left, what the stay
 * costs, and why a room type cannot take it.
 *
 * <p>Room counts and availability are never computed here. `roomsLeft` is the
 * tightest night across the stay, which only the server can know, and the quote
 * is what will be charged.
 */
export function HotelRoomPicker({
  hotel, initialCheckIn, initialCheckOut, initialGuests,
}: {
  hotel: HotelDetail
  initialCheckIn: string
  initialCheckOut: string
  initialGuests: number
}) {
  const { user } = useAuth()
  const { locale, t } = useLanguage()
  const router = useRouter()

  const maxCapacity = Math.max(...hotel.roomTypes.map((room) => room.capacity), 1)

  const [checkIn, setCheckIn] = useState(initialCheckIn)
  const [checkOut, setCheckOut] = useState(initialCheckOut)
  const [guests, setGuests] = useState(Math.max(1, initialGuests))
  const [rooms, setRooms] = useState(1)
  const [calendarOpen, setCalendarOpen] = useState(false)
  const [availability, setAvailability] = useState<RoomTypeAvailability[] | null>(null)
  const [availabilityError, setAvailabilityError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const [selected, setSelected] = useState<string | null>(null)
  const [quote, setQuote] = useState<Quote | null>(null)
  const [quoteError, setQuoteError] = useState<string | null>(null)
  const [quoting, setQuoting] = useState(false)
  const [booking, setBooking] = useState(false)
  const [bookingError, setBookingError] = useState<string | null>(null)

  const nights = checkIn && checkOut ? nightsBetween(checkIn, checkOut) : 0
  const hasStay = Boolean(checkIn && checkOut) && nights >= 1

  // A click only counts while that room type is still offered for the current
  // stay, so changing the dates cannot leave a stale quote on screen. Deriving
  // this beats clearing the selection after the fact.
  const activeRoomTypeId = availability?.find(
    (row) => row.roomTypeId === selected && row.bookable)?.roomTypeId ?? null

  // Availability depends on the whole stay, so it reloads with any of it.
  useEffect(() => {
    let cancelled = false

    async function load() {
      if (!hasStay) {
        setAvailability(null)
        setAvailabilityError(null)
        return
      }
      setLoading(true)
      setAvailabilityError(null)
      try {
        const rows = await api.hotelAvailability(hotel.id, { checkIn, checkOut, guests, rooms })
        if (!cancelled) {
          setAvailability(rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setAvailability(null)
          setAvailabilityError(describeError(failure))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [hotel.id, checkIn, checkOut, guests, rooms, hasStay])

  useEffect(() => {
    let cancelled = false

    async function loadQuote() {
      if (!activeRoomTypeId || !hasStay) {
        setQuote(null)
        setQuoteError(null)
        return
      }
      setQuoting(true)
      setQuoteError(null)
      try {
        const priced = await api.hotelQuote(hotel.id, activeRoomTypeId,
          { checkIn, checkOut, guests }, rooms)
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
  }, [hotel.id, activeRoomTypeId, checkIn, checkOut, guests, rooms, hasStay])

  async function submit() {
    if (!activeRoomTypeId) {
      return
    }
    setBooking(true)
    setBookingError(null)
    try {
      // A hotel reservation is never a request to a host: it goes straight to
      // payment.
      const created = await api.book(
        { roomTypeId: activeRoomTypeId, checkIn, checkOut, guests, rooms })
      router.push(`/bookings/${created.id}/checkout`)
    } catch (failure) {
      setBookingError(describeError(failure))
    } finally {
      setBooking(false)
    }
  }

  return (
    <div className="card" id="rooms">
      <h2 className="card__title">{t('hotel.chooseDates')}</h2>

      <label className="field">
        <span className="field__label">{t('search.checkIn')} – {t('search.checkOut')}</span>
        <button
          type="button"
          className="avail-cal__trigger"
          onClick={() => setCalendarOpen((open) => !open)}
        >
          {checkIn && checkOut ? formatDateRange(checkIn, checkOut, locale) : t('book.selectDates')}
        </button>
      </label>

      {calendarOpen && (
        // No listing id: a hotel's availability is a question about room types
        // over a range, which the list below answers once both dates exist —
        // there is no per-night endpoint to shade this calendar with.
        <AvailabilityCalendar
          checkIn={checkIn}
          checkOut={checkOut}
          onSelect={(nextCheckIn, nextCheckOut) => {
            setCheckIn(nextCheckIn)
            setCheckOut(nextCheckOut)
            if (nextCheckIn && nextCheckOut) {
              setCalendarOpen(false)
            }
          }}
        />
      )}

      <div className="grid-2">
        <label className="field">
          <span className="field__label">{t('hotel.guestsPerRoom')}</span>
          <select value={guests} onChange={(event) => setGuests(Number(event.target.value))}>
            {Array.from({ length: maxCapacity }, (_, index) => index + 1).map((count) => (
              <option key={count} value={count}>
                {count} guest{count > 1 ? 's' : ''}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field__label">{t('hotel.roomCount')}</span>
          <select value={rooms} onChange={(event) => setRooms(Number(event.target.value))}>
            {Array.from({ length: 5 }, (_, index) => index + 1).map((count) => (
              <option key={count} value={count}>
                {count} room{count > 1 ? 's' : ''}
              </option>
            ))}
          </select>
        </label>
      </div>

      {!hasStay && (
        <p className="muted small">
          {t('hotel.selectDates')}
        </p>
      )}
      {loading && <p className="muted small">{t('book.checking')}</p>}
      {availabilityError && <div className="alert alert--error">{availabilityError}</div>}

      {availability?.map((room) => {
        const isSelected = room.roomTypeId === selected
        return (
          <div
            key={room.roomTypeId}
            style={{
              display: 'flex', gap: 12, marginTop: 12, paddingTop: 12,
              borderTop: '1px solid var(--line)',
            }}
          >
            {room.photos[0] && (
              <Image
                src={room.photos[0].url}
                alt={room.photos[0].altText ?? room.name}
                width={112}
                height={84}
                style={{ objectFit: 'cover', borderRadius: 8, flexShrink: 0 }}
              />
            )}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="row" style={{ gap: 8, alignItems: 'center' }}>
                <strong>{room.name}</strong>
                <span className="muted small">{t('hotel.sleeps', { count: room.capacity })}</span>
              </div>

              {room.bookable ? (
                <p className="muted small" style={{ margin: '4px 0' }}>
                  {formatMoney(room.totalForStay ?? room.nightlyFrom, room.currency)}
                  {room.totalForStay
                    ? ` for ${nights} night${nights > 1 ? 's' : ''}`
                    : ' / night'}
                  {room.roomsLeft <= 3 && ` · only ${room.roomsLeft} left`}
                </p>
              ) : (
                <p className="muted small" style={{ margin: '4px 0' }}>
                  {room.unavailableReason ?? t('hotel.soldOut')}
                </p>
              )}

              <button
                type="button"
                className="button button--ghost"
                disabled={!room.bookable}
                onClick={() => setSelected(isSelected ? null : room.roomTypeId)}
              >
                {isSelected
                  ? t('hotel.selected')
                  : room.bookable ? t('hotel.select') : t('hotel.unavailable2')}
              </button>
            </div>
          </div>
        )
      })}

      {quoting && <p className="muted small" style={{ marginTop: 12 }}>{t('hotel.pricing')}</p>}
      {quoteError && <div className="alert alert--error">{quoteError}</div>}

      {quote && (
        <QuoteBreakdown
          quote={quote}
          roomsLabel={`${quote.rooms} room${quote.rooms > 1 ? 's' : ''}`}
        />
      )}

      {bookingError && <div className="alert alert--error">{bookingError}</div>}

      {activeRoomTypeId && (user ? (
        <button
          type="button"
          className="button button--block"
          disabled={!quote || booking || quoting}
          onClick={submit}
        >
          {booking ? t('book.working') : t('book.reserve')}
        </button>
      ) : (
        <Link href="/login" className="button button--block">{t('book.signInToBook')}</Link>
      ))}

      {activeRoomTypeId && (
        <p className="muted small" style={{ marginTop: 8, marginBottom: 0 }}>
          {t('book.payNextStep')}
        </p>
      )}
    </div>
  )
}
