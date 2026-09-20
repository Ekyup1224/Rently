'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { api, describeError, type Page } from '@/lib/api'
import { addDays, formatMoney, nightsBetween, todayInUlaanbaatar } from '@/lib/format'
import type { ListingSummary } from '@/lib/types'
import { ResultsMap } from './ResultsMap'
import { RatingBadge } from './Reviews'
import { useT } from '@/lib/i18n'

/**
 * Describes a result in one line, differently for each supply type: a house by its
 * category and bedrooms, a hotel by its rating and how many rooms it sells.
 */
type Translate = (key: string, values?: Record<string, string | number>) => string

function describeSupply(listing: ListingSummary, t: Translate): string {
  const place = listing.district ?? listing.city

  if (listing.supplyType === 'HOTEL') {
    const parts = [
      listing.starRating
        ? t('search.starHotel', { stars: '★'.repeat(listing.starRating) })
        : t('search.hotel'),
      t('search.in', { place }),
    ]
    if (listing.roomTypeCount) {
      parts.push(t('hotel.roomTypes', { count: listing.roomTypeCount }))
    }
    parts.push(t('search.sleepsPerRoom', { count: listing.maxGuests }))
    return parts.join(' · ')
  }

  const parts = [
    listing.propertyType ? t(`type.${listing.propertyType}`) : t('trips.yourStay'),
    t('search.in', { place }),
    t('listing.sleeps', { count: listing.maxGuests }),
  ]
  const bedrooms = listing.bedrooms ?? 0
  if (bedrooms > 0) {
    parts.push(t('listing.bedrooms', { count: bedrooms }))
  }
  return parts.join(' · ')
}

const SORTS = ['relevance', 'price_asc', 'price_desc', 'newest', 'guests'] as const

