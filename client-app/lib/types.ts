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

/** One search result card. */
export interface ListingSummary {
  id: string
  title: string
  propertyType: PropertyType
  city: string
  district?: string
  latitude?: number
  longitude?: number
  nightlyFrom: number
  cleaningFee: number
  currency: string
  maxGuests: number
  bedrooms: number
  beds: number
  bathrooms: number
  instantBook: boolean
  cancellationPolicy: CancellationPolicy
  minStayNights: number
  coverPhotoUrl?: string
  photoCount: number
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
  propertyId: string
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
  listing?: { id: string; title: string; city: string; district?: string; coverPhotoUrl?: string }
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
