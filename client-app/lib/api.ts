import type {
  ApiError, AuthSession, Booking, CalendarDay, Conversation, HostApplication, HotelDetail,
  ListingDetail, ListingSummary, Message, OtpChallenge, Payment, Quote, Review,
  RoomTypeAvailability, SupplyType, User,
} from './types'

/** Shape of every paged endpoint. */
export interface Page<T> {
  rows: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface SearchQuery {
  q?: string
  city?: string
  checkIn?: string
  checkOut?: string
  guests?: number
  /** PROPERTY, HOTEL, or omit for both. */
  supplyTypes?: string[]
  types?: string[]
  amenities?: string[]
  instantBook?: boolean
  /** Minimum hotel rating. Setting it narrows the search to hotels. */
  starRating?: number
  minPrice?: number
  maxPrice?: number
  sort?: string
  page?: number
  size?: number
}

function searchParams(query: SearchQuery): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') {
      continue
    }
    if (Array.isArray(value)) {
      // Repeated keys, which is what Spring binds a List parameter from.
      value.forEach((entry) => params.append(key, String(entry)))
    } else {
      params.set(key, String(value))
    }
  }
  return params.toString()
}

const BASE = '/api/v1'
const REFRESH_STORAGE_KEY = 'stay.refreshToken'

/**
 * Access token in memory, refresh token in localStorage.
 *
 * <p>Keeping the access token out of storage means a stolen storage dump is not a
 * live session. The refresh token has to outlive a reload, so it is XSS-reachable;
 * server-side rotation and replay detection are what limit the damage. An HttpOnly
 * cookie is the next hardening step.
 */
let accessToken: string | null = null
let onSessionLost: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getRefreshToken(): string | null {
  // Guards against server-side rendering, where localStorage does not exist.
  return typeof window === 'undefined' ? null : window.localStorage.getItem(REFRESH_STORAGE_KEY)
}

export function setRefreshToken(token: string | null) {
  if (typeof window === 'undefined') {
    return
  }
  if (token) {
    window.localStorage.setItem(REFRESH_STORAGE_KEY, token)
  } else {
    window.localStorage.removeItem(REFRESH_STORAGE_KEY)
  }
}

export function setSessionLostHandler(handler: (() => void) | null) {
  onSessionLost = handler
}

export class RequestError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, error: ApiError) {
    super(error.message || 'Request failed')
    this.status = status
    this.code = error.code || 'unknown_error'
  }
}

