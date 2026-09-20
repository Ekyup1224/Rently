'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { api } from '@/lib/api'
import { formatMoney } from '@/lib/format'
import type { ListingSummary } from '@/lib/types'
import { useT } from '@/lib/i18n'
import { RatingBadge } from './Reviews'

/** How many cards a row shows before sending people to the full results. */
const PER_ROW = 4

/** Photographed listings first, then the rest, capped at one row. */
function bestFirst(rows: ListingSummary[]): ListingSummary[] {
  const withPhoto = rows.filter((row) => row.coverPhotoUrl)
  const without = rows.filter((row) => !row.coverPhotoUrl)
  return [...withPhoto, ...without].slice(0, PER_ROW)
}

/**
 * What the landing page leads with: the actual stays on offer, a row per supply
 * type.
 *
 * <p>Two rows rather than one mixed grid, because the two are chosen differently
 * — a hotel by its rating and location, a house by what the whole place is — and
 * a visitor usually knows which of the two they came for. Each row links into the
 * same search page, pre-filtered, so this is a shortcut into search rather than a
 * separate browsing model.
 *
 * <p>Nothing here is fatal: if a row fails to load or a supply type has nothing
 * published yet, its section is simply left out. A landing page must not turn
 * into an error page.
 */
export function FeaturedStays() {
  const t = useT()
  const [hotels, setHotels] = useState<ListingSummary[] | null>(null)
  const [houses, setHouses] = useState<ListingSummary[] | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      // Over-fetch, then lead with the listings that have a photo. A shop window
      // shows the stock that looks like something; a photoless listing is still
      // findable in search, it just does not get the front page.
      const [hotelRows, houseRows] = await Promise.all([
        api.search({ supplyTypes: ['HOTEL'], size: PER_ROW * 4 })
          .then((page) => bestFirst(page.rows)).catch(() => []),
        api.search({ supplyTypes: ['PROPERTY'], size: PER_ROW * 4 })
          .then((page) => bestFirst(page.rows)).catch(() => []),
      ])
      if (!cancelled) {
        setHotels(hotelRows)
        setHouses(houseRows)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [])

  // Nothing at all to show: stay silent rather than render two empty headings.
  if (hotels?.length === 0 && houses?.length === 0) {
    return null
  }

  return (
    <>
      <StayRow
        title={t('home.hotels')}
        subtitle={t('home.hotelsSub')}
        href="/search?supplyTypes=HOTEL&guests=2"
        rows={hotels}
      />
      <StayRow
        title={t('home.houses')}
        subtitle={t('home.housesSub')}
        href="/search?supplyTypes=PROPERTY&guests=2"
        rows={houses}
      />
    </>
  )
}

function StayRow({ title, subtitle, href, rows }: {
  title: string
  subtitle: string
  href: string
  rows: ListingSummary[] | null
}) {
  const t = useT()
  // `null` is still loading; an empty array means this type has nothing live.
  if (rows !== null && rows.length === 0) {
    return null
  }

  return (
    <section>
      <div className="section-head">
        <div>
          <h2>{title}</h2>
          <p className="muted small" style={{ margin: 0 }}>{subtitle}</p>
        </div>
        <Link href={href}>{t('home.seeAll')} →</Link>
      </div>

      {rows === null ? (
        <p className="muted small">{t('common.loading')}</p>
      ) : (
        <div className="card-grid">
          {rows.map((stay) => <StayCard key={stay.id} stay={stay} />)}
        </div>
      )}
    </section>
  )
}

function StayCard({ stay }: { stay: ListingSummary }) {
  const t = useT()
  const isHotel = stay.supplyType === 'HOTEL'
  const detail = isHotel
    ? [stay.starRating ? '★'.repeat(stay.starRating) : null, stay.district ?? stay.city]
      .filter(Boolean).join(' · ')
    : [stay.propertyType ? t(`type.${stay.propertyType}`) : t('trips.yourStay'),
       stay.district ?? stay.city].join(' · ')

  return (
    <Link href={`${isHotel ? '/hotels' : '/listings'}/${stay.id}`} className="supply-card">
      {stay.coverPhotoUrl ? (
        <Image
          className="supply-card__image"
          src={stay.coverPhotoUrl}
          alt=""
          width={320}
          height={240}
        />
      ) : (
        <div className="supply-card__image supply-card__image--empty" aria-hidden="true">
          {isHotel ? '🏨' : '🏡'}
        </div>
      )}
      <div className="supply-card__body">
        <strong style={{ display: 'block', fontSize: 14, lineHeight: 1.35 }}>{stay.title}</strong>
        <p className="muted small" style={{ margin: '2px 0 6px' }}>{detail}</p>
        <p style={{ margin: '0 0 6px' }}>
          <RatingBadge average={stay.ratingAverage} count={stay.ratingCount} size="small" />
        </p>
        <span className="small">
          <span className="price">
            {isHotel
              ? t('home.fromPrice', { price: formatMoney(stay.nightlyFrom, stay.currency) })
              : formatMoney(stay.nightlyFrom, stay.currency)}
          </span>
          <span className="muted"> {t('home.perNight')}</span>
        </span>
      </div>
    </Link>
  )
}
