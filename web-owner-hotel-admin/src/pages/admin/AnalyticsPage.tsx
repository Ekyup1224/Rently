import { useEffect, useMemo, useState } from 'react'
import { App as AntApp, Col, DatePicker, Flex, Row, Segmented, Skeleton } from 'antd'
import {
  ApartmentOutlined, CalendarOutlined, PercentageOutlined, RiseOutlined,
} from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { RequestError } from '../../api/client'
import { analytics } from '../../api/endpoints'
import type { AnalyticsOverview, AnalyticsPoint } from '../../types'
import { formatMoney } from '../owner/listingFormat'
import { StatTile } from '../../ui/StatTile'
import { BreakdownChart, RateChart, TrendChart, type ChartRow } from '../../ui/charts'
import { palette } from '../../theme'
import { plural } from '../../ui/plural'
import { PageHead } from '../../ui/PageHead'

/** Compact money for axis ticks: 4.9M is readable where 4,880,000 is not. */
function compactMoney(amount: number): string {
  if (Math.abs(amount) >= 1_000_000) {
    return `${(amount / 1_000_000).toFixed(1)}M`
  }
  if (Math.abs(amount) >= 1_000) {
    return `${Math.round(amount / 1_000)}K`
  }
  return String(Math.round(amount))
}

/**
 * How the business is doing.
 *
 * <p>Four headline numbers, because those are the ones a decision hangs on:
 * what guests spent, what the platform kept, how many stays that was, and how
 * much supply is live. The charts below are for reading them — a take rate means
 * nothing without knowing whether refunds ate the month.
 *
 * <p>Every figure is compared against the period immediately before it, of the
 * same length. That comparison is the only reason a number like "4.9M" means
 * anything on its own.
 */
