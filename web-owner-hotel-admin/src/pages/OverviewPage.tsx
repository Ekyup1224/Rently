import { useEffect, useState } from 'react'
import {
  Alert, Card, Col, Descriptions, Empty, Flex, Row, Skeleton, Tag, Typography,
} from 'antd'
import {
  ArrowRightOutlined, CalendarOutlined, ClockCircleOutlined, HomeOutlined, RiseOutlined,
  SafetyCertificateOutlined, WalletOutlined,
} from '@ant-design/icons'
import { Link } from 'react-router-dom'
import dayjs from 'dayjs'
import { useAuth } from '../auth/AuthProvider'
import { analytics, hostPayouts, owner } from '../api/endpoints'
import type { AnalyticsOverview, Booking, EarningsSummary, Payout } from '../types'
import { BOOKING_STATUS_COLORS, BOOKING_STATUS_LABELS, formatMoney }
  from './owner/listingFormat'
import { StatTile } from '../ui/StatTile'
import { TrendChart, type ChartRow } from '../ui/charts'
import { palette } from '../theme'
import { plural } from '../ui/plural'
import { PageHead } from '../ui/PageHead'

/**
 * The first screen after signing in, tailored to what the account can do.
 *
 * <p>A dashboard's job is to answer "is anything waiting for me?" before it
 * answers anything else. So each role gets its own few numbers and its own
 * shortcuts, rather than everyone getting the same grid of figures most of them
 * cannot act on.
 */
