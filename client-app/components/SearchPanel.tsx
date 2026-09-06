'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { addDays, todayInUlaanbaatar } from '@/lib/format'

/**
 * The unified search entry point: one form over both supply types, which is the
 * whole premise of the product. It collects criteria and explains what is not
 * wired yet rather than pretending to return results.
 */
export function SearchPanel() {
  const router = useRouter()
  const today = todayInUlaanbaatar()
  const [stayType, setStayType] = useState<'all' | 'houses' | 'hotels'>('all')
  const [destination, setDestination] = useState('')
  const [checkIn, setCheckIn] = useState('')
  const [checkOut, setCheckOut] = useState('')
  const [guests, setGuests] = useState('2')
  const [maxPrice, setMaxPrice] = useState('')

  return (
    <div className="card">
      <h2 className="card__title">Find a stay</h2>
      <p className="muted small" style={{ marginTop: 0 }}>
        Houses, apartments and hotel rooms in one search.
      </p>

      <div className="segmented" role="group" aria-label="Type of stay">
        {([['all', 'All stays'], ['houses', 'Houses'], ['hotels', 'Hotels']] as const).map(
          ([value, label]) => (
            <button
              key={value}
              type="button"
              aria-pressed={stayType === value}
              onClick={() => setStayType(value)}
            >
              {label}
            </button>
          ),
        )}
      </div>

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
          if (maxPrice) params.set('maxPrice', maxPrice)
          // Hotels join the same result set in Step 3; until then this narrows to
          // house-style listings rather than pretending to filter both.
          if (stayType === 'hotels') params.set('types', 'GUESTHOUSE')
          router.push(`/search?${params.toString()}`)
        }}
      >
        <label className="field">
          <span className="field__label">Where</span>
          <input
            name="destination"
            placeholder="Ulaanbaatar, Khuvsgul, Gobi…"
            value={destination}
            onChange={(event) => setDestination(event.target.value)}
          />
        </label>

        <div className="grid-2">
          <label className="field">
            <span className="field__label">Check in</span>
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
            <span className="field__label">Check out</span>
            <input
              name="checkOut"
              type="date"
              min={checkIn ? addDays(checkIn, 1) : addDays(today, 1)}
              value={checkOut}
              onChange={(event) => setCheckOut(event.target.value)}
            />
          </label>
        </div>

        <div className="grid-2">
          <label className="field">
            <span className="field__label">Guests</span>
            <select
              name="guests"
              value={guests}
              onChange={(event) => setGuests(event.target.value)}
            >
              {[1, 2, 3, 4, 5, 6].map((count) => (
                <option key={count} value={count}>
                  {count} guest{count > 1 ? 's' : ''}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field__label">Max price per night (₮)</span>
            <input
              name="maxPrice"
              type="number"
              min={0}
              step={10000}
              placeholder="250 000"
              value={maxPrice}
              onChange={(event) => setMaxPrice(event.target.value)}
            />
          </label>
        </div>

        <button type="submit" className="button button--block">Search</button>
      </form>

      {stayType === 'hotels' && (
        <div className="alert alert--info" style={{ marginTop: 16, marginBottom: 0 }}>
          Hotel rooms join the same search in the next release. For now this shows
          guesthouses and other whole-place stays.
        </div>
      )}
    </div>
  )
}
