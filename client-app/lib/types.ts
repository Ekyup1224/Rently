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
