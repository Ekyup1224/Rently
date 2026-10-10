'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { formatDateRange } from '@/lib/format'
import { useLanguage } from '@/lib/i18n'
import { AvailabilityCalendar } from './AvailabilityCalendar'

/**
 * The landing page's search bar: one row over both supply types, which is the
 * whole premise of the product.
 *
 * <p>Deliberately only three fields — where, when, how many. The stays
 * themselves are the point of the
 * landing page, so this stays one row deep and leaves them above the fold;
 * narrowing by type, price, instant-book or sort happens on the results page,
 * against real results rather than guessed at before seeing any.
 */
export function SearchPanel() {
  const router = useRouter()
  const { locale, t } = useLanguage()
  const [destination, setDestination] = useState('')
  const [checkIn, setCheckIn] = useState('')
  const [checkOut, setCheckOut] = useState('')
  const [guests, setGuests] = useState('2')
  const [calendarOpen, setCalendarOpen] = useState(false)

  return (
    <div className="card">
      <form
        onSubmit={(event) => {
          event.preventDefault()
          const params = new URLSearchParams()
          if (destination) params.set('q', destination)
          if (checkIn && checkOut) {
            params.set('checkIn', checkIn)
            params.set('checkOut', checkOut)
          }
          params.set('guests', guests)
          router.push(`/search?${params.toString()}`)
        }}
      >
        <div className="search-bar">
          <label className="field">
            <span className="field__label">{t('search.where')}</span>
            <input
              name="destination"
              placeholder={t('search.wherePlaceholder')}
              value={destination}
              onChange={(event) => setDestination(event.target.value)}
            />
          </label>

          <label className="field">
            <span className="field__label">{t('search.checkIn')} – {t('search.checkOut')}</span>
            <button
              type="button"
              className="avail-cal__trigger"
              onClick={() => setCalendarOpen((open) => !open)}
            >
              {checkIn && checkOut
                ? formatDateRange(checkIn, checkOut, locale)
                : t('book.selectDates')}
            </button>
          </label>

          <label className="field">
            <span className="field__label">{t('search.guests')}</span>
            <select
              name="guests"
              value={guests}
              onChange={(event) => setGuests(event.target.value)}
            >
              {[1, 2, 3, 4, 5, 6].map((count) => (
                <option key={count} value={count}>
                  {count === 1 ? t('common.guest_one') : t('common.guests', { count })}
                </option>
              ))}
            </select>
          </label>

          <button type="submit" className="button">{t('search.submit')}</button>
        </div>

        {/* Outside .search-bar: that is a single grid row, and a month grid
            is not one of its columns. */}
        {calendarOpen && (
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
      </form>
    </div>
  )
}
