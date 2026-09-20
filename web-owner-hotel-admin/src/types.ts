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
  /**
   * The code itself, returned only by a development server. Absent in any real
   * deployment, where the code arrives by SMS and nowhere else.
   */
  devCode?: string
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
  applicantId: string
  applicantName?: string
  applicantPhone: string
  applicantKycStatus: KycStatus
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
  /**
   * What was booked. `supplyType` says whether to read it as a house listing or a
   * hotel; `roomTypeName` and `rooms` are set only for a hotel stay.
   */
  listing?: {
    id: string
    title: string
    city: string
    district?: string
    coverPhotoUrl?: string
    supplyType: 'PROPERTY' | 'HOTEL'
    roomTypeName?: string
    rooms?: number
  }
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

// --- Step 3: hotels, room types, counted inventory --------------------------

/** Lifecycle shared by every kind of supply (houses and hotels alike). */
export type SupplyStatus = PropertyStatus

export type RoomTypeStatus = 'ACTIVE' | 'INACTIVE'

export interface Hotel {
  id: string
  organizationId: string
  organizationName: string
  name: string
  description?: string
  /** 1–5, or absent when unrated — which is not the same as zero. */
  starRating?: number
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
  status: SupplyStatus
  rejectionReason?: string
  /** What still blocks submitting for review; empty when ready. */
  readinessProblems: string[]
  photos: Photo[]
  roomTypes: RoomType[]
  publishedAt?: string
  createdAt: string
  updatedAt: string
}

export interface RoomType {
  id: string
  hotelId: string
  name: string
  description?: string
  /** Guests per room, not per booking. */
  capacity: number
  bedConfig?: string
  sizeSqm?: number
  basePrice: number
  /** Physical rooms, and the default availability for a night with no override. */
  totalRooms: number
  amenities: string[]
  minStayNights: number
  maxStayNights?: number
  status: RoomTypeStatus
  sortOrder: number
  photos: Photo[]
}

/**
 * One night of one room type.
 *
 * `booked` is maintained by the database from the reservations themselves, so it
 * is read-only here: `available` is the only number a hotel sets.
 */
export interface InventoryNight {
  date: string
  available: number
  booked: number
  remaining: number
  rate: number
  overridden: boolean
  stopSell: boolean
  minStay?: number
}

export interface RoomTypeInventory {
  roomTypeId: string
  name: string
  totalRooms: number
  basePrice: number
  status: RoomTypeStatus
  /** Dense: every day of the requested range, defaults filled in. */
  nights: InventoryNight[]
}

export interface StaffMember {
  userId: string
  phone: string
  fullName?: string
  assignedAt: string
}

export interface OccupancyReport {
  from: string
  to: string
  roomNightsAvailable: number
  roomNightsSold: number
  occupancyPercent: number
  roomRevenue: number
  /** Revenue per room-night sold, which is not the average rate on offer. */
  averageDailyRate: number
  currency: string
  reservations: number
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

// --- trust and safety -------------------------------------------------------

export type PayoutStatus = 'PENDING' | 'BLOCKED' | 'RELEASED' | 'PAID' | 'CANCELLED'

/**
 * What the platform owes a host for one stay.
 *
 * `blockedReason` is the whole story when something is held: `stay_not_started`
 * means nobody checked in, `kyc_required` that the payee is unverified,
 * `listing_flagged` that someone has questioned the listing.
 */
export interface Payout {
  id: string
  bookingId: string
  bookingReference: string
  listingTitle: string
  checkIn: string
  checkOut: string
  amount: number
  currency: string
  status: PayoutStatus
  releaseAfter: string
  blockedReason?: string
  releasedAt?: string
  paidAt?: string
  providerRef?: string
  createdAt: string
}

export type FlagType = 'DUPLICATE_PHOTO' | 'GUEST_REPORT'
export type FlagStatus = 'OPEN' | 'DISMISSED' | 'UPHELD'

export interface ListingFlag {
  id: string
  supplyKind: 'PROPERTY' | 'HOTEL' | 'ROOM_TYPE'
  supplyId: string
  type: FlagType
  status: FlagStatus
  raisedBy?: string
  bookingId?: string
  summary: string
  /** Matching photo ids and distances, or the guest's own words. */
  details: Record<string, unknown>
  resolutionNote?: string
  resolvedAt?: string
  createdAt: string
}

export interface KycSubmission {
  id: string
  userId: string
  userName?: string
  userPhone?: string
  documentType: string
  documentNumber?: string
  fullName: string
  status: KycStatus
  reviewNote?: string
  reviewedAt?: string
  createdAt: string
}

// --- Step 4: payment oversight and analytics --------------------------------

export interface AdminPayment {
  id: string
  bookingId: string
  bookingReference: string
  provider: string
  intent: 'CHARGE' | 'REFUND'
  status: string
  amount: number
  currency: string
  providerRef?: string
  failureCode?: string
  failureMessage?: string
  paidAt?: string
  createdAt: string
}

/** `signatureVerified: false` means the callback arrived unsigned or mis-signed. */
export interface PaymentEventView {
  id: string
  eventType: string
  signatureVerified: boolean
  receivedAt: string
  processedAt?: string
  processingError?: string
}

export interface PaymentDetail {
  payment: AdminPayment
  events: PaymentEventView[]
}

/** The four numbers the spec asks for, plus what explains them. */
export interface AnalyticsOverview {
  from: string
  to: string
  grossValue: number
  refunded: number
  commission: number
  takeRatePercent: number
  bookings: number
  cancelledBookings: number
  cancellationRatePercent: number
  settledCharges: number
  liveListings: number
  liveHotels: number
  currency: string
}

export interface AnalyticsPoint {
  date: string
  grossValue: number
  commission: number
  bookings: number
}

// --- reviews, messaging and payout runs (step 5) ---------------------------

export interface ModeratedReview {
  id: string
  bookingId: string
  bookingReference: string
  /** SUPPLY when a guest reviewed the place, GUEST when a host reviewed the person. */
  subject: 'SUPPLY' | 'GUEST'
  authorName: string
  rating: number
  comment: string | null
  visible: boolean
  status: 'PENDING' | 'PUBLISHED' | 'HIDDEN'
  hiddenReason: string | null
  createdAt: string
}

export interface FlaggedMessage {
  id: string
  reason: string
  senderName: string
  senderPhone: string
  bookingReference: string
  body: string
  sentAt: string
}

export interface PayoutBatch {
  id: string
  payoutCount: number
  total: number
  currency: string
  status: 'OPEN' | 'EXPORTED' | 'SETTLED'
  createdAt: string
  exportedAt: string | null
}

export interface TransferLine {
  payeeId: string
  payeeName: string
  payeePhone: string
  organizationName: string | null
  amount: number
  currency: string
  bookings: string[]
}

export interface Conversation {
  id: string
  bookingId: string
  bookingReference: string
  listingTitle: string
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
  flaggedReason: string | null
  sentAt: string
}
