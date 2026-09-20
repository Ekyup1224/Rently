import { request } from './client'
import type {
  AuditLogRow, AuthSession, Booking, CalendarDay, CommissionRule, EarningsSummary, Hotel,
  AdminPayment, AnalyticsOverview, AnalyticsPoint, HostApplication, KycStatus, KycSubmission,
  Conversation, FlaggedMessage, ListingFlag, Message, ModeratedReview, OccupancyReport,
  OtpChallenge, Page, PaymentDetail, Payout, PayoutBatch, Photo, Property, PropertyStatus,
  PropertyType, Role, RoomType, RoomTypeInventory, StaffMember, SupplyStatus, TransferLine,
  User, UserStatus,
} from '../types'

export const auth = {
  requestOtp: (phone: string, locale = 'mn') =>
    request<OtpChallenge>('/auth/otp/request', {
      method: 'POST', body: { phone, locale }, anonymous: true,
    }),

  verifyOtp: (phone: string, code: string, deviceLabel: string) =>
    request<AuthSession>('/auth/otp/verify', {
      method: 'POST', body: { phone, code, deviceLabel }, anonymous: true,
    }),

  login: (email: string, password: string, deviceLabel: string) =>
    request<AuthSession>('/auth/login', {
      method: 'POST', body: { email, password, deviceLabel }, anonymous: true,
    }),

  logout: (refreshToken: string) =>
    request<void>('/auth/logout', { method: 'POST', body: { refreshToken } }),

  me: () => request<User>('/auth/me'),
}

export const users = {
  profile: () => request<User>('/users/me'),

  updateProfile: (patch: { fullName?: string; email?: string; locale?: string }) =>
    request<User>('/users/me', { method: 'PATCH', body: patch }),
}

export interface UserQuery {
  q?: string
  status?: UserStatus
  kycStatus?: KycStatus
  role?: Role
  page: number
  size: number
  sort?: string
}

function queryString(
  params: Record<string, string | number | string[] | undefined>,
): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === '') {
      continue
    }
    // Repeated rather than comma-joined: Spring binds `?status=A&status=B` to a
    // List, which is how the trust queues filter by several statuses at once.
    if (Array.isArray(value)) {
      value.forEach((entry) => search.append(key, entry))
    } else {
      search.set(key, String(value))
    }
  }
  return search.toString()
}

export const admin = {
  listUsers: (query: UserQuery) =>
    request<Page<User>>(`/admin/users?${queryString({ ...query })}`),

  getUser: (userId: string) => request<User>(`/admin/users/${userId}`),

  changeStatus: (userId: string, status: UserStatus, reason: string) =>
    request<User>(`/admin/users/${userId}/status`, {
      method: 'PATCH', body: { status, reason },
    }),

  changeKycStatus: (userId: string, kycStatus: KycStatus) =>
    request<User>(`/admin/users/${userId}/kyc-status`, {
      method: 'PATCH', body: { kycStatus },
    }),

  grantRole: (userId: string, role: Role, organizationId?: string) =>
    request<User>(`/admin/users/${userId}/roles`, {
      method: 'POST', body: { role, organizationId: organizationId ?? null },
    }),

  revokeRole: (userId: string, role: Role, organizationId?: string) =>
    request<User>(
      `/admin/users/${userId}/roles/${role}?${queryString({ organizationId })}`,
      { method: 'DELETE' },
    ),

  listAuditLogs: (query: { actorId?: string; action?: string; targetId?: string; page: number; size: number }) =>
    request<Page<AuditLogRow>>(`/admin/audit-logs?${queryString({ ...query })}`),
}

// --- Step 2: listings, calendar, bookings, review ---------------------------

