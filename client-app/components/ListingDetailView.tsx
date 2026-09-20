'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import { useSearchParams } from 'next/navigation'
import { api, describeError } from '@/lib/api'
import { formatMoney } from '@/lib/format'
import { useT } from '@/lib/i18n'
import type { ListingDetail } from '@/lib/types'
import { BookingWidget } from './BookingWidget'
import { ReportListing } from './ReportListing'
import { RatingBadge, ReviewList } from './Reviews'

/**
 * The listing page.
 *
 * <p>Shows what the API is willing to publish: no street address (that follows a
 * booking) and only the host's first name. The exact total for a stay comes from
 * the quote endpoint in the booking widget, not from multiplying the nightly rate
 * here, because per-night overrides make that arithmetic wrong.
 */
export function ListingDetailView({ propertyId }: { propertyId: string }) {
  const params = useSearchParams()
  const t = useT()
  const [listing, setListing] = useState<ListingDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [activePhoto, setActivePhoto] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const loaded = await api.listing(propertyId)
        if (!cancelled) {
          setListing(loaded)
        }
      } catch (failure) {
        if (!cancelled) {
          setError(describeError(failure))
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [propertyId])

  if (error) {
    return (
      <div className="card">
        <h2 className="card__title">{t('listing.unavailable')}</h2>
        <p className="muted small" style={{ marginTop: 0, marginBottom: 0 }}>{error}</p>
      </div>
    )
  }
  if (!listing) {
    return <p className="muted">{t('common.loading')}</p>
  }

  const cover = listing.photos[activePhoto] ?? listing.photos[0]

  return (
    <>
      <h1>{listing.title}</h1>
      <p style={{ marginTop: 0, marginBottom: 4 }}>
        <RatingBadge average={listing.ratingAverage} count={listing.ratingCount} />
      </p>
      <p className="muted" style={{ marginTop: 0 }}>
        {t(`type.${listing.propertyType}`)} · {listing.district
          ? `${listing.district}, ${listing.city}` : listing.city}
        {' · '}{t('listing.sleeps', { count: listing.maxGuests })}
        {listing.bedrooms > 0 && ` · ${t('listing.bedrooms', { count: listing.bedrooms })}`}
        {' · '}{t('listing.baths', { count: listing.bathrooms })}
      </p>

      {cover && (
        <div className="card" style={{ padding: 0, overflow: 'hidden', marginBottom: 16 }}>
          <Image
            src={cover.url}
            alt={cover.altText ?? listing.title}
            width={960}
            height={420}
            priority
            style={{ width: '100%', height: 'auto', maxHeight: 420, objectFit: 'cover',
                     display: 'block' }}
          />
          {listing.photos.length > 1 && (
            <div className="row" style={{ gap: 6, padding: 8 }}>
              {listing.photos.map((photo, index) => (
                <button
                  key={photo.id}
                  type="button"
                  onClick={() => setActivePhoto(index)}
                  aria-label={`Photo ${index + 1}`}
                  style={{
                    padding: 0, border: index === activePhoto
                      ? '2px solid var(--brand)' : '1px solid var(--line)',
                    borderRadius: 8, overflow: 'hidden', cursor: 'pointer', background: 'none',
                  }}
                >
                  <Image src={photo.url} alt="" width={72} height={52}
                         style={{ objectFit: 'cover', display: 'block' }} />
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      <BookingWidget
        listing={listing}
        initialCheckIn={params.get('checkIn') ?? ''}
        initialCheckOut={params.get('checkOut') ?? ''}
        initialGuests={Number(params.get('guests') ?? '2')}
      />

      {listing.description && (
        <div className="card">
          <h2 className="card__title">{t('listing.about')}</h2>
          <p style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{listing.description}</p>
        </div>
      )}

      {listing.amenities.length > 0 && (
        <div className="card">
          <h2 className="card__title">{t('listing.offers')}</h2>
          <div className="row" style={{ gap: 8 }}>
            {listing.amenities.map((amenity) => (
              <span key={amenity} className="tag">{t(`amenity.${amenity}`)}</span>
            ))}
          </div>
        </div>
      )}

      <div className="card">
        <h2 className="card__title">{t('listing.goodToKnow')}</h2>
        <dl className="definition">
          <dt>{t('listing.nightlyFrom')}</dt>
          <dd>{formatMoney(listing.nightlyFrom, listing.currency)}</dd>
          {listing.cleaningFee > 0 && (
            <>
              <dt>{t('listing.cleaningFee')}</dt>
              <dd>{formatMoney(listing.cleaningFee, listing.currency)}</dd>
            </>
          )}
          <dt>{t('listing.minStay')}</dt>
          <dd>{listing.minStayNights === 1
            ? t('common.night_one')
            : t('common.nights', { count: listing.minStayNights })}</dd>
          {listing.checkInFrom && (
            <>
              <dt>{t('listing.checkInFrom')}</dt>
              <dd>{listing.checkInFrom.slice(0, 5)}</dd>
            </>
          )}
          {listing.checkOutBy && (
            <>
              <dt>{t('listing.checkOutBy')}</dt>
              <dd>{listing.checkOutBy.slice(0, 5)}</dd>
            </>
          )}
          <dt>{t('listing.cancellation')}</dt>
          <dd>{t(`policy.${listing.cancellationPolicy}`)}</dd>
          <dt>{t('listing.booking')}</dt>
          <dd>{listing.instantBook ? t('listing.instant') : t('listing.byRequest')}</dd>
          <dt>{t('listing.host')}</dt>
          <dd>
            {t('listing.hostSince', {
              name: listing.host.displayName, year: listing.host.since,
            })}
            {listing.host.identityVerified && ` · ${t('listing.verified')}`}
          </dd>
        </dl>
      </div>

      {listing.houseRules && (
        <div className="card">
          <h2 className="card__title">{t('listing.rules')}</h2>
          <p style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{listing.houseRules}</p>
        </div>
      )}

      <div className="card">
        <h2 className="card__title">{t('listing.where')}</h2>
        <p className="muted small" style={{ marginTop: 0, marginBottom: 0 }}>
          {listing.district ? `${listing.district}, ` : ''}{listing.city}
          {'. '}{t('listing.whereBody')}
        </p>
      </div>
      <ReviewList
        supplyType="PROPERTY"
        supplyId={listing.id}
        average={listing.ratingAverage}
        count={listing.ratingCount}
      />
      <ReportListing supplyType="PROPERTY" supplyId={listing.id} />
    </>
  )
}