/** Sort values are snake_case on the wire; message keys are camelCase. */
const SORT_KEYS: Record<(typeof SORTS)[number], string> = {
  relevance: 'search.sort.relevance',
  price_asc: 'search.sort.priceAsc',
  price_desc: 'search.sort.priceDesc',
  newest: 'search.sort.newest',
  guests: 'search.sort.guests',
}

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
  const t = useT()
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
  const supplyType = params.get('supplyTypes') ?? ''
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
          supplyTypes: supplyType ? [supplyType] : undefined,
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
  }, [query, checkIn, checkOut, guests, supplyType, maxPrice, instantBook, sort, page])

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
      <h1>{query ? t('search.matching', { query }) : t('search.allStays')}</h1>
      <p className="muted" style={{ marginTop: 0 }}>
        {nights > 0
          ? t('search.dateRange', {
            checkIn,
            checkOut,
            nights: nights === 1 ? t('common.night_one') : t('common.nights', { count: nights }),
          })
          : t('search.addDates')}
      </p>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="grid-2">
          <label className="field">
            <span className="field__label">{t('search.checkIn')}</span>
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
            <span className="field__label">{t('search.checkOut')}</span>
            <input
              type="date"
              min={checkIn ? addDays(checkIn, 1) : addDays(today, 1)}
              value={checkOut}
              onChange={(event) => update({ checkOut: event.target.value })}
            />
          </label>
        </div>
        <div className="grid-3">
          <label className="field">
            <span className="field__label">{t('search.guests')}</span>
            <select value={guests} onChange={(event) => update({ guests: event.target.value })}>
              <option value="">{t('search.any')}</option>
              {[1, 2, 3, 4, 5, 6, 8, 10].map((count) => (
                <option key={count} value={count}>{count}+</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field__label">{t('search.maxPrice')}</span>
            <input
              // Uncontrolled and keyed on the URL value: typing must not push a
              // history entry per keystroke, but Back still has to update the box.
              key={maxPrice}
              type="number"
              min={0}
              step={10000}
              placeholder={t('search.any')}
              defaultValue={maxPrice}
              onBlur={(event) => update({ maxPrice: event.target.value })}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.currentTarget.blur()
                }
              }}
            />
          </label>
          <label className="field">
            <span className="field__label">{t('search.sort')}</span>
            <select value={sort} onChange={(event) => update({ sort: event.target.value })}>
              {SORTS.map((option) => (
                <option key={option} value={option}>{t(SORT_KEYS[option])}</option>
              ))}
            </select>
          </label>
        </div>
        <div className="segmented" role="group" aria-label={t('search.typeOfStay')}>
          {([['', 'search.allStays'], ['PROPERTY', 'search.houses'],
             ['HOTEL', 'search.hotels']] as const).map(([value, key]) => (
            <button
              key={value || 'all'}
              type="button"
              aria-pressed={supplyType === value}
              onClick={() => update({ supplyTypes: value || null })}
            >
              {t(key)}
            </button>
          ))}
        </div>

        <label className="row" style={{ alignItems: 'center', gap: 8 }}>
          <input
            type="checkbox"
            style={{ width: 'auto' }}
            checked={instantBook}
            onChange={(event) => update({ instantBook: event.target.checked ? 'true' : null })}
          />
          <span className="small">{t('search.instantOnly')}</span>
        </label>
      </div>

      {error && <div className="alert alert--error">{error}</div>}
      {loading && <p className="muted">{t('search.searching')}</p>}

      {results && !loading && (
        <>
          <div className="row" style={{ justifyContent: 'space-between', alignItems: 'center',
                                        marginBottom: 12 }}>
            <span className="muted small">
              {results.total === 1
                ? t('search.countOne') : t('search.count', { count: results.total })}
              {nights > 0 ? ` ${t('search.available')}` : ''}
            </span>
            {mappable.length > 0 && (
              <button type="button" className="button button--ghost"
                      onClick={() => setShowMap((visible) => !visible)}>
                {showMap ? t('search.hideMap') : t('search.showMap')}
              </button>
            )}
          </div>

          {showMap && mappable.length > 0 && <ResultsMap listings={mappable} />}

          {results.rows.length === 0 && (
            <div className="card">
              <h2 className="card__title">{t('search.nothing')}</h2>
              <p className="muted small" style={{ marginTop: 0, marginBottom: 0 }}>
                {t('search.nothingBody')}
              </p>
            </div>
          )}

          {results.rows.map((listing) => (
            <Link
              key={listing.id}
              href={`${listing.supplyType === 'HOTEL' ? '/hotels' : '/listings'}/${listing.id}`
                + (checkIn && checkOut
                  ? `?checkIn=${checkIn}&checkOut=${checkOut}&guests=${guests || 2}` : '')}
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
                : <div className="supply-card__image--empty"
                       style={{ width: 168, height: 120, borderRadius: 10, flexShrink: 0 }}
                       aria-hidden="true">
                    {listing.supplyType === 'HOTEL' ? '🏨' : '🏡'}
                  </div>}

              <div style={{ minWidth: 0 }}>
                <div className="row" style={{ gap: 6, alignItems: 'center' }}>
                  <strong>{listing.title}</strong>
                  {listing.supplyType === 'HOTEL'
                    ? <span className="tag tag--hotel">{t('search.hotelTag')}</span>
                    : listing.instantBook
                      && <span className="tag tag--instant">{t('search.instantTag')}</span>}
                </div>
                <p className="muted small" style={{ margin: '4px 0' }}>
                  {describeSupply(listing, t)}
                </p>
                <p style={{ margin: '0 0 4px' }}>
                  <RatingBadge
                    average={listing.ratingAverage}
                    count={listing.ratingCount}
                    size="small"
                  />
                </p>
                <p style={{ margin: 0 }}>
                  <strong>
                    {listing.supplyType === 'HOTEL'
                      ? t('home.fromPrice', {
                        price: formatMoney(listing.nightlyFrom, listing.currency),
                      })
                      : formatMoney(listing.nightlyFrom, listing.currency)}
                  </strong>
                  <span className="muted small"> {t('home.perNight')}</span>
                  {nights > 0 && (
                    <span className="muted small">
                      {' · '}
                      {t('search.about', {
                        total: formatMoney(
                          listing.nightlyFrom * nights + listing.cleaningFee, listing.currency),
                      })}
                    </span>
                  )}
                </p>
                {listing.minStayNights > 1 && (
                  <p className="muted small" style={{ margin: '2px 0 0' }}>
                    {t('search.minNights', { count: listing.minStayNights })}
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
                {t('search.previous')}
              </button>
              <span className="muted small" style={{ alignSelf: 'center' }}>
                {t('search.pageOf', { page: page + 1, total: results.totalPages })}
              </span>
              <button
                type="button"
                className="button button--ghost"
                disabled={page + 1 >= results.totalPages}
                onClick={() => update({ page: String(page + 1) })}
              >
                {t('search.next')}
              </button>
            </div>
          )}
        </>
      )}
    </>
  )
}