export const owner = {
  listProperties: (query: { status?: PropertyStatus; page: number; size: number; sort?: string }) =>
    request<Page<Property>>(`/owner/properties?${queryString({ ...query })}`),

  getProperty: (propertyId: string) => request<Property>(`/owner/properties/${propertyId}`),

  createProperty: (body: {
    title: string; propertyType: PropertyType; city: string; maxGuests: number
  }) => request<Property>('/owner/properties', { method: 'POST', body }),

  updateProperty: (propertyId: string, patch: Record<string, unknown>) =>
    request<Property>(`/owner/properties/${propertyId}`, { method: 'PATCH', body: patch }),

  submitProperty: (propertyId: string) =>
    request<Property>(`/owner/properties/${propertyId}/submit`, { method: 'POST' }),

  pauseProperty: (propertyId: string) =>
    request<Property>(`/owner/properties/${propertyId}/pause`, { method: 'POST' }),

  resumeProperty: (propertyId: string) =>
    request<Property>(`/owner/properties/${propertyId}/resume`, { method: 'POST' }),

  deleteProperty: (propertyId: string) =>
    request<void>(`/owner/properties/${propertyId}`, { method: 'DELETE' }),

  /** Step one of a photo upload: a short-lived URL to PUT the file to. */
  photoUploadUrl: (propertyId: string, body: { contentType: string; sizeBytes: number }) =>
    request<{ uploadUrl: string; storageKey: string; expiresAt: string }>(
      `/owner/properties/${propertyId}/photos/upload-url`, { method: 'POST', body }),

  /** Step two, after the PUT: register the file against the listing. */
  confirmPhoto: (propertyId: string, body: { storageKey: string; altText?: string }) =>
    request<Photo>(`/owner/properties/${propertyId}/photos`, { method: 'POST', body }),

  reorderPhotos: (propertyId: string, body: { photoIdsInOrder: string[]; coverPhotoId?: string }) =>
    request<Photo[]>(`/owner/properties/${propertyId}/photos/order`,
      { method: 'PATCH', body }),

  deletePhoto: (propertyId: string, photoId: string) =>
    request<void>(`/owner/properties/${propertyId}/photos/${photoId}`, { method: 'DELETE' }),

  calendar: (propertyId: string, from: string, to: string) =>
    request<CalendarDay[]>(
      `/owner/properties/${propertyId}/calendar?${queryString({ from, to })}`),

  updateCalendar: (propertyId: string, body: {
    from: string; to: string; weekdays?: string[]; blocked?: boolean
    price?: number; clearPrice?: boolean; minStayNights?: number; clearMinStay?: boolean
  }) => request<{ daysUpdated: number }>(`/owner/properties/${propertyId}/calendar`,
    { method: 'PUT', body }),

  clearCalendar: (propertyId: string, from: string, to: string) =>
    request<{ daysCleared: number }>(
      `/owner/properties/${propertyId}/calendar?${queryString({ from, to })}`,
      { method: 'DELETE' }),

  listBookings: (query: { scope?: string; page: number; size: number; sort?: string }) =>
    request<Page<Booking>>(`/owner/bookings?${queryString({ ...query })}`),

  approveBooking: (bookingId: string, note?: string) =>
    request<Booking>(`/owner/bookings/${bookingId}/approve`, { method: 'POST', body: { note } }),

  declineBooking: (bookingId: string, note?: string) =>
    request<Booking>(`/owner/bookings/${bookingId}/decline`, { method: 'POST', body: { note } }),

  cancelBooking: (bookingId: string, reason?: string) =>
    request<Booking>(`/owner/bookings/${bookingId}/cancel`, { method: 'POST', body: { reason } }),

  /** A house has no front desk, so the host confirms the guest turned up. */
  checkIn: (bookingId: string) =>
    request<Booking>(`/owner/bookings/${bookingId}/check-in`, { method: 'POST' }),

  checkOut: (bookingId: string) =>
    request<Booking>(`/owner/bookings/${bookingId}/check-out`, { method: 'POST' }),

  earnings: (from: string, to: string) =>
    request<EarningsSummary>(`/owner/earnings/summary?${queryString({ from, to })}`),
}

export const adminListings = {
  list: (query: { status?: PropertyStatus; page: number; size: number }) =>
    request<Page<Property>>(`/admin/properties?${queryString({ ...query })}`),

  get: (propertyId: string) => request<Property>(`/admin/properties/${propertyId}`),

  setStatus: (propertyId: string, status: PropertyStatus, reason?: string) =>
    request<Property>(`/admin/properties/${propertyId}/status`,
      { method: 'PATCH', body: { status, reason } }),

  commissionRules: () => request<CommissionRule[]>('/admin/commission-rules'),

  createCommissionRule: (body: {
    scope: 'GLOBAL' | 'PROPERTY_TYPE'; category?: string
    hostFeePercent: number; guestFeePercent: number; note?: string
  }) => request<CommissionRule>('/admin/commission-rules', { method: 'POST', body }),
}

