'use client'

import { useState } from 'react'

/**
 * The unified search entry point: one form over both supply types, which is the
 * whole premise of the product. It collects criteria and explains what is not
 * wired yet rather than pretending to return results.
 */
export function SearchPanel() {
  const [stayType, setStayType] = useState<'all' | 'houses' | 'hotels'>('all')
  const [submitted, setSubmitted] = useState(false)

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
          setSubmitted(true)
        }}
      >
        <label className="field">
          <span className="field__label">Where</span>
          <input name="destination" placeholder="Ulaanbaatar, Khuvsgul, Gobi…" />
        </label>

        <div className="grid-2">
          <label className="field">
            <span className="field__label">Check in</span>
            <input name="checkIn" type="date" />
          </label>
          <label className="field">
            <span className="field__label">Check out</span>
            <input name="checkOut" type="date" />
          </label>
        </div>

        <div className="grid-2">
          <label className="field">
            <span className="field__label">Guests</span>
            <select name="guests" defaultValue="2">
              {[1, 2, 3, 4, 5, 6].map((count) => (
                <option key={count} value={count}>
                  {count} guest{count > 1 ? 's' : ''}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field__label">Max price per night (₮)</span>
            <input name="maxPrice" type="number" min={0} step={10000} placeholder="250 000" />
          </label>
        </div>

        <button type="submit" className="button button--block">Search</button>
      </form>

      {submitted && (
        <div className="alert alert--info" style={{ marginTop: 16, marginBottom: 0 }}>
          Search results arrive with the listing catalogue: houses in Step 2, hotel
          rooms in Step 3, then both in one ranked result set.
        </div>
      )}
    </div>
  )
}
