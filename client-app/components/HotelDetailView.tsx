'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import { useSearchParams } from 'next/navigation'
import { api, describeError } from '@/lib/api'
import { formatMoney } from '@/lib/format'
import { useT } from '@/lib/i18n'
import type { HotelDetail } from '@/lib/types'
import { HotelRoomPicker } from './HotelRoomPicker'
import { ReportListing } from './ReportListing'
import { RatingBadge, ReviewList } from './Reviews'

/**
 * The hotel page.
 *
 * <p>Deliberately unlike a house: a hotel publishes its street address, because
 * that is public information a guest uses to choose, and it sells room types
 * rather than the whole building. So the room picker sits at the top — the choice
 * that matters here is which room, on which nights — and the descriptive sections
 * follow it.
 */
export function HotelDetailView({ hotelId }: { hotelId: string }) {
  const params = useSearchParams()
  const t = useT()
  const [hotel, setHotel] = useState<HotelDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [activePhoto, setActivePhoto] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const loaded = await api.hotel(hotelId)
        if (!cancelled) {
          setHotel(loaded)
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
  }, [hotelId])

  if (error) {
    return (
      <div className="card">
        <h2 className="card__title">{t('hotel.unavailable')}</h2>
        <p className="muted small" style={{ marginTop: 0, marginBottom: 0 }}>{error}</p>
      </div>
    )
  }
  if (!hotel) {
    return <p className="muted">{t('common.loading')}</p>
  }

  const cover = hotel.photos[activePhoto] ?? hotel.photos[0]
  const cheapest = hotel.roomTypes.length > 0
    ? Math.min(...hotel.roomTypes.map((room) => room.nightlyFrom))
    : null

  return (
    <>
      <h1>{hotel.name}</h1>
      <p style={{ marginTop: 0, marginBottom: 4 }}>
        <RatingBadge average={hotel.ratingAverage} count={hotel.ratingCount} />
      </p>
      <p className="muted" style={{ marginTop: 0 }}>
        {hotel.starRating ? `${'★'.repeat(hotel.starRating)} · ` : ''}
        {t('hotel.hotelIn', {
          place: hotel.district ? `${hotel.district}, ${hotel.city}` : hotel.city,
        })}
        {hotel.roomTypes.length > 0
          && ` · ${t('hotel.roomTypes', { count: hotel.roomTypes.length })}`}
        {cheapest !== null
          && ` · ${t('listing.from', { price: formatMoney(cheapest, hotel.currency) })}`}
      </p>

      {cover && (
        <div className="card" style={{ padding: 0, overflow: 'hidden', marginBottom: 16 }}>
          <Image
            src={cover.url}
            alt={cover.altText ?? hotel.name}
            width={960}
            height={420}
            priority
            style={{ width: '100%', height: 'auto', maxHeight: 420, objectFit: 'cover',
                     display: 'block' }}
          />
          {hotel.photos.length > 1 && (
            <div className="row" style={{ gap: 6, padding: 8 }}>
              {hotel.photos.map((photo, index) => (
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

      <HotelRoomPicker
        hotel={hotel}
        initialCheckIn={params.get('checkIn') ?? ''}
        initialCheckOut={params.get('checkOut') ?? ''}
        initialGuests={Number(params.get('guests') ?? '2')}
      />

      {hotel.description && (
        <div className="card">
          <h2 className="card__title">{t('hotel.about')}</h2>
          <p style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{hotel.description}</p>
        </div>
      )}

      {hotel.roomTypes.length > 0 && (
        <div className="card">
          <h2 className="card__title">{t('hotel.theRooms')}</h2>
          {hotel.roomTypes.map((room) => (
            <div
              key={room.id}
              style={{ borderTop: '1px solid var(--line)', paddingTop: 12, marginTop: 12 }}
            >
              <div className="row" style={{ gap: 8, alignItems: 'center' }}>
                <strong>{room.name}</strong>
                <span className="muted small">
                  {t('hotel.sleeps', { count: room.capacity })}
                  {room.bedConfig && ` · ${room.bedConfig}`}
                  {room.sizeSqm && ` · ${room.sizeSqm} m²`}
                </span>
              </div>
              {room.description && (
                <p className="small" style={{ margin: '4px 0' }}>{room.description}</p>
              )}
              <p className="muted small" style={{ margin: '4px 0' }}>
                {t('listing.from', {
                  price: formatMoney(room.nightlyFrom, hotel.currency),
                })}
                {room.minStayNights > 1
                  && ` · ${t('hotel.minNights', { count: room.minStayNights })}`}
              </p>
              {room.amenities.length > 0 && (
                <div className="row" style={{ gap: 6 }}>
                  {room.amenities.map((amenity) => (
                    <span key={amenity} className="tag">{t(`amenity.${amenity}`)}</span>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {hotel.amenities.length > 0 && (
        <div className="card">
          <h2 className="card__title">{t('hotel.offers')}</h2>
          <div className="row" style={{ gap: 8 }}>
            {hotel.amenities.map((amenity) => (
              <span key={amenity} className="tag">{t(`amenity.${amenity}`)}</span>
            ))}
          </div>
        </div>
      )}

      <div className="card">
        <h2 className="card__title">{t('listing.goodToKnow')}</h2>
        <dl className="definition">
          {hotel.checkInFrom && (
            <>
              <dt>{t('listing.checkInFrom')}</dt>
              <dd>{hotel.checkInFrom.slice(0, 5)}</dd>
            </>
          )}
          {hotel.checkOutBy && (
            <>
              <dt>{t('listing.checkOutBy')}</dt>
              <dd>{hotel.checkOutBy.slice(0, 5)}</dd>
            </>
          )}
          <dt>{t('listing.cancellation')}</dt>
          <dd>{t(`policy.${hotel.cancellationPolicy}`)}</dd>
          <dt>{t('listing.booking')}</dt>
          <dd>{t('hotel.confirmedOnPayment')}</dd>
        </dl>
      </div>

      {hotel.policies && (
        <div className="card">
          <h2 className="card__title">{t('hotel.policies')}</h2>
          <p style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{hotel.policies}</p>
        </div>
      )}

      <div className="card">
        <h2 className="card__title">{t('listing.where')}</h2>
        <p className="small" style={{ marginTop: 0, marginBottom: 0 }}>
          {hotel.addressLine ? `${hotel.addressLine}, ` : ''}
          {hotel.district ? `${hotel.district}, ` : ''}{hotel.city}
        </p>
      </div>
      <ReviewList
        supplyType="HOTEL"
        supplyId={hotel.id}
        average={hotel.ratingAverage}
        count={hotel.ratingCount}
      />
      <ReportListing supplyType="HOTEL" supplyId={hotel.id} />
    </>
  )
}
