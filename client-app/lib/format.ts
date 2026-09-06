/** Display helpers shared across the guest app. */

/**
 * Formats an amount for guests. MNT has no practical subunit, so whole tögrög
 * are shown; other currencies keep their decimals.
 */
export function formatMoney(amount: number | null | undefined, currency = 'MNT'): string {
  if (amount === null || amount === undefined) {
    return '—'
  }
  const fractionDigits = currency === 'MNT' ? 0 : 2
  const formatted = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(amount)
  return currency === 'MNT' ? `${formatted} ₮` : `${formatted} ${currency}`
}

export function formatDateRange(checkIn: string, checkOut: string): string {
  const from = new Date(checkIn + 'T00:00:00')
  const to = new Date(checkOut + 'T00:00:00')
  const sameMonth = from.getMonth() === to.getMonth() && from.getFullYear() === to.getFullYear()
  const day = (date: Date) => date.getDate()
  const month = (date: Date) =>
    date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' })
  return sameMonth
    ? `${day(from)}–${day(to)} ${month(to)}`
    : `${day(from)} ${month(from)} – ${day(to)} ${month(to)}`
}

export function amenityLabel(amenity: string): string {
  return amenity.toLowerCase().replace(/_/g, ' ')
}

/** Today in Mongolia, as YYYY-MM-DD, for date input minimums. */
export function todayInUlaanbaatar(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ulaanbaatar',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

export function addDays(isoDate: string, days: number): string {
  const date = new Date(isoDate + 'T00:00:00Z')
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

export function nightsBetween(checkIn: string, checkOut: string): number {
  const from = new Date(checkIn + 'T00:00:00Z').getTime()
  const to = new Date(checkOut + 'T00:00:00Z').getTime()
  return Math.max(0, Math.round((to - from) / 86_400_000))
}