async function send(path: string, init: RequestInit, withAuth: boolean): Promise<Response> {
  const headers = new Headers(init.headers)
  if (init.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (withAuth && accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return fetch(BASE + path, { ...init, headers })
}

// Concurrent refreshes would rotate the token repeatedly and trip the server's
// replay detection, so callers share one in-flight attempt.
let refreshInFlight: Promise<boolean> | null = null

export function refreshSession(): Promise<boolean> {
  if (refreshInFlight) {
    return refreshInFlight
  }
  refreshInFlight = (async () => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      return false
    }
    const response = await send('/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    }, false)
    if (!response.ok) {
      setAccessToken(null)
      setRefreshToken(null)
      return false
    }
    const session = (await response.json()) as AuthSession
    setAccessToken(session.accessToken)
    setRefreshToken(session.refreshToken)
    return true
  })().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; anonymous?: boolean } = {},
): Promise<T> {
  const init: RequestInit = {
    method: options.method ?? 'GET',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  }
  const withAuth = !options.anonymous

  let response = await send(path, init, withAuth)

  if (response.status === 401 && withAuth && getRefreshToken()) {
    if (await refreshSession()) {
      response = await send(path, init, true)
    } else {
      onSessionLost?.()
    }
  }

  if (!response.ok) {
    if (response.status === 401 && withAuth) {
      onSessionLost?.()
    }
    let body: ApiError
    try {
      body = (await response.json()) as ApiError
    } catch {
      body = { code: 'unknown_error', message: response.statusText, timestamp: '' }
    }
    throw new RequestError(response.status, body)
  }

  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

const DEVICE_LABEL = 'Guest app'

export const api = {
  requestOtp: (phone: string, locale = 'mn') =>
    request<OtpChallenge>('/auth/otp/request', {
      method: 'POST', body: { phone, locale }, anonymous: true,
    }),

  verifyOtp: (phone: string, code: string) =>
    request<AuthSession>('/auth/otp/verify', {
      method: 'POST', body: { phone, code, deviceLabel: DEVICE_LABEL }, anonymous: true,
    }),

  logout: (refreshToken: string) =>
    request<void>('/auth/logout', { method: 'POST', body: { refreshToken } }),

  me: () => request<User>('/auth/me'),

  updateProfile: (patch: { fullName?: string; email?: string; locale?: string }) =>
    request<User>('/users/me', { method: 'PATCH', body: patch }),

  listHostApplications: () => request<HostApplication[]>('/users/me/host-applications'),

  applyAsHost: (body: {
    requestedRole: 'HOUSE_OWNER' | 'HOTEL_MANAGER'
    organizationName?: string
    organizationRegistrationNo?: string
    note?: string
  }) => request<HostApplication>('/users/me/host-applications', { method: 'POST', body }),

  withdrawHostApplication: (applicationId: string) =>
    request<HostApplication>(`/users/me/host-applications/${applicationId}/withdraw`, {
      method: 'POST',
    }),

  // --- catalogue (no account needed) ---------------------------------------

  search: (query: SearchQuery) =>
    request<Page<ListingSummary>>(`/listings/search?${searchParams(query)}`,
      { anonymous: true }),

  listing: (propertyId: string) =>
    request<ListingDetail>(`/listings/${propertyId}`, { anonymous: true }),

  availability: (propertyId: string, from: string, to: string) =>
    request<CalendarDay[]>(
      `/listings/${propertyId}/availability?${searchParams({ from, to } as SearchQuery)}`,
      { anonymous: true }),

  /**
   * Prices a stay without creating anything. The same code prices the booking
   * itself, so what is shown here is what gets charged.
   */
  quote: (propertyId: string, body: { checkIn: string; checkOut: string; guests: number }) =>
    request<Quote>(`/listings/${propertyId}/quote`,
      { method: 'POST', body, anonymous: true }),

  // --- bookings and payments (signed in) -----------------------------------

  hotel: (hotelId: string) =>
    request<HotelDetail>(`/hotels/${hotelId}`, { anonymous: true }),

  /** Every room type with whether it can take these dates and what it would cost. */
  hotelAvailability: (hotelId: string, query: {
    checkIn: string; checkOut: string; guests?: number; rooms?: number
  }) => request<RoomTypeAvailability[]>(
    `/hotels/${hotelId}/availability?${searchParams(query as SearchQuery)}`,
    { anonymous: true }),

  hotelQuote: (hotelId: string, roomTypeId: string, body: {
    checkIn: string; checkOut: string; guests: number
  }, rooms = 1) => request<Quote>(
    `/hotels/${hotelId}/room-types/${roomTypeId}/quote?rooms=${rooms}`,
    { method: 'POST', body, anonymous: true }),

  /** Books a house (propertyId) or hotel rooms (roomTypeId with rooms). */
  book: (body: {
    propertyId?: string
    roomTypeId?: string
    checkIn: string
    checkOut: string
    guests: number
    rooms?: number
    message?: string
  }) => request<Booking>('/bookings', { method: 'POST', body }),

  bookings: (scope = 'all') => request<Page<Booking>>(`/bookings?scope=${scope}&size=50`),

  booking: (bookingId: string) => request<Booking>(`/bookings/${bookingId}`),

  /**
   * Reports a listing as not what it claims to be. Signing in is required, so a
   * competitor cannot bury a rival anonymously.
   */
  reportListing: (supplyType: SupplyType, supplyId: string,
                  body: { reason: string; details?: string; bookingId?: string }) =>
    request<unknown>(
      `${supplyType === 'HOTEL' ? '/hotels' : '/listings'}/${supplyId}/report`,
      { method: 'POST', body }),

  cancelBooking: (bookingId: string, reason?: string) =>
    request<Booking>(`/bookings/${bookingId}/cancel`, { method: 'POST', body: { reason } }),

  startPayment: (bookingId: string, provider?: string) =>
    request<Payment>(`/bookings/${bookingId}/payments`, { method: 'POST', body: { provider } }),

  payments: (bookingId: string) => request<Payment[]>(`/bookings/${bookingId}/payments`),

  payment: (bookingId: string, paymentId: string) =>
    request<Payment>(`/bookings/${bookingId}/payments/${paymentId}`),

  // --- reviews -------------------------------------------------------------

  /** Public: what other guests said about this place. */
  listingReviews: (propertyId: string, page = 0) =>
    request<Page<Review>>(`/listings/${propertyId}/reviews?page=${page}&size=10`,
      { anonymous: true }),

  hotelReviews: (hotelId: string, page = 0) =>
    request<Page<Review>>(`/hotels/${hotelId}/reviews?page=${page}&size=10`,
      { anonymous: true }),

  /** Both reviews of one stay. An unpublished one comes back only to its author. */
  bookingReviews: (bookingId: string) => request<Review[]>(`/bookings/${bookingId}/reviews`),

  writeReview: (bookingId: string, body: {
    rating: number
    subRatings?: Record<string, number>
    comment?: string
  }) => request<Review>(`/bookings/${bookingId}/review`, { method: 'POST', body }),

  // --- messages ------------------------------------------------------------

  conversations: () => request<Page<Conversation>>('/conversations?size=50'),

  /** Opens the thread for a booking, creating it the first time. */
  openConversation: (bookingId: string) =>
    request<Conversation>(`/conversations/for-booking/${bookingId}`, { method: 'POST' }),

  messages: (conversationId: string) =>
    request<Page<Message>>(`/conversations/${conversationId}/messages?size=100`),

  sendMessage: (conversationId: string, body: string) =>
    request<Message>(`/conversations/${conversationId}/messages`,
      { method: 'POST', body: { body } }),

  markConversationRead: (conversationId: string) =>
    request<void>(`/conversations/${conversationId}/read`, { method: 'POST' }),

  /**
   * Development only: settles a simulated payment as a provider callback would.
   * The endpoint does not exist unless the simulated gateway is enabled.
   */
  simulateSettlement: (bookingId: string, paymentId: string) =>
    request<Payment>(`/bookings/${bookingId}/payments/${paymentId}/simulate-settlement`,
      { method: 'POST' }),
}

/** Maps a backend error code to guest-facing copy. */
export function describeError(failure: unknown): string {
  if (!(failure instanceof RequestError)) {
    return 'Could not reach the server. Check your connection and try again.'
  }
  switch (failure.code) {
    case 'invalid_phone':
      return 'That does not look like a valid phone number.'
    case 'otp_invalid':
      return 'That code is incorrect.'
    case 'otp_expired':
      return 'That code has expired. Request a new one.'
    case 'otp_cooldown':
      return 'A code was just sent. Wait a moment before asking for another.'
    case 'otp_rate_limited':
      return 'Too many codes requested for this number. Try again in an hour.'
    case 'otp_locked':
      return 'Too many incorrect codes. This number is locked for a short while.'
    case 'account_not_active':
      return 'This account cannot sign in. Contact support.'
    case 'email_taken':
      return 'That email is already in use.'
    case 'application_pending':
      return 'You already have an application under review.'
    case 'role_already_held':
      return 'You already have this role.'
    case 'organization_name_required':
      return 'Enter the registered business name.'
    case 'rooms_unavailable':
      return 'Those rooms have just been taken. Try different dates or another room.'
    case 'too_many_rooms':
      return 'That is more rooms than this hotel has of that type.'
    case 'room_type_not_bookable':
      return 'That room is not available to book right now.'
    case 'stay_not_finished':
      return 'You can review a stay once it is over.'
    case 'review_window_closed':
      return 'Reviews close 14 days after checkout.'
    case 'already_reviewed':
      return 'You have already reviewed this stay.'
    case 'invalid_rating':
      return 'Choose a rating from 1 to 5.'
    case 'conversation_not_found':
      return 'That conversation is not yours.'
    default:
      return failure.message
  }
}
