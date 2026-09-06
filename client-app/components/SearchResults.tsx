'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { api, describeError, type Page } from '@/lib/api'
import { addDays, formatMoney, nightsBetween, todayInUlaanbaatar } from '@/lib/format'
import type { ListingSummary } from '@/lib/types'
import { ResultsMap } from './ResultsMap'

/**
 * Room count as a phrase. A flat with no separate bedroom is a studio; a ger or
 * cabin with none is just a ger, so the fragment is omitted rather than
 * mislabelled.
 */
function describeRooms(propertyType: string, bedrooms: number): string {
  if (bedrooms > 0) {
    return ` · ${bedrooms} bedroom${bedrooms > 1 ? 's' : ''}`
  }
  return propertyType === 'APARTMENT' || propertyType === 'STUDIO' ? ' · studio' : ''
}

const SORTS = [
  { value: 'relevance', label: 'Best match' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'newest', label: 'Newest' },
  { value: 'guests', label: 'Sleeps most' },
]

/**
 * Search results, driven entirely by the URL.
 *
 * <p>Filters live in query parameters so a search is shareable and the back
 * button behaves. Availability filtering happens server-side in one query, so
 * paging is correct — filtering after the fact would silently break page counts.
 */
export function SearchResults() {
  const params = useSearchParams()
  const router = useRouter()
  const today = todayInUlaanbaatar()

  const [results, setResults] = useState<Page<ListingSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [showMap, setShowMap] = useState(false)

  const query = params.get('q') ?? ''
  const checkIn = params.get('checkIn') ?? ''
  const checkOut = params.get('checkOut') ?? ''
  const guests = params.get('guests') ?? ''
  const maxPrice = params.get('maxPrice') ?? ''
  const instantBook = params.get('instantBook') === 'true'
  const sort = params.get('sort') ?? 'relevance'
  const page = Number(params.get('page') ?? '0')

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const found = await api.search({
          q: query || undefined,
          checkIn: checkIn || undefined,
          checkOut: checkOut || undefined,
          guests: guests ? Number(guests) : undefined,
          maxPrice: maxPrice ? Number(maxPrice) : undefined,
          instantBook: instantBook || undefined,
          sort,
          page,
          size: 12,
        })
        if (!cancelled) {
          setResults(found)
        }
      } catch (failure) {
        if (!cancelled) {
          setError(describeError(failure))
          setResults(null)
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
  }, [query, checkIn, checkOut, guests, maxPrice, instantBook, sort, page])

  /** Rewrites the URL, which is what actually re-runs the search. */
  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params.toString())
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === '') {
        next.delete(key)
      } else {
        next.set(key, value)
      }
    }
    // Any filter change invalidates the current page number.
    if (!('page' in changes)) {
      next.delete('page')
    }
    router.push(`/search?${next.toString()}`)
  }

  const nights = checkIn && checkOut ? nightsBetween(checkIn, checkOut) : 0
  const mappable = (results?.rows ?? []).filter(
    (listing) => listing.latitude != null && listing.longitude != null)

  return (
    <>
      <h1>{query ? `Stays matching “${query}”` : 'All stays'}</h1>
      <p className="muted" style={{ marginTop: 0 }}>
        {nights > 0
          ? `${checkIn} → ${checkOut} · ${nights} night${nights > 1 ? 's' : ''}`
          : 'Add dates to see exact totals and only available places'}
      </p>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="grid-2">
          <label className="field">
            <span className="field__label">Check in</span>
            <input
              type="date"
              min={today}
              value={checkIn}
              onChange={(event) => {
                const value = event.target.value
                update({
                  checkIn: value,
                  checkOut: value && checkOut <= value ? addDays(value, 2) : checkOut,
                })
              }}
            />
          </label>
          <label className="field">
            <span className="field__label">Check out</span>
            <input
              type="date"
              min={checkIn ? addDays(checkIn, 1) : addDays(today, 1)}
              value={checkOut}
              onChange={(event) => update({ checkOut: event.target.value })}
            />
          </label>
        </div>
        <div className="grid-2">
          <label className="field">
            <span className="field__label">Guests</span>
            <select value={guests} onChange={(event) => update({ guests: event.target.value })}>
              <option value="">Any</option>
              {[1, 2, 3, 4, 5, 6, 8, 10].map((count) => (
                <option key={count} value={count}>{count}+</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field__label">Sort</span>
            <select value={sort} onChange={(event) => update({ sort: event.target.value })}>
              {SORTS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>
        </div>
        <label className="row" style={{ alignItems: 'center', gap: 8 }}>
          <input
            type="checkbox"
            style={{ width: 'auto' }}
            checked={instantBook}
            onChange={(event) => update({ instantBook: event.target.checked ? 'true' : null })}
          />
          <span className="small">Instant book only</span>
        </label>
      </div>

      {error && <div className="alert alert--error">{error}</div>}
      {loading && <p className="muted">Searching…</p>}

      {results && !loading && (
        <>
          <div className="row" style={{ justifyContent: 'space-between', alignItems: 'center',
                                        marginBottom: 12 }}>
            <span className="muted small">
              {results.total} place{results.total === 1 ? '' : 's'}
              {nights > 0 ? ' available' : ''}
            </span>
            {mappable.length > 0 && (
              <button type="button" className="button button--ghost"
                      onClick={() => setShowMap((visible) => !visible)}>
                {showMap ? 'Hide map' : 'Show map'}
              </button>
            )}
          </div>

          {showMap && mappable.length > 0 && <ResultsMap listings={mappable} />}

          {results.rows.length === 0 && (
            <div className="card">
              <h2 className="card__title">Nothing matched</h2>
              <p className="muted small" style={{ marginTop: 0, marginBottom: 0 }}>
                Try widening your dates, raising the price limit, or searching a different area.
              </p>
            </div>
          )}

          {results.rows.map((listing) => (
            <Link
              key={listing.id}
              href={`/listings/${listing.id}${checkIn && checkOut
                ? `?checkIn=${checkIn}&checkOut=${checkOut}&guests=${guests || 2}` : ''}`}
              className="card"
              style={{ display: 'flex', gap: 14, marginBottom: 12, textDecoration: 'none',
                       color: 'inherit' }}
            >
              {listing.coverPhotoUrl
                ? <Image
                    src={listing.coverPhotoUrl}
                    alt=""
                    width={168}
                    height={120}
                    style={{ objectFit: 'cover', borderRadius: 10, flexShrink: 0 }}
                  />
                : <div style={{ width: 168, height: 120, borderRadius: 10, flexShrink: 0,
                                background: 'var(--ground)', border: '1px solid var(--line)' }} />}

              <div style={{ minWidth: 0 }}>
                <div className="row" style={{ gap: 6, alignItems: 'center' }}>
                  <strong>{listing.title}</strong>
                  {listing.instantBook && <span className="tag">Instant book</span>}
                </div>
                <p className="muted small" style={{ margin: '4px 0' }}>
                  {listing.propertyType.toLowerCase()} in {listing.district ?? listing.city}
                  {' · '}sleeps {listing.maxGuests}
                  {describeRooms(listing.propertyType, listing.bedrooms)}
                </p>
                <p style={{ margin: 0 }}>
                  <strong>{formatMoney(listing.nightlyFrom, listing.currency)}</strong>
                  <span className="muted small"> / night</span>
                  {nights > 0 && (
                    <span className="muted small">
                      {' · about '}
                      {formatMoney(listing.nightlyFrom * nights + listing.cleaningFee,
                        listing.currency)}
                      {' total'}
                    </span>
                  )}
                </p>
                {listing.minStayNights > 1 && (
                  <p className="muted small" style={{ margin: '2px 0 0' }}>
                    {listing.minStayNights}-night minimum
                  </p>
                )}
              </div>
            </Link>
          ))}

          {results.totalPages > 1 && (
            <div className="row" style={{ justifyContent: 'center', gap: 12, marginTop: 8 }}>
              <button
                type="button"
                className="button button--ghost"
                disabled={page <= 0}
                onClick={() => update({ page: String(page - 1) })}
              >
                Previous
              </button>
              <span className="muted small" style={{ alignSelf: 'center' }}>
                Page {page + 1} of {results.totalPages}
              </span>
              <button
                type="button"
                className="button button--ghost"
                disabled={page + 1 >= results.totalPages}
                onClick={() => update({ page: String(page + 1) })}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </>
  )
}
