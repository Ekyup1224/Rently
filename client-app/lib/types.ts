/** Mirrors of the backend DTOs used by the guest app. */

export type Role = 'CLIENT' | 'HOUSE_OWNER' | 'HOTEL_MANAGER' | 'HOTEL_STAFF' | 'SUPER_ADMIN'

export type UserStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'DELETED'

export interface RoleGrant {
  role: Role
  organizationId?: string
  organizationName?: string
}

export interface User {
  id: string
  phone: string
  email?: string
  fullName?: string
  status: UserStatus
  kycStatus: 'NONE' | 'PENDING' | 'VERIFIED' | 'REJECTED'
  locale: string
  phoneVerified: boolean
  emailVerified: boolean
  hasPassword: boolean
  roles: RoleGrant[]
  lastLoginAt?: string
  createdAt: string
}

export interface AuthSession {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  refreshToken: string
  user: User
}

export interface OtpChallenge {
  message: string
  resendAfterSeconds: number
  /**
   * The code itself, returned only by a development server. Absent in any real
   * deployment, where the code arrives by SMS and nowhere else.
   */
  devCode?: string
}

export interface HostApplication {
  id: string
  requestedRole: Role
  organizationName?: string
  organizationRegistrationNo?: string
  note?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'
  decisionNote?: string
  decidedAt?: string
  createdAt: string
}

export interface ApiError {
  code: string
  message: string
  fieldErrors?: { field: string; message: string }[]
  timestamp: string
}

// --- Step 2: listings, bookings, payments -----------------------------------

export type PropertyType =
  | 'APARTMENT' | 'HOUSE' | 'GER' | 'CABIN' | 'VILLA' | 'STUDIO' | 'TOWNHOUSE' | 'GUESTHOUSE'

export type CancellationPolicy = 'FLEXIBLE' | 'MODERATE' | 'STRICT'

export interface Photo {
  id: string
  url: string
  altText?: string
  sortOrder: number
  cover: boolean
}

export type SupplyType = 'PROPERTY' | 'HOTEL'

/**
 * One search result card, for either supply type.
 *
 * <p>`supplyType` says which it is and where it links: `/listings/{id}` for a
 * house, `/hotels/{id}` for a hotel. Fields that apply to only one type are
 * absent on the other rather than faked — `bedrooms` for a hotel, `starRating`
 * for a house.
 */
export interface ListingSummary {
  supplyType: SupplyType
  id: string
  title: string
  propertyType?: PropertyType
  starRating?: number
  city: string
  district?: string
  latitude?: number
  longitude?: number
  /** For a hotel, the cheapest room type's rate. */
  nightlyFrom: number
  cleaningFee: number
  currency: string
  maxGuests: number
  bedrooms?: number
  beds?: number
  bathrooms?: number
  instantBook: boolean
  cancellationPolicy: CancellationPolicy
  minStayNights: number
  coverPhotoUrl?: string
  photoCount: number
  /** Hotels only. */
  roomTypeCount?: number
  /** Guest reviews. Null until this listing has a published one. */
  ratingAverage?: number
  ratingCount: number
}

export interface ListingDetail {
  id: string
  title: string
  description?: string
  propertyType: PropertyType
  maxGuests: number
  bedrooms: number
  beds: number
  bathrooms: number
  district?: string
  city: string
  country: string
  latitude?: number
  longitude?: number
  amenities: string[]
  houseRules?: string
  checkInFrom?: string
  checkOutBy?: string
  nightlyFrom: number
  cleaningFee: number
  currency: string
  minStayNights: number
  maxStayNights?: number
  cancellationPolicy: CancellationPolicy
  instantBook: boolean
  photos: Photo[]
  /** Guest reviews. Null until the first is published. */
  ratingAverage?: number
  ratingCount: number
  host: { displayName: string; since: string; identityVerified: boolean }
}

export interface CalendarDay {
  date: string
  /** The public calendar reports booked nights as BLOCKED. */
  status: 'AVAILABLE' | 'BLOCKED'
  price: number
  overridden: boolean
  minStay?: number
}

export interface Quote {
  supplyType: SupplyType
  /** The property or room type that was priced. */
  supplyId: string
  /** Rooms priced, for a hotel stay; always 1 for a house. */
  rooms: number
  checkIn: string
  checkOut: string
  nights: number
  guests: number
  currency: string
  nightlyRates: { date: string; amount: number; overridden: boolean }[]
  nightlySubtotal: number
  cleaningFee: number
  guestServiceFee: number
  tax: number
  total: number
  cancellationPolicy: CancellationPolicy
  refundSchedule: { cancelBefore: string; refundAmount: number; description: string }[]
  instantBook: boolean
}

