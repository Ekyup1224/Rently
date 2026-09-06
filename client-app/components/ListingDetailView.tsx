'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import { useSearchParams } from 'next/navigation'
import { api, describeError } from '@/lib/api'
import { amenityLabel, formatMoney } from '@/lib/format'
import type { ListingDetail } from '@/lib/types'
import { BookingWidget } from './BookingWidget'

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
        <h2 className="card__title">This stay is not available</h2>
        <p className="muted small" style={{ marginTop: 0, marginBottom: 0 }}>{error}</p>
      </div>
    )
  }
  if (!listing) {
    return <p className="muted">Loading…</p>
  }

  const cover = listing.photos[activePhoto] ?? listing.photos[0]

  return (
    <>
      <h1>{listing.title}</h1>
      <p className="muted" style={{ marginTop: 0 }}>
        {listing.propertyType.toLowerCase()} in {listing.district
          ? `${listing.district}, ${listing.city}` : listing.city}
        {' · '}sleeps {listing.maxGuests}
        {listing.bedrooms > 0
          ? ` · ${listing.bedrooms} bedroom${listing.bedrooms > 1 ? 's' : ''}`
          : (listing.propertyType === 'APARTMENT' || listing.propertyType === 'STUDIO'
            ? ' · studio' : '')}
        {' · '}{listing.bathrooms} bath
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
          <h2 className="card__title">About this place</h2>
          <p style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{listing.description}</p>
        </div>
      )}

      {listing.amenities.length > 0 && (
        <div className="card">
          <h2 className="card__title">What this place offers</h2>
          <div className="row" style={{ gap: 8 }}>
            {listing.amenities.map((amenity) => (
              <span key={amenity} className="tag">{amenityLabel(amenity)}</span>
            ))}
          </div>
        </div>
      )}

      <div className="card">
        <h2 className="card__title">Good to know</h2>
        <dl className="definition">
          <dt>Nightly from</dt>
          <dd>{formatMoney(listing.nightlyFrom, listing.currency)}</dd>
          {listing.cleaningFee > 0 && (
            <>
              <dt>Cleaning fee</dt>
              <dd>{formatMoney(listing.cleaningFee, listing.currency)}</dd>
            </>
          )}
          <dt>Minimum stay</dt>
          <dd>{listing.minStayNights} night{listing.minStayNights > 1 ? 's' : ''}</dd>
          {listing.checkInFrom && (
            <>
              <dt>Check in</dt>
              <dd>from {listing.checkInFrom.slice(0, 5)}</dd>
            </>
          )}
          {listing.checkOutBy && (
            <>
              <dt>Check out</dt>
              <dd>by {listing.checkOutBy.slice(0, 5)}</dd>
            </>
          )}
          <dt>Cancellation</dt>
          <dd>{listing.cancellationPolicy.toLowerCase()}</dd>
          <dt>Booking</dt>
          <dd>{listing.instantBook
            ? 'Instant — no waiting for approval'
            : 'The host reviews each request'}</dd>
          <dt>Host</dt>
          <dd>
            {listing.host.displayName}, hosting since {listing.host.since}
            {listing.host.identityVerified && ' · identity verified'}
          </dd>
        </dl>
      </div>

      {listing.houseRules && (
        <div className="card">
          <h2 className="card__title">House rules</h2>
          <p style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{listing.houseRules}</p>
        </div>
      )}

      <div className="card">
        <h2 className="card__title">Where you will be</h2>
        <p className="muted small" style={{ marginTop: 0, marginBottom: 0 }}>
          {listing.district ? `${listing.district}, ` : ''}{listing.city}
          {'. '}The exact address is shared once your booking is confirmed.
        </p>
      </div>
    </>
  )
}
