import type { InventoryNight, RoomTypeStatus, SupplyStatus } from '../../types'

/** Shared labels and cell colours for the hotel screens. */

export const HOTEL_STATUS_LABELS: Record<SupplyStatus, string> = {
  DRAFT: 'Draft',
  PENDING_REVIEW: 'Under review',
  APPROVED: 'Live',
  REJECTED: 'Not approved',
  PAUSED: 'Paused',
  SUSPENDED: 'Suspended',
}

export const HOTEL_STATUS_COLORS: Record<SupplyStatus, string> = {
  DRAFT: 'default',
  PENDING_REVIEW: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
  PAUSED: 'blue',
  SUSPENDED: 'volcano',
}

export const ROOM_TYPE_STATUS_COLORS: Record<RoomTypeStatus, string> = {
  ACTIVE: 'green',
  INACTIVE: 'default',
}

/**
 * How an inventory cell should read at a glance.
 *
 * <p>Closed and sold out look different on purpose: one is a decision the hotel
 * made and can undo, the other is money already taken.
 */
export function nightAppearance(night: InventoryNight): {
  background: string
  border: string
  label: string
} {
  if (night.stopSell) {
    return { background: '#fff1f0', border: '#ffa39e', label: 'Closed' }
  }
  if (night.remaining === 0) {
    return { background: '#e6f4ff', border: '#91caff', label: 'Sold out' }
  }
  if (night.remaining <= Math.max(1, Math.floor(night.available * 0.25))) {
    return { background: '#fffbe6', border: '#ffe58f', label: 'Nearly full' }
  }
  return { background: '#ffffff', border: '#e4e6eb', label: 'Open' }
}

/** Hotel amenities, grouped the way a hotelier thinks about them. */
export const HOTEL_AMENITY_GROUPS: { label: string; amenities: string[] }[] = [
  { label: 'Service', amenities: ['RECEPTION_24H', 'CONCIERGE', 'ROOM_SERVICE',
      'DAILY_HOUSEKEEPING', 'LAUNDRY_SERVICE', 'TOUR_DESK', 'CURRENCY_EXCHANGE'] },
  { label: 'Food and drink', amenities: ['RESTAURANT', 'BAR', 'BREAKFAST_INCLUDED',
      'BREAKFAST_AVAILABLE'] },
  { label: 'Facilities', amenities: ['WIFI', 'GYM', 'SPA', 'POOL', 'SAUNA', 'ELEVATOR',
      'CONFERENCE_ROOM', 'BUSINESS_CENTRE', 'PARKING_FREE', 'PARKING_PAID',
      'AIRPORT_SHUTTLE'] },
  { label: 'Access', amenities: ['WHEELCHAIR_ACCESSIBLE', 'LUGGAGE_DROPOFF',
      'SELF_CHECK_IN'] },
  { label: 'Safety', amenities: ['SMOKE_ALARM', 'FIRE_EXTINGUISHER', 'FIRST_AID_KIT',
      'SECURITY_CAMERAS_OUTSIDE'] },
  { label: 'Policies', amenities: ['PETS_ALLOWED', 'SMOKING_ALLOWED', 'EVENTS_ALLOWED',
      'LONG_TERM_STAYS'] },
]

/** Room-level amenities, which are a different question from hotel facilities. */
export const ROOM_AMENITY_GROUPS: { label: string; amenities: string[] }[] = [
  { label: 'Comfort', amenities: ['AIR_CONDITIONING', 'HEATING', 'BLACKOUT_CURTAINS',
      'SOUNDPROOFING', 'BALCONY', 'CITY_VIEW', 'MOUNTAIN_VIEW', 'RIVER_VIEW'] },
  { label: 'Bathroom', amenities: ['PRIVATE_BATHROOM', 'SHARED_BATHROOM', 'BATHTUB',
      'SHOWER', 'HAIRDRYER', 'TOILETRIES', 'BATHROBE', 'SLIPPERS'] },
  { label: 'In the room', amenities: ['TV', 'DESK', 'WARDROBE', 'SAFE', 'MINIBAR',
      'KETTLE', 'COFFEE_MACHINE', 'IRON', 'WIFI'] },
  { label: 'Family and access', amenities: ['CRIB', 'HIGH_CHAIR', 'CONNECTING_ROOMS',
      'ACCESSIBLE_BATHROOM'] },
]
