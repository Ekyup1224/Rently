'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { addDays, todayInUlaanbaatar } from '@/lib/format'
import { useT } from '@/lib/i18n'

/**
 * The landing page's search bar: one row over both supply types, which is the
 * whole premise of the product.
 *
 * <p>Deliberately only four fields. The stays themselves are the point of the
 * landing page, so this stays one row deep and leaves them above the fold;
 * narrowing by type, price, instant-book or sort happens on the results page,
 * against real results rather than guessed at before seeing any.
 */
export function SearchPanel() {
  const router = useRouter()
  const t = useT()
  const today = todayInUlaanbaatar()
  const [destination, setDestination] = useState('')
  const [checkIn, setCheckIn] = useState('')
  const [checkOut, setCheckOut] = useState('')
  const [guests, setGuests] = useState('2')

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
            <span className="field__label">{t('search.checkIn')}</span>
            <input
              name="checkIn"
              type="date"
              min={today}
              value={checkIn}
              onChange={(event) => {
                setCheckIn(event.target.value)
                // Keep check-out after check-in so an impossible range is not
                // even expressible.
                if (event.target.value && checkOut <= event.target.value) {
                  setCheckOut(addDays(event.target.value, 2))
                }
              }}
            />
          </label>

          <label className="field">
            <span className="field__label">{t('search.checkOut')}</span>
            <input
              name="checkOut"
              type="date"
              min={checkIn ? addDays(checkIn, 1) : addDays(today, 1)}
              value={checkOut}
              onChange={(event) => setCheckOut(event.target.value)}
            />
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
      </form>
    </div>
  )
}