/** Uploads a file straight to storage using a presigned URL, bypassing the API. */
export async function putToStorage(uploadUrl: string, file: File): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    // Must match the contentType the URL was signed for, or storage rejects it.
    headers: { 'Content-Type': file.type },
    body: file,
  })
  if (!response.ok) {
    throw new Error(`Upload failed with status ${response.status}`)
  }
}

// --- Step 3: hotels ---------------------------------------------------------

export const hotels = {
  list: (query: { status?: SupplyStatus; page: number; size: number }) =>
    request<Page<Hotel>>(`/hotel/hotels?${queryString({ ...query })}`),

  get: (hotelId: string) => request<Hotel>(`/hotel/hotels/${hotelId}`),

  create: (body: { name: string; city: string; organizationId?: string }) =>
    request<Hotel>('/hotel/hotels', { method: 'POST', body }),

  update: (hotelId: string, patch: Record<string, unknown>) =>
    request<Hotel>(`/hotel/hotels/${hotelId}`, { method: 'PATCH', body: patch }),

  submit: (hotelId: string) =>
    request<Hotel>(`/hotel/hotels/${hotelId}/submit`, { method: 'POST' }),

  pause: (hotelId: string) =>
    request<Hotel>(`/hotel/hotels/${hotelId}/pause`, { method: 'POST' }),

  resume: (hotelId: string) =>
    request<Hotel>(`/hotel/hotels/${hotelId}/resume`, { method: 'POST' }),

  photoUploadUrl: (hotelId: string, body: { contentType: string; sizeBytes: number }) =>
    request<{ uploadUrl: string; storageKey: string; expiresAt: string }>(
      `/hotel/hotels/${hotelId}/photos/upload-url`, { method: 'POST', body }),

  confirmPhoto: (hotelId: string, body: { storageKey: string; altText?: string }) =>
    request<Photo>(`/hotel/hotels/${hotelId}/photos`, { method: 'POST', body }),

  deletePhoto: (hotelId: string, photoId: string) =>
    request<void>(`/hotel/hotels/${hotelId}/photos/${photoId}`, { method: 'DELETE' }),

  reorderPhotos: (hotelId: string, body: { photoIdsInOrder: string[]; coverPhotoId?: string }) =>
    request<Photo[]>(`/hotel/hotels/${hotelId}/photos/order`, { method: 'PATCH', body }),

  // --- room types ---

  listRoomTypes: (hotelId: string) =>
    request<RoomType[]>(`/hotel/hotels/${hotelId}/room-types`),

  createRoomType: (hotelId: string, body: {
    name: string; capacity: number; totalRooms: number; basePrice: number
  }) => request<RoomType>(`/hotel/hotels/${hotelId}/room-types`, { method: 'POST', body }),

  updateRoomType: (roomTypeId: string, patch: Record<string, unknown>) =>
    request<RoomType>(`/hotel/room-types/${roomTypeId}`, { method: 'PATCH', body: patch }),

  deleteRoomType: (roomTypeId: string) =>
    request<void>(`/hotel/room-types/${roomTypeId}`, { method: 'DELETE' }),

  roomTypePhotoUploadUrl: (roomTypeId: string, body: {
    contentType: string; sizeBytes: number
  }) => request<{ uploadUrl: string; storageKey: string; expiresAt: string }>(
    `/hotel/room-types/${roomTypeId}/photos/upload-url`, { method: 'POST', body }),

  confirmRoomTypePhoto: (roomTypeId: string, body: { storageKey: string; altText?: string }) =>
    request<Photo>(`/hotel/room-types/${roomTypeId}/photos`, { method: 'POST', body }),

  deleteRoomTypePhoto: (roomTypeId: string, photoId: string) =>
    request<void>(`/hotel/room-types/${roomTypeId}/photos/${photoId}`, { method: 'DELETE' }),

  // --- inventory ---

  inventory: (hotelId: string, from: string, to: string) =>
    request<RoomTypeInventory[]>(
      `/hotel/hotels/${hotelId}/inventory?${queryString({ from, to })}`),

  /** Bulk edit across a range. `availableCount` cannot go below the rooms sold. */
  updateInventory: (roomTypeId: string, body: {
    from: string; to: string; weekdays?: string[]; availableCount?: number
    rate?: number; clearRate?: boolean; stopSell?: boolean
    minStayNights?: number; clearMinStay?: boolean
  }) => request<{ nightsUpdated: number }>(`/hotel/room-types/${roomTypeId}/inventory`,
    { method: 'PUT', body }),

  clearInventory: (roomTypeId: string, from: string, to: string) =>
    request<{ nightsCleared: number }>(
      `/hotel/room-types/${roomTypeId}/inventory?${queryString({ from, to })}`,
      { method: 'DELETE' }),

  // --- front desk ---

  reservations: (query: { hotelId: string; scope?: string; page: number; size: number }) =>
    request<Page<Booking>>(`/hotel/bookings?${queryString({ ...query })}`),

  checkIn: (bookingId: string) =>
    request<Booking>(`/hotel/bookings/${bookingId}/check-in`, { method: 'POST' }),

  checkOut: (bookingId: string) =>
    request<Booking>(`/hotel/bookings/${bookingId}/check-out`, { method: 'POST' }),

  cancelReservation: (bookingId: string, reason?: string) =>
    request<Booking>(`/hotel/bookings/${bookingId}/cancel`, { method: 'POST', body: { reason } }),

  occupancy: (hotelId: string, from: string, to: string) =>
    request<OccupancyReport>(`/hotel/reports/occupancy?${queryString({ hotelId, from, to })}`),

  // --- staff ---

  listStaff: (hotelId: string) => request<StaffMember[]>(`/hotel/hotels/${hotelId}/staff`),

  addStaff: (hotelId: string, phone: string) =>
    request<StaffMember>(`/hotel/hotels/${hotelId}/staff`, { method: 'POST', body: { phone } }),

  removeStaff: (hotelId: string, userId: string) =>
    request<void>(`/hotel/hotels/${hotelId}/staff/${userId}`, { method: 'DELETE' }),
}

