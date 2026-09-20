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

/**
 * @param locale which month names to use. Mongolian writes the year first and
 *               numbers its months, so this is a different sentence rather than
 *               the same one with translated words.
 */
export function formatDateRange(checkIn: string, checkOut: string, locale = 'mn'): string {
  const from = new Date(checkIn + 'T00:00:00')
  const to = new Date(checkOut + 'T00:00:00')
  const sameMonth = from.getMonth() === to.getMonth() && from.getFullYear() === to.getFullYear()

  if (locale === 'mn') {
    const mn = (date: Date) =>
      `${date.getFullYear()} оны ${date.getMonth() + 1} сарын ${date.getDate()}`
    return sameMonth
      ? `${mn(from)} – ${to.getDate()}`
      : `${mn(from)} – ${mn(to)}`
  }

  const day = (date: Date) => date.getDate()
  const month = (date: Date) =>
    date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' })
  return sameMonth
    ? `${day(from)}–${day(to)} ${month(to)}`
    : `${day(from)} ${month(from)} – ${day(to)} ${month(to)}`
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

/**
 * A date in the reader's language.
 *
 * <p>Written out rather than handed to `Intl` with an `mn-MN` locale: browsers
 * do not reliably carry Mongolian month names and quietly fall back to English,
 * which puts "Sep 13, 2026" in the middle of a Mongolian page. Mongolian numbers
 * its months, so spelling it out is short as well as correct.
 */
export function formatDate(iso: string, locale = 'mn'): string {
  const date = new Date(iso)
  if (locale === 'mn') {
    return `${date.getFullYear()} оны ${date.getMonth() + 1} сарын ${date.getDate()}`
  }
  return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function formatDateTime(iso: string, locale = 'mn'): string {
  const date = new Date(iso)
  const time = String(date.getHours()).padStart(2, '0')
    + ':' + String(date.getMinutes()).padStart(2, '0')
  if (locale === 'mn') {
    return `${formatDate(iso, locale)}, ${time}`
  }
  return `${date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })}, ${time}`
}