export function AnalyticsPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const [range, setRange] = useState<[Dayjs, Dayjs]>(
    () => [dayjs().subtract(29, 'day'), dayjs()])
  const [interval, setInterval] = useState<'day' | 'week'>('day')
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null)
  const [previous, setPrevious] = useState<AnalyticsOverview | null>(null)
  const [series, setSeries] = useState<AnalyticsPoint[]>([])
  const [loading, setLoading] = useState(true)

  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      // The preceding window of the same length, so "up 12%" is against a
      // comparable stretch rather than against last month whatever its size.
      const span = range[1].diff(range[0], 'day') + 1
      const priorTo = range[0].subtract(1, 'day')
      const priorFrom = priorTo.subtract(span - 1, 'day')

      try {
        const [summary, points, prior] = await Promise.all([
          analytics.overview(from, to),
          analytics.series(from, to, interval),
          analytics.overview(priorFrom.format('YYYY-MM-DD'), priorTo.format('YYYY-MM-DD'))
            // A missing comparison is not worth failing the page for.
            .catch(() => null),
        ])
        if (!cancelled) {
          setOverview(summary)
          setSeries(points)
          setPrevious(prior)
        }
      } catch (failure) {
        if (!cancelled) {
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load figures')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    load()
    return () => { cancelled = true }
  }, [from, to, interval, range, message])

  const currency = overview?.currency ?? 'MNT'

  /** @returns percentage change, or null when there is nothing to compare to */
  function change(now: number | undefined, before: number | undefined): number | null {
    if (now == null || before == null || before === 0) {
      return null
    }
    return ((now - before) / before) * 100
  }

  const trendRows = useMemo<ChartRow[]>(() => series.map((point) => ({
    date: point.date.slice(5),
    gross: point.grossValue,
    commission: point.commission,
  })), [series])

  const bookingRows = useMemo<ChartRow[]>(() => series.map((point) => ({
    date: point.date.slice(5),
    bookings: point.bookings,
    // Commission per booking says more than either number alone: it is the
    // average value of a stay, and it moves when the mix of supply moves.
    perBooking: point.bookings > 0
      ? Math.round(point.grossValue / point.bookings) : 0,
  })), [series])

  const takeRows = useMemo(() => series.map((point) => ({
    date: point.date.slice(5),
    // Null, not zero: nothing was taken because nothing was charged.
    take: point.grossValue > 0
      ? Number(((point.commission / point.grossValue) * 100).toFixed(2)) : null,
  })), [series])

  const supply = overview
    ? [
      { name: 'Houses', value: overview.liveListings, colour: palette.brand },
      { name: 'Hotels', value: overview.liveHotels, colour: palette.teal },
    ]
    : []

  const money = overview
    ? [
      { name: 'Paid to hosts', value: Math.max(0, overview.grossValue - overview.commission),
        colour: palette.brand },
      { name: 'Commission', value: overview.commission, colour: palette.teal },
      { name: 'Refunded', value: overview.refunded, colour: palette.rose },
    ]
    : []

  const outcomes = overview
    ? [
      { name: 'Completed or upcoming',
        value: Math.max(0, overview.bookings - overview.cancelledBookings),
        colour: palette.success },
      { name: 'Cancelled', value: overview.cancelledBookings, colour: palette.rose },
    ]
    : []

  return (
    <>
      <PageHead
        title="Analytics"
        description={'What guests spent, what the platform kept, and how the two moved. '
          + 'Every change is against the period immediately before this one.'}
        extra={[
          <DatePicker.RangePicker
            key="range"
            value={range}
            allowClear={false}
            onChange={(value) => {
              if (value && value[0] && value[1]) {
                setRange([value[0], value[1]])
              }
            }}
          />,
          <Segmented
            key="interval"
            value={interval}
            onChange={(value) => setInterval(value as 'day' | 'week')}
            options={[{ label: 'Daily', value: 'day' }, { label: 'Weekly', value: 'week' }]}
          />,
        ]}
      />

      {loading || !overview ? (
        <Skeleton active paragraph={{ rows: 10 }} />
      ) : (
        <Flex vertical gap={16}>
          <Row gutter={[16, 16]}>
            <Col xs={12} lg={6}>
              <StatTile
                label="Gross value"
                value={formatMoney(overview.grossValue, currency)}
                hint={`${formatMoney(overview.refunded, currency)} refunded`}
                icon={<RiseOutlined />}
                tone="brand"
                delta={change(overview.grossValue, previous?.grossValue)}
              />
            </Col>
            <Col xs={12} lg={6}>
              <StatTile
                label="Commission"
                value={formatMoney(overview.commission, currency)}
                hint={`${(Math.round(overview.takeRatePercent * 10) / 10).toFixed(1)}% take rate`}
                icon={<PercentageOutlined />}
                tone="teal"
                delta={change(overview.commission, previous?.commission)}
              />
            </Col>
            <Col xs={12} lg={6}>
              <StatTile
                label="Bookings"
                value={overview.bookings}
                hint={`${overview.cancelledBookings} cancelled`}
                icon={<CalendarOutlined />}
                tone="accent"
                delta={change(overview.bookings, previous?.bookings)}
              />
            </Col>
            <Col xs={12} lg={6}>
              <StatTile
                label="Live supply"
                value={overview.liveListings + overview.liveHotels}
                hint={`${plural(overview.liveListings, 'house')} · `
                  + plural(overview.liveHotels, 'hotel')}
                icon={<ApartmentOutlined />}
                tone="violet"
                delta={change(
                  overview.liveListings + overview.liveHotels,
                  previous ? previous.liveListings + previous.liveHotels : undefined)}
              />
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            <Col xs={24} lg={16}>
              <TrendChart
                title="Gross value and commission"
                data={trendRows}
                xKey="date"
                series={[
                  { key: 'gross', label: 'Gross value' },
                  { key: 'commission', label: 'Commission' },
                ]}
                format={compactMoney}
                note="Settled charges, less refunds, on the day the money moved."
                height={280}
              />
            </Col>
            <Col xs={24} lg={8}>
              <BreakdownChart
                title="Where the money went"
                slices={money}
                total={compactMoney(overview.grossValue)}
                totalLabel="gross"
                note="Of everything guests paid in this period."
                height={280}
              />
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            <Col xs={24} lg={8}>
              <BreakdownChart
                title="Booking outcomes"
                slices={outcomes}
                total={overview.bookings}
                totalLabel="bookings"
                note={`${(Math.round(overview.cancellationRatePercent * 10) / 10).toFixed(1)}% `
                  + 'were cancelled.'}
              />
            </Col>
            <Col xs={24} lg={8}>
              <BreakdownChart
                title="Live supply"
                slices={supply}
                total={overview.liveListings + overview.liveHotels}
                totalLabel="on sale"
                note="Approved and published right now, not only in this period."
              />
            </Col>
            <Col xs={24} lg={8}>
              <RateChart
                title="Take rate"
                data={takeRows}
                xKey="date"
                valueKey="take"
                label="Take rate"
                note="Commission as a share of gross. Flat is healthy; a dip means refunds."
              />
            </Col>
          </Row>

          <TrendChart
            title="Bookings and average stay value"
            data={bookingRows}
            xKey="date"
            series={[
              { key: 'bookings', label: 'Bookings' },
              { key: 'perBooking', label: 'Average value', axis: 'right',
                format: compactMoney },
            ]}
            format={(value) => String(Math.round(value))}
            note="Counts on the left, money on the right — they do not share a scale."
            height={240}
          />
        </Flex>
      )}
    </>
  )
}
