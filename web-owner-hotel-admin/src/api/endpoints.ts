import { request } from './client'
import type {
  AuditLogRow, AuthSession, KycStatus, OtpChallenge, Page, Role, User, UserStatus,
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