export const adminHotels = {
  list: (query: { status?: SupplyStatus; page: number; size: number }) =>
    request<Page<Hotel>>(`/admin/hotels?${queryString({ ...query })}`),

  setStatus: (hotelId: string, status: SupplyStatus, reason?: string) =>
    request<Hotel>(`/admin/hotels/${hotelId}/status`,
      { method: 'PATCH', body: { status, reason } }),

  hostApplications: (query: { status?: string; page: number; size: number }) =>
    request<Page<HostApplication>>(`/admin/host-applications?${queryString({ ...query })}`),

  approveApplication: (applicationId: string, note?: string) =>
    request<HostApplication>(`/admin/host-applications/${applicationId}/approve`,
      { method: 'POST', body: { note } }),

  rejectApplication: (applicationId: string, note: string) =>
    request<HostApplication>(`/admin/host-applications/${applicationId}/reject`,
      { method: 'POST', body: { note } }),
}

/** Trust and safety: held money, flagged listings, identity documents. */
export const trust = {
  /** Runs the release sweep now, applying the same rules the nightly job does. */
  releaseDue: () =>
    request<{ released: number }>('/admin/payouts/release-due', { method: 'POST' }),

  payouts: (query: { status?: string[]; page: number; size: number }) =>
    request<Page<Payout>>(`/admin/payouts?${queryString({ ...query })}`),

  releasePayout: (payoutId: string, note?: string) =>
    request<Payout>(`/admin/payouts/${payoutId}/release`, { method: 'POST', body: { note } }),

  holdPayout: (payoutId: string, reason: string) =>
    request<Payout>(`/admin/payouts/${payoutId}/hold`, { method: 'POST', body: { reason } }),

  markPaid: (payoutId: string, providerRef: string, note?: string) =>
    request<Payout>(`/admin/payouts/${payoutId}/mark-paid`,
      { method: 'POST', body: { providerRef, note } }),

  flags: (query: { status?: string[]; page: number; size: number }) =>
    request<Page<ListingFlag>>(`/admin/flags?${queryString({ ...query })}`),

  dismissFlag: (flagId: string, note?: string) =>
    request<ListingFlag>(`/admin/flags/${flagId}/dismiss`, { method: 'POST', body: { note } }),

  upholdFlag: (flagId: string, note?: string) =>
    request<ListingFlag>(`/admin/flags/${flagId}/uphold`, { method: 'POST', body: { note } }),

  kyc: (query: { status?: string[]; page: number; size: number }) =>
    request<Page<KycSubmission>>(`/admin/kyc?${queryString({ ...query })}`),

  reviewKyc: (submissionId: string, outcome: 'VERIFIED' | 'REJECTED', note?: string) =>
    request<KycSubmission>(`/admin/kyc/${submissionId}/review`,
      { method: 'POST', body: { outcome, note } }),
}

