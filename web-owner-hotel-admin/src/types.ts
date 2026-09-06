/** Mirrors of the backend DTOs. Kept hand-written until an OpenAPI generator is in place. */

export type Role = 'CLIENT' | 'HOUSE_OWNER' | 'HOTEL_MANAGER' | 'HOTEL_STAFF' | 'SUPER_ADMIN'

export type UserStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'DELETED'

export type KycStatus = 'NONE' | 'PENDING' | 'VERIFIED' | 'REJECTED'

export interface RoleGrant {
  role: Role
  /** Set only for hotel-side roles, which are always scoped to a business. */
  organizationId?: string
  organizationName?: string
}

export interface User {
  id: string
  phone: string
  email?: string
  fullName?: string
  status: UserStatus
  kycStatus: KycStatus
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

/** Shape of every paged endpoint, sized for AG Grid's row models. */
export interface Page<T> {
  rows: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface AuditLogRow {
  id: string
  actorId?: string
  action: string
  targetType?: string
  targetId?: string
  metadata: Record<string, unknown>
  ip?: string
  createdAt: string
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

/** The single error shape the backend returns for every failure. */
export interface ApiError {
  code: string
  message: string
  fieldErrors?: { field: string; message: string }[]
  timestamp: string
}

// --- Step 2: listings, bookings, payments -----------------------------------

export type PropertyType =
  | 'APARTMENT' | 'HOUSE' | 'GER' | 'CABIN' | 'VILLA' | 'STUDIO' | 'TOWNHOUSE' | 'GUESTHOUSE'

export type PropertyStatus =
  | 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'PAUSED' | 'SUSPENDED'

export type CancellationPolicy = 'FLEXIBLE' | 'MODERATE' | 'STRICT'

export interface Photo {
  id: string
  url: string
  altText?: string
  sortOrder: number
  cover: boolean
  width?: number
  height?: number
}

export interface Property {
  id: string
  ownerId: string
  title: string
  description?: string
  propertyType: PropertyType
  maxGuests: number
  bedrooms: number
  beds: number
  bathrooms: number
  addressLine?: string
  district?: string
  city: string
  country: string
  latitude?: number
  longitude?: number
  amenities: string[]
  houseRules?: string
  checkInFrom?: string
  checkOutBy?: string
  basePrice: number
  cleaningFee: number
  currency: string
  minStayNights: number
  maxStayNights?: number
  cancellationPolicy: CancellationPolicy
  instantBook: boolean
  status: PropertyStatus
  rejectionReason?: string
  /** What still blocks submitting for review; empty when ready. */
  readinessProblems: string[]
  photos: Photo[]
  publishedAt?: string
  createdAt: string
  updatedAt: string
}

export type DayStatus = 'AVAILABLE' | 'BLOCKED' | 'BOOKED'

export interface CalendarDay {
  date: string
  status: DayStatus
  price: number
  /** True when `price` comes from a calendar override rather than the base price. */
  overridden: boolean
  minStay?: number
}

export type BookingStatus =
  | 'PENDING_HOST_APPROVAL' | 'PENDING_PAYMENT' | 'CONFIRMED' | 'CHECKED_IN'
  | 'CHECKED_OUT' | 'COMPLETED' | 'DECLINED' | 'EXPIRED'
  | 'CANCELLED_BY_GUEST' | 'CANCELLED_BY_HOST'

export type BookingPaymentStatus =
  | 'UNPAID' | 'PROCESSING' | 'PAID' | 'PARTIALLY_REFUNDED' | 'REFUNDED' | 'FAILED'

export interface Booking {
  id: string
  reference: string
  status: BookingStatus
  paymentStatus: BookingPaymentStatus
  checkIn: string
  checkOut: string
  nights: number
  guestCount: number
  currency: string
  nightlySubtotal: number
  cleaningFee: number
  /** Guest-side only; absent on the host's view. */
  guestServiceFee?: number
  tax: number
  /** Guest-side only. */
  total?: number
  /** Host-side only. */
  hostCommission?: number
  /** Host-side only. */
  hostPayout?: number
  cancellationPolicy: CancellationPolicy
  guestMessage?: string
  hostResponseNote?: string
  refundAmount?: number
  cancellationReason?: string
  expiresAt?: string
  confirmedAt?: string
  createdAt: string
  listing?: { id: string; title: string; city: string; district?: string; coverPhotoUrl?: string }
  counterpartyName?: string
  viewer: 'GUEST' | 'HOST'
}

export interface EarningsSummary {
  from: string
  to: string
  currency: string
  earnedFromCompletedStays: number
  confirmedUpcoming: number
  commissionWithheld: number
  completedStays: number
  upcomingStays: number
  byMonth: { month: string; earned: number; stays: number }[]
}

export interface CommissionRule {
  id: string
  scope: 'GLOBAL' | 'PROPERTY_TYPE' | 'HOTEL'
  category?: string
  hostFeePercent: number
  guestFeePercent: number
  effectiveFrom: string
  /** Null means this is the rule currently in force. */
  effectiveTo?: string
  note?: string
  createdAt: string
}
