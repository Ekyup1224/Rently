'use client'

import { useEffect, useMemo, useState } from 'react'
import { api } from '@/lib/api'
import { addDays, todayInUlaanbaatar } from '@/lib/format'
import type { CalendarDay } from '@/lib/types'
import { useLanguage } from '@/lib/i18n'

const WEEKDAY_KEYS = [
  'calendar.weekday.mon', 'calendar.weekday.tue', 'calendar.weekday.wed',
  'calendar.weekday.thu', 'calendar.weekday.fri', 'calendar.weekday.sat',
  'calendar.weekday.sun',
] as const

/** Days fetched per request, and topped up again once the guest pages past
 *  the loaded window — the endpoint is real, this just avoids refetching
 *  every time a month flips within a window we already have. */
const WINDOW_DAYS = 120

/**
 * @param locale Mongolian numbers its months rather than naming them, and
 *               `Intl` doesn't carry Mongolian month names reliably (see
 *               `formatDate` in lib/format.ts) — so, same as there, this is
 *               spelled out rather than handed to `toLocaleDateString`.
 */
function monthTitle(year: number, month: number, locale: 'mn' | 'en'): string {
  if (locale === 'mn') {
    return `${year} оны ${month + 1} сар`
  }
  return new Date(year, month, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

/** Monday-first weeks covering the month, padded with the neighbouring
 *  months' days so the grid is always six full rows — its shape shouldn't
 *  jump around as the guest navigates. */
function buildGrid(year: number, month: number): Date[] {
  const first = new Date(year, month, 1)
  const leading = (first.getDay() + 6) % 7 // getDay(): 0 = Sunday; shift to Monday-first.
  const start = new Date(year, month, 1 - leading)
  return Array.from({ length: 42 }, (_, i) => {
    const date = new Date(start)
    date.setDate(start.getDate() + i)
    return date
  })
}

function toIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
    + `-${String(date.getDate()).padStart(2, '0')}`
}

/**
 * A single-month range picker. Given a house listing, it also shades that
 * listing's booked nights and refuses a checkout placed past one, so a stay
 * cannot be selected straight through an occupied night.
 *
 * <p>The endpoint is a UX aid, not the source of truth — if it fails to load,
 * every date just stays selectable and the booking widget's own quote call
 * (the thing that actually prices and validates the stay) still catches a
 * bad range when it's submitted.
 *
 * <p>That same property is what lets {@code listingId} be omitted. The search
 * bars have no single listing to ask about, and hotels have no per-day
 * endpoint at all — their availability is a question about room types over a
 * range, answered once dates exist. Without an id the calendar simply skips
 * the fetch and every future date stays selectable, which is exactly the
 * degraded mode above, reached deliberately rather than by a failed request.
 */
export function AvailabilityCalendar({
  listingId, checkIn, checkOut, onSelect,
}: {
  /** A house listing whose booked nights should be shaded. Omit to pick dates
   *  with no availability data — see the note above. */
  listingId?: string
  checkIn: string
  checkOut: string
  onSelect: (checkIn: string, checkOut: string) => void
}) {
  const { locale, t } = useLanguage()
  const today = todayInUlaanbaatar()
  const initial = checkIn ? new Date(`${checkIn}T00:00:00`) : new Date(`${today}T00:00:00`)

  const [viewYear, setViewYear] = useState(initial.getFullYear())
  const [viewMonth, setViewMonth] = useState(initial.getMonth())
  const [days, setDays] = useState<Map<string, CalendarDay>>(new Map())
  const [loadedThrough, setLoadedThrough] = useState<string | null>(null)
  const [hoverDate, setHoverDate] = useState<string | null>(null)

  useEffect(() => {
    if (!listingId) {
      return
    }
    const viewEnd = toIso(new Date(viewYear, viewMonth + 1, 0))
    if (loadedThrough && loadedThrough >= viewEnd) {
      return
    }
    let cancelled = false
    const from = loadedThrough ? addDays(loadedThrough, 1) : today
    const to = addDays(from, WINDOW_DAYS)
    api.availability(listingId, from, to)
      .then((rows) => {
        if (cancelled) return
        setDays((prev) => {
          const next = new Map(prev)
          for (const row of rows) {
            next.set(row.date, row)
          }
          return next
        })
        setLoadedThrough(to)
      })
      .catch(() => {
        // Fail quiet — see the component doc comment.
      })
    return () => {
      cancelled = true
    }
  }, [listingId, viewYear, viewMonth, loadedThrough, today])

  const grid = useMemo(() => buildGrid(viewYear, viewMonth), [viewYear, viewMonth])
  const isBlocked = (iso: string) => days.get(iso)?.status === 'BLOCKED'

  /** A stay can't be booked through a blocked night, so a candidate checkout
   *  past one has to be rejected — the nights in between are what get booked,
   *  the checkout date itself is just the departure morning. */
  function crossesBlocked(fromIso: string, throughIso: string): boolean {
    for (let cursor = fromIso; cursor < throughIso; cursor = addDays(cursor, 1)) {
      if (isBlocked(cursor)) {
        return true
      }
    }
    return false
  }

  function handlePick(iso: string) {
    if (iso < today || isBlocked(iso)) {
      return
    }
    const pickingCheckOut = checkIn && !checkOut && iso > checkIn
    if (!pickingCheckOut) {
      // Nothing picked yet, a full range already picked, or clicking back
      // before the current check-in: any of these starts a fresh selection.
      onSelect(iso, '')
      return
    }
    if (crossesBlocked(checkIn, iso)) {
      return
    }
    onSelect(checkIn, iso)
  }

  const atEarliestMonth = viewYear === Number(today.slice(0, 4))
    && viewMonth === Number(today.slice(5, 7)) - 1

  return (
    <div className="avail-cal">
      <div className="avail-cal__head">
        <button
          type="button"
          className="avail-cal__nav"
          aria-label={t('calendar.prevMonth')}
          disabled={atEarliestMonth}
          onClick={() => {
            const prev = new Date(viewYear, viewMonth - 1, 1)
            setViewYear(prev.getFullYear())
            setViewMonth(prev.getMonth())
          }}
        >
          ‹
        </button>
        <strong className="avail-cal__title">{monthTitle(viewYear, viewMonth, locale)}</strong>
        <button
          type="button"
          className="avail-cal__nav"
          aria-label={t('calendar.nextMonth')}
          onClick={() => {
            const next = new Date(viewYear, viewMonth + 1, 1)
            setViewYear(next.getFullYear())
            setViewMonth(next.getMonth())
          }}
        >
          ›
        </button>
      </div>

      <div className="avail-cal__weekdays">
        {WEEKDAY_KEYS.map((key) => <span key={key}>{t(key)}</span>)}
      </div>

      <div className="avail-cal__grid" onMouseLeave={() => setHoverDate(null)}>
        {grid.map((date) => {
          const iso = toIso(date)
          const inMonth = date.getMonth() === viewMonth
          const disabled = iso < today || isBlocked(iso)
          const isToday = iso === today

          let state: 'start' | 'end' | 'mid' | 'preview' | null = null
          if (checkIn && iso === checkIn) {
            state = 'start'
          } else if (checkOut && iso === checkOut) {
            state = 'end'
          } else if (checkIn && checkOut && iso > checkIn && iso < checkOut) {
            state = 'mid'
          } else if (checkIn && !checkOut && hoverDate && iso > checkIn && iso <= hoverDate) {
            state = 'preview'
          }

          return (
            <button
              key={iso}
              type="button"
              className={[
                'avail-cal__day',
                !inMonth && 'avail-cal__day--outside',
                disabled && 'avail-cal__day--disabled',
                isToday && 'avail-cal__day--today',
                state && `avail-cal__day--${state}`,
              ].filter(Boolean).join(' ')}
              disabled={disabled}
              tabIndex={inMonth ? 0 : -1}
              aria-pressed={state === 'start' || state === 'end'}
              aria-label={isBlocked(iso) ? `${iso} — ${t('calendar.unavailable')}` : iso}
              onClick={() => handlePick(iso)}
              onMouseEnter={() => setHoverDate(iso)}
            >
              {date.getDate()}
            </button>
          )
        })}
      </div>
    </div>
  )
}