export function OverviewPage() {
  const { user, roles } = useAuth()
  const isStaff = roles.some((role) => role !== 'CLIENT')
  const isOwner = roles.includes('HOUSE_OWNER')
  const isAdmin = roles.includes('SUPER_ADMIN')
  // Only people the platform pays need an identity check. An administrator is
  // staff but earns nothing here, so the prompt would be nonsense for them.
  const getsPaid = isOwner || roles.includes('HOTEL_MANAGER')

  return (
    <>
      <PageHead
        title={`Welcome back${user?.fullName ? `, ${user.fullName.split(' ')[0]}` : ''}`}
        description="What is waiting for you today."
      />

      {!isStaff && (
        <Alert
          style={{ marginBottom: 16 }}
          type="info"
          showIcon
          message="This account has no partner role yet"
          description={'You are signed in as a guest. Apply to become a house owner or hotel '
            + 'from the guest app, and an administrator will review it.'}
        />
      )}

      {isAdmin && <AdminSummary />}
      {isOwner && <OwnerSummary />}

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card title="Account" size="small">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Phone">{user?.phone}</Descriptions.Item>
              <Descriptions.Item label="Email">{user?.email ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={user?.status === 'ACTIVE' ? 'green' : 'orange'} variant="filled">
                  {user?.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Identity check">
                <Tag variant="filled"
                     color={user?.kycStatus === 'VERIFIED' ? 'green' : 'gold'}>
                  {user?.kycStatus}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Roles">
                <Flex gap={4} wrap>
                  {roles.map((role) => <Tag key={role} variant="filled">{role}</Tag>)}
                </Flex>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        {user?.kycStatus !== 'VERIFIED' && getsPaid && (
          <Col xs={24} lg={12}>
            <Card size="small" title={<Flex gap={8} align="center">
              <SafetyCertificateOutlined style={{ color: palette.warning }} />
              Verify your identity
            </Flex>}>
              <Typography.Paragraph style={{ marginBottom: 8 }}>
                No money leaves the platform to an unverified account. Your earnings still
                accrue — they simply sit held until this is done.
              </Typography.Paragraph>
              <Link to="/account">
                Go to your account <ArrowRightOutlined style={{ fontSize: 11 }} />
              </Link>
            </Card>
          </Col>
        )}
      </Row>
    </>
  )
}

/** A house owner's money and what is arriving. */
function OwnerSummary() {
  const [earnings, setEarnings] = useState<EarningsSummary | null>(null)
  const [upcoming, setUpcoming] = useState<Booking[]>([])
  const [payouts, setPayouts] = useState<Payout[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    async function load() {
      // Backwards for the chart, forwards for "confirmed ahead". The summary
      // scopes both figures to the window it is given, so a range ending today
      // reports zero upcoming stays however many are booked.
      const from = dayjs().subtract(5, 'month').startOf('month').format('YYYY-MM-DD')
      const to = dayjs().add(6, 'month').endOf('month').format('YYYY-MM-DD')
      const [summary, bookings, payoutRows] = await Promise.all([
        owner.earnings(from, to).catch(() => null),
        owner.listBookings({ scope: 'upcoming', page: 0, size: 5 })
          .then((found) => found.rows).catch((): Booking[] => []),
        // The host's own payouts, not the platform ledger: /admin/payouts would
        // 403 here, and a swallowed 403 renders as a confident "0 ₮ held".
        hostPayouts.mine({ page: 0, size: 50 })
          .then((found) => found.rows).catch((): Payout[] => []),
      ])
      if (!cancelled) {
        setEarnings(summary)
        setUpcoming(bookings)
        setPayouts(payoutRows)
        setLoading(false)
      }
    }

    load()
    return () => { cancelled = true }
  }, [])

  if (loading) {
    return <Skeleton active paragraph={{ rows: 6 }} />
  }
  if (!earnings) {
    return null
  }

  const currency = earnings.currency
  const held = payouts.filter((payout) => payout.status === 'BLOCKED')
    .reduce((sum, payout) => sum + payout.amount, 0)
  const waiting = payouts.filter((payout) => payout.status === 'PENDING')
    .reduce((sum, payout) => sum + payout.amount, 0)

  const months: ChartRow[] = earnings.byMonth.map((entry) => ({
    month: dayjs(entry.month + '-01').format('MMM'),
    earned: entry.earned,
    stays: entry.stays,
  }))

  return (
    <Flex vertical gap={16}>
      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <StatTile
            label="Earned"
            value={formatMoney(earnings.earnedFromCompletedStays, currency)}
            hint={`${plural(earnings.completedStays, 'completed stay')}`}
            icon={<RiseOutlined />}
            tone="brand"
          />
        </Col>
        <Col xs={12} lg={6}>
          <StatTile
            label="Confirmed ahead"
            value={formatMoney(earnings.confirmedUpcoming, currency)}
            hint={`${plural(earnings.upcomingStays, 'upcoming stay')}`}
            icon={<CalendarOutlined />}
            tone="teal"
          />
        </Col>
        <Col xs={12} lg={6}>
          <StatTile
            label="Waiting to be released"
            value={formatMoney(waiting, currency)}
            hint="released after check-in"
            icon={<ClockCircleOutlined />}
            tone="accent"
          />
        </Col>
        <Col xs={12} lg={6}>
          <StatTile
            label="Held"
            value={formatMoney(held, currency)}
            hint={held > 0 ? 'something needs resolving' : 'nothing held'}
            icon={<WalletOutlined />}
            tone={held > 0 ? 'rose' : 'violet'}
          />
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <TrendChart
            title="Earnings by month"
            data={months}
            xKey="month"
            series={[{ key: 'earned', label: 'Earned' }]}
            format={(value) => value >= 1_000_000
              ? `${(value / 1_000_000).toFixed(1)}M` : `${Math.round(value / 1000)}K`}
            note="After commission, on stays that have finished."
            height={240}
          />
        </Col>
        <Col xs={24} lg={10}>
          <Card
            size="small"
            title="Arriving next"
            extra={<Link to="/bookings">All reservations</Link>}
            styles={{ body: { minHeight: 240 } }}
          >
            {upcoming.length === 0 ? (
              <Flex align="center" justify="center" style={{ height: 200 }}>
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nothing booked yet" />
              </Flex>
            ) : (
              <Flex vertical gap={10}>
                {upcoming.map((booking) => (
                  <Flex key={booking.id} justify="space-between" align="center" gap={12}>
                    <Flex vertical gap={3} style={{ minWidth: 0 }}>
                      <Flex gap={7} align="center" wrap>
                        <Typography.Text strong style={{ fontSize: 13 }}>
                          {booking.counterpartyName ?? booking.reference}
                        </Typography.Text>
                        {/* A stay that is not paid for yet is not money coming;
                            without the status this row reads as though it were. */}
                        <Tag color={BOOKING_STATUS_COLORS[booking.status]} variant="filled"
                             style={{ fontSize: 11, marginInlineEnd: 0 }}>
                          {BOOKING_STATUS_LABELS[booking.status]}
                        </Tag>
                      </Flex>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        <HomeOutlined /> {booking.checkIn} → {booking.checkOut}
                      </Typography.Text>
                    </Flex>
                    <Typography.Text strong style={{ fontSize: 13, whiteSpace: 'nowrap' }}>
                      {formatMoney(booking.hostPayout ?? 0, booking.currency)}
                    </Typography.Text>
                  </Flex>
                ))}
              </Flex>
            )}
          </Card>
        </Col>
      </Row>
    </Flex>
  )
}

/** The platform's last 30 days, as a way into Analytics. */
function AdminSummary() {
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    const from = dayjs().subtract(29, 'day').format('YYYY-MM-DD')
    const to = dayjs().format('YYYY-MM-DD')

    analytics.overview(from, to)
      .then((summary) => { if (!cancelled) setOverview(summary) })
      .catch(() => undefined)
      .finally(() => { if (!cancelled) setLoading(false) })

    return () => { cancelled = true }
  }, [])

  if (loading) {
    return <Skeleton active paragraph={{ rows: 3 }} />
  }
  if (!overview) {
    return null
  }

  return (
    <Row gutter={[16, 16]}>
      <Col xs={12} lg={6}>
        <StatTile label="Gross value, 30 days"
                  value={formatMoney(overview.grossValue, overview.currency)}
                  icon={<RiseOutlined />} tone="brand" />
      </Col>
      <Col xs={12} lg={6}>
        <StatTile label="Commission"
                  value={formatMoney(overview.commission, overview.currency)}
                  hint={`${(Math.round(overview.takeRatePercent * 10) / 10).toFixed(1)}% take`}
                  tone="teal" />
      </Col>
      <Col xs={12} lg={6}>
        <StatTile label="Bookings" value={overview.bookings}
                  hint={`${overview.cancelledBookings} cancelled`}
                  icon={<CalendarOutlined />} tone="accent" />
      </Col>
      <Col xs={12} lg={6}>
        <StatTile label="Live supply" value={overview.liveListings + overview.liveHotels}
                  hint={`${plural(overview.liveListings, 'house')} · `
                    + plural(overview.liveHotels, 'hotel')}
                  icon={<HomeOutlined />} tone="violet" />
      </Col>
    </Row>
  )
}
