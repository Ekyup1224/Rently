import type { BookingStatus, PropertyStatus } from '../../types'

/** Shared labels and formatting, so listing state reads the same on every screen. */

export const STATUS_LABELS: Record<PropertyStatus, string> = {
  DRAFT: 'Draft',
  PENDING_REVIEW: 'Under review',
  APPROVED: 'Live',
  REJECTED: 'Not approved',
  PAUSED: 'Paused',
  SUSPENDED: 'Suspended',
}

export const STATUS_COLORS: Record<PropertyStatus, string> = {
  DRAFT: 'default',
  PENDING_REVIEW: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
  PAUSED: 'blue',
  SUSPENDED: 'volcano',
}

export const BOOKING_STATUS_LABELS: Record<BookingStatus, string> = {
  PENDING_HOST_APPROVAL: 'Awaiting your response',
  PENDING_PAYMENT: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  CHECKED_IN: 'Checked in',
  CHECKED_OUT: 'Checked out',
  COMPLETED: 'Completed',
  DECLINED: 'Declined',
  EXPIRED: 'Expired',
  CANCELLED_BY_GUEST: 'Cancelled by guest',
  CANCELLED_BY_HOST: 'Cancelled by you',
}

export const BOOKING_STATUS_COLORS: Record<BookingStatus, string> = {
  PENDING_HOST_APPROVAL: 'orange',
  PENDING_PAYMENT: 'gold',
  CONFIRMED: 'green',
  CHECKED_IN: 'cyan',
  CHECKED_OUT: 'blue',
  COMPLETED: 'default',
  DECLINED: 'red',
  EXPIRED: 'default',
  CANCELLED_BY_GUEST: 'red',
  CANCELLED_BY_HOST: 'volcano',
}

/**
 * Formats an amount for display. MNT has no practical subunit, so whole tögrög
 * are shown; other currencies keep their decimals.
 */
export function formatMoney(amount: number | null | undefined, currency = 'MNT'): string {
  if (amount === null || amount === undefined) {
    return '—'
  }
  const fractionDigits = currency === 'MNT' ? 0 : 2
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(amount) + (currency === 'MNT' ? ' ₮' : ` ${currency}`)
}