export type BookingStatus =
  | 'PENDING_HOST_APPROVAL' | 'PENDING_PAYMENT' | 'CONFIRMED' | 'CHECKED_IN'
  | 'CHECKED_OUT' | 'COMPLETED' | 'DECLINED' | 'EXPIRED'
  | 'CANCELLED_BY_GUEST' | 'CANCELLED_BY_HOST'

export interface Booking {
  id: string
  reference: string
  status: BookingStatus
  paymentStatus: 'UNPAID' | 'PROCESSING' | 'PAID' | 'PARTIALLY_REFUNDED' | 'REFUNDED' | 'FAILED'
  checkIn: string
  checkOut: string
  nights: number
  guestCount: number
  currency: string
  nightlySubtotal: number
  cleaningFee: number
  guestServiceFee?: number
  tax: number
  total?: number
  cancellationPolicy: CancellationPolicy
  guestMessage?: string
  hostResponseNote?: string
  refundAmount?: number
  cancellationReason?: string
  /** Deadline for the current pending state, so the UI can count down. */
  expiresAt?: string
  confirmedAt?: string
  createdAt: string
  /**
   * The supply booked. `supplyType` decides where the card links — `/listings/{id}`
   * for a house, `/hotels/{id}` for a hotel — and `roomTypeName` is what
   * distinguishes two reservations at the same hotel.
   */
  listing?: {
    id: string
    title: string
    city: string
    district?: string
    coverPhotoUrl?: string
    supplyType: SupplyType
    roomTypeName?: string
    rooms?: number
  }
  counterpartyName?: string
}

export interface Payment {
  id: string
  bookingId: string
  provider: 'QPAY' | 'SOCIALPAY' | 'STRIPE' | 'SIMULATED'
  intent: 'CHARGE' | 'REFUND'
  status: 'CREATED' | 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'EXPIRED'
  amount: number
  currency: string
  /** Provider-specific: QR text, bank deeplinks, or a hosted checkout URL. */
  checkout?: Record<string, unknown>
  expiresAt?: string
  paidAt?: string
  failureMessage?: string
  createdAt: string
}

// --- Step 3: hotels ---------------------------------------------------------

export interface HotelRoomType {
  id: string
  name: string
  description?: string
  /** Guests per room. Several rooms can be booked together. */
  capacity: number
  bedConfig?: string
  sizeSqm?: number
  nightlyFrom: number
  amenities: string[]
  minStayNights: number
  maxStayNights?: number
  photos: Photo[]
}

export interface HotelDetail {
  id: string
  name: string
  description?: string
  starRating?: number
  /** Published, unlike a private home's address. */
  addressLine?: string
  district?: string
  city: string
  country: string
  latitude?: number
  longitude?: number
  amenities: string[]
  policies?: string
  checkInFrom?: string
  checkOutBy?: string
  currency: string
  cancellationPolicy: CancellationPolicy
  photos: Photo[]
  /** Guest reviews. Distinct from starRating, which is the official class. */
  ratingAverage?: number
  ratingCount: number
  roomTypes: HotelRoomType[]
}

/**
 * Whether a room type can take a stay, and what it would cost.
 *
 * @property roomsLeft the fewest rooms free across the stay's nights — a stay
 *   needs every night, so the tightest night decides
 */
export interface RoomTypeAvailability {
  roomTypeId: string
  name: string
  capacity: number
  bookable: boolean
  unavailableReason?: string
  roomsLeft: number
  nightlyFrom: number
  totalForStay?: number
  currency: string
  photos: Photo[]
}

/** A review as anyone may read it. Never carries a phone number. */
export interface Review {
  id: string
  bookingId: string
  /** SUPPLY when a guest reviewed the place, GUEST when a host reviewed the person. */
  subject: 'SUPPLY' | 'GUEST'
  authorName: string
  rating: number
  subRatings: Record<string, number> | null
  comment: string | null
  response: string | null
  respondedAt: string | null
  /** False while the blind period is still running. Only its author sees it. */
  visible: boolean
  createdAt: string
}

/** One booking's message thread, as one of its two participants sees it. */
export interface Conversation {
  id: string
  bookingId: string
  bookingReference: string
  listingTitle: string
  /** The other person's first name. */
  withName: string
  checkIn: string
  checkOut: string
  unread: number
  lastMessageAt: string | null
}

export interface Message {
  id: string
  body: string
  mine: boolean
  senderName: string
  /** Only ever set on your own messages, and only when they were flagged. */
  flaggedReason: string | null
  sentAt: string
}
