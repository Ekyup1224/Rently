import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Checkbox, Flex, InputNumber, Space, Spin, Switch,
  Typography,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import dayjs, { type Dayjs } from 'dayjs'
import { RequestError } from '../../api/client'
import { owner } from '../../api/endpoints'
import type { CalendarDay, DayStatus, Property } from '../../types'
import { formatMoney } from './listingFormat'
import { PageHead } from '../../ui/PageHead'
import { plural } from '../../ui/plural'

const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const WEEKDAY_NAMES = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

const STATUS_STYLE: Record<DayStatus, { background: string; border: string; label: string }> = {
  AVAILABLE: { background: '#fff', border: '#e4e6eb', label: 'Available' },
  BLOCKED: { background: '#fafafa', border: '#d9d9d9', label: 'Blocked' },
  BOOKED: { background: '#e6f4ff', border: '#91caff', label: 'Booked' },
}

/**
 * Availability and per-night pricing.
 *
 * <p>A month calendar rather than a data grid: owners think in weekends and
 * seasons, and select ranges. (The AG Grid date-by-room-type matrix is the right
 * tool for hotel inventory, which arrives in Step 3.)
 *
 * <p>Booked nights are shown but not editable — a sold night cannot be freed by
 * unblocking it, and pretending otherwise would be the worst kind of bug.
 */