/** A host's own payouts. */
export const hostPayouts = {
  mine: (query: { page: number; size: number }) =>
    request<Page<Payout>>(`/owner/payouts?${queryString({ ...query })}`),

  forHotel: (hotelId: string, query: { page: number; size: number }) =>
    request<Page<Payout>>(`/hotel/payouts?${queryString({ hotelId, ...query })}`),
}

/** Payment oversight and the platform's own numbers. */
export const adminPayments = {
  search: (query: {
    status?: string; provider?: string; intent?: string
    from?: string; to?: string; query?: string; page: number; size: number
  }) => request<Page<AdminPayment>>(`/admin/payments?${queryString({ ...query })}`),

  detail: (paymentId: string) =>
    request<PaymentDetail>(`/admin/payments/${paymentId}`),

  refund: (paymentId: string, amount: number, reason: string) =>
    request<unknown>(`/admin/payments/${paymentId}/refund`,
      { method: 'POST', body: { amount, reason } }),
}

export const analytics = {
  overview: (from: string, to: string) =>
    request<AnalyticsOverview>(`/admin/analytics/overview?${queryString({ from, to })}`),

  series: (from: string, to: string, interval: 'day' | 'week' = 'day') =>
    request<AnalyticsPoint[]>(`/admin/analytics/series?${queryString({ from, to, interval })}`),
}

/** Reviews and flagged messages: the two things a moderator works through. */
export const moderation = {
  reviews: (status: string[] | undefined, page: number, size = 25) =>
    request<Page<ModeratedReview>>(
      `/admin/reviews?${queryString({ status, page, size })}`),

  hideReview: (reviewId: string, reason: string) =>
    request<ModeratedReview>(`/admin/reviews/${reviewId}/hide`,
      { method: 'POST', body: { reason } }),

  restoreReview: (reviewId: string) =>
    request<ModeratedReview>(`/admin/reviews/${reviewId}/restore`, { method: 'POST' }),

  flaggedMessages: (page: number, size = 25) =>
    request<Page<FlaggedMessage>>(`/admin/messages/flagged?${queryString({ page, size })}`),
}

/** Turning released payouts into one bank file at a time. */
export const payoutBatches = {
  list: (page: number, size = 25) =>
    request<Page<PayoutBatch>>(`/admin/payout-batches?${queryString({ page, size })}`),

  assemble: () => request<PayoutBatch>('/admin/payout-batches', { method: 'POST' }),

  lines: (batchId: string) =>
    request<TransferLine[]>(`/admin/payout-batches/${batchId}/lines`),

  markExported: (batchId: string) =>
    request<PayoutBatch>(`/admin/payout-batches/${batchId}/exported`, { method: 'POST' }),

  settle: (batchId: string, providerRef: string) =>
    request<PayoutBatch>(`/admin/payout-batches/${batchId}/settled`,
      { method: 'POST', body: { providerRef } }),
}

/** One thread per booking, the same endpoints the guest app uses. */
export const conversations = {
  inbox: () => request<Page<Conversation>>('/conversations?size=50'),

  openForBooking: (bookingId: string) =>
    request<Conversation>(`/conversations/for-booking/${bookingId}`, { method: 'POST' }),

  messages: (conversationId: string) =>
    request<Page<Message>>(`/conversations/${conversationId}/messages?size=100`),

  send: (conversationId: string, body: string) =>
    request<Message>(`/conversations/${conversationId}/messages`,
      { method: 'POST', body: { body } }),

  markRead: (conversationId: string) =>
    request<void>(`/conversations/${conversationId}/read`, { method: 'POST' }),
}
