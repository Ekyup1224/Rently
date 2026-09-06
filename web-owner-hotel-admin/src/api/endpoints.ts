import { request } from './client'
import type {
  AuditLogRow, AuthSession, Booking, CalendarDay, CommissionRule, EarningsSummary, KycStatus,
  OtpChallenge, Page, Photo, Property, PropertyStatus, PropertyType, Role, User, UserStatus,
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

function queryString(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
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