export function CalendarPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const { propertyId } = useParams<{ propertyId: string }>()
  const navigate = useNavigate()

  const [listing, setListing] = useState<Property | null>(null)
  const [month, setMonth] = useState<Dayjs>(dayjs().startOf('month'))
  const [days, setDays] = useState<CalendarDay[] | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [busy, setBusy] = useState(false)

  const [rangeStart, setRangeStart] = useState<string | null>(null)
  const [rangeEnd, setRangeEnd] = useState<string | null>(null)
  const [blocked, setBlocked] = useState(false)
  const [price, setPrice] = useState<number | null>(null)
  const [minStay, setMinStay] = useState<number | null>(null)
  const [weekdays, setWeekdays] = useState<string[]>([])

  const from = month.startOf('month').format('YYYY-MM-DD')
  const to = month.endOf('month').format('YYYY-MM-DD')

  useEffect(() => {
    if (!propertyId) {
      return
    }
    let cancelled = false

    async function load() {
      try {
        const [loadedListing, loadedDays] = await Promise.all([
          owner.getProperty(propertyId!),
          owner.calendar(propertyId!, from, to),
        ])
        if (!cancelled) {
          setListing(loadedListing)
          setDays(loadedDays)
        }
      } catch (failure) {
        if (!cancelled) {
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load the calendar')
          setDays([])
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [propertyId, from, to, reloadToken, message])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  const byDate = useMemo(() => {
    const map = new Map<string, CalendarDay>()
    days?.forEach((day) => map.set(day.date, day))
    return map
  }, [days])

  /** The grid always starts on a Monday, so leading blanks pad the first week. */
  const gridDates = useMemo(() => {
    const firstOfMonth = month.startOf('month')
    // dayjs day(): 0 = Sunday. Shift so Monday is 0.
    const leadingBlanks = (firstOfMonth.day() + 6) % 7
    const cells: (string | null)[] = Array.from({ length: leadingBlanks }, () => null)
    for (let index = 0; index < month.daysInMonth(); index++) {
      cells.push(firstOfMonth.add(index, 'day').format('YYYY-MM-DD'))
    }
    return cells
  }, [month])

  const selected = useMemo(() => {
    if (!rangeStart) {
      return new Set<string>()
    }
    const end = rangeEnd ?? rangeStart
    const [low, high] = [rangeStart, end].sort()
    const dates = new Set<string>()
    for (let cursor = dayjs(low); !cursor.isAfter(dayjs(high)); cursor = cursor.add(1, 'day')) {
      dates.add(cursor.format('YYYY-MM-DD'))
    }
    return dates
  }, [rangeStart, rangeEnd])

  function clickDay(date: string) {
    const day = byDate.get(date)
    if (day?.status === 'BOOKED') {
      message.info('This night is booked and cannot be changed')
      return
    }
    if (!rangeStart || rangeEnd) {
      setRangeStart(date)
      setRangeEnd(null)
      // Prefill from the day clicked so an edit starts from what is already there.
      setBlocked(day?.status === 'BLOCKED')
      setPrice(day?.overridden ? day.price : null)
      setMinStay(day?.minStay ?? null)
    } else {
      setRangeEnd(date)
    }
  }

  async function apply() {
    if (!propertyId || !rangeStart) {
      return
    }
    const [low, high] = [rangeStart, rangeEnd ?? rangeStart].sort()
    setBusy(true)
    try {
      const result = await owner.updateCalendar(propertyId, {
        from: low,
        to: high,
        weekdays: weekdays.length > 0 ? weekdays : undefined,
        blocked,
        price: price ?? undefined,
        clearPrice: price === null,
        minStayNights: minStay ?? undefined,
        clearMinStay: minStay === null,
      })
      message.success(`${plural(result.daysUpdated, 'day')} updated`)
      setRangeStart(null)
      setRangeEnd(null)
      reload()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not save')
    } finally {
      setBusy(false)
    }
  }

  async function resetRange() {
    if (!propertyId || !rangeStart) {
      return
    }
    const [low, high] = [rangeStart, rangeEnd ?? rangeStart].sort()
    setBusy(true)
    try {
      const result = await owner.clearCalendar(propertyId, low, high)
      message.success(`${plural(result.daysCleared, 'override')} cleared`)
      setRangeStart(null)
      setRangeEnd(null)
      reload()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not clear')
    } finally {
      setBusy(false)
    }
  }

  if (!listing || days === null) {
    return <Flex justify="center" style={{ padding: 64 }}><Spin size="large" /></Flex>
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%', maxWidth: 1000 }}>
      <Flex align="center" justify="space-between" wrap gap={12}>
        <Space>
          <Button onClick={() => navigate('/properties')}>Back</Button>
          <PageHead
        title="Calendar"
        description={'Which nights are free, and what each one costs.'}
      />
        </Space>
        <Space>
          <Button onClick={() => setMonth(month.subtract(1, 'month'))}>←</Button>
          <Typography.Text strong style={{ minWidth: 140, textAlign: 'center', display: 'inline-block' }}>
            {month.format('MMMM YYYY')}
          </Typography.Text>
          <Button onClick={() => setMonth(month.add(1, 'month'))}>→</Button>
        </Space>
      </Flex>

      {listing.status !== 'APPROVED' && (
        <Alert type="info" showIcon
               message="This listing is not live, so nothing can be booked yet"
               description="You can still set availability and pricing ahead of approval." />
      )}

      <Card size="small">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 6 }}>
          {WEEKDAY_LABELS.map((label) => (
            <Typography.Text key={label} type="secondary"
                             style={{ fontSize: 12, textAlign: 'center' }}>
              {label}
            </Typography.Text>
          ))}

          {gridDates.map((date, index) => {
            if (!date) {
              return <div key={`blank-${index}`} />
            }
            const day = byDate.get(date)
            const style = STATUS_STYLE[day?.status ?? 'AVAILABLE']
            const isSelected = selected.has(date)
            const isPast = dayjs(date).isBefore(dayjs().startOf('day'))

            return (
              <button
                key={date}
                type="button"
                onClick={() => clickDay(date)}
                disabled={day?.status === 'BOOKED'}
                style={{
                  background: isSelected ? '#1668dc' : style.background,
                  color: isSelected ? '#fff' : (isPast ? '#bbb' : 'inherit'),
                  border: `1px solid ${isSelected ? '#1668dc' : style.border}`,
                  borderRadius: 8,
                  padding: '8px 6px',
                  minHeight: 68,
                  textAlign: 'left',
                  cursor: day?.status === 'BOOKED' ? 'not-allowed' : 'pointer',
                  font: 'inherit',
                  opacity: isPast ? 0.6 : 1,
                }}
              >
                <div style={{ fontWeight: 600, fontSize: 13 }}>{dayjs(date).date()}</div>
                <div style={{ fontSize: 11, marginTop: 2 }}>
                  {day?.status === 'BOOKED'
                    ? 'Booked'
                    : day?.status === 'BLOCKED'
                      ? 'Blocked'
                      : formatMoney(day?.price ?? listing.basePrice, listing.currency)}
                </div>
                {day?.overridden && day.status === 'AVAILABLE' && (
                  <div style={{ fontSize: 10, opacity: 0.75 }}>custom</div>
                )}
                {day?.minStay && (
                  <div style={{ fontSize: 10, opacity: 0.75 }}>min {day.minStay}n</div>
                )}
              </button>
            )
          })}
        </div>

        <Flex gap={16} wrap style={{ marginTop: 12 }}>
          {(Object.keys(STATUS_STYLE) as DayStatus[]).map((status) => (
            <Flex key={status} align="center" gap={6}>
              <span style={{ width: 12, height: 12, borderRadius: 3,
                             background: STATUS_STYLE[status].background,
                             border: `1px solid ${STATUS_STYLE[status].border}` }} />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {STATUS_STYLE[status].label}
              </Typography.Text>
            </Flex>
          ))}
        </Flex>
      </Card>

      <Card
        size="small"
        title={rangeStart
          ? `Editing ${rangeStart}${rangeEnd && rangeEnd !== rangeStart ? ` → ${rangeEnd}` : ''}`
          : 'Select days to edit'}
      >
        {!rangeStart ? (
          <Typography.Text type="secondary">
            Click a day to start, then click another to select a range.
          </Typography.Text>
        ) : (
          <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
            <Flex gap={24} wrap align="flex-end">
              <Flex vertical gap={4}>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>Blocked</Typography.Text>
                <Switch checked={blocked} onChange={setBlocked} />
              </Flex>
              <Flex vertical gap={4}>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Price per night (₮)
                </Typography.Text>
                <InputNumber
                  min={0} step={10000} style={{ width: 160 }}
                  placeholder={`Base ${listing.basePrice}`}
                  value={price ?? undefined}
                  onChange={(value) => setPrice(value ?? null)}
                />
              </Flex>
              <Flex vertical gap={4}>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Minimum nights
                </Typography.Text>
                <InputNumber
                  min={1} style={{ width: 130 }}
                  placeholder={`Base ${listing.minStayNights}`}
                  value={minStay ?? undefined}
                  onChange={(value) => setMinStay(value ?? null)}
                />
              </Flex>
            </Flex>

            <div>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Only these weekdays (leave empty for every day) — for weekend pricing
              </Typography.Text>
              <Checkbox.Group
                style={{ marginTop: 6 }}
                value={weekdays}
                onChange={(values) => setWeekdays(values as string[])}
                options={WEEKDAY_NAMES.map((name, index) => ({
                  value: name, label: WEEKDAY_LABELS[index],
                }))}
              />
            </div>

            <Flex gap={8} wrap>
              <Button type="primary" loading={busy} onClick={apply}>Apply to selected days</Button>
              <Button loading={busy} onClick={resetRange}>Reset to defaults</Button>
              <Button type="text" onClick={() => { setRangeStart(null); setRangeEnd(null) }}>
                Cancel
              </Button>
            </Flex>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Leaving price or minimum nights empty clears any custom value on those days.
            </Typography.Text>
          </Space>
        )}
      </Card>
    </Space>
  )
}
