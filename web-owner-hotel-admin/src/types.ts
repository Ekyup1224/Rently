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
