import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Col, DatePicker, Descriptions, Empty, Flex, Input, Modal,
  Row, Segmented, Space, Spin, Statistic, Tag, Typography,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import dayjs, { type Dayjs } from 'dayjs'
import { RequestError } from '../../api/client'
import { hotels } from '../../api/endpoints'
import type { Booking, Hotel, OccupancyReport } from '../../types'
import { BOOKING_STATUS_COLORS, BOOKING_STATUS_LABELS, formatMoney } from '../owner/listingFormat'
import { PageHead } from '../../ui/PageHead'

const SCOPES = [
  { label: 'Arriving', value: 'arriving' },
  { label: 'In house', value: 'in-house' },
  { label: 'Upcoming', value: 'upcoming' },
  { label: 'Past', value: 'past' },
  { label: 'Cancelled', value: 'cancelled' },
  { label: 'All', value: 'all' },
]

/**
 * The front desk: who is arriving, who is in house, and the performance report.
 *
 * <p>Check-in and check-out are the two things a desk does all day, so they are
 * one click from the list. Cancelling is deliberately not: it refunds the guest in
 * full and is a manager's decision, which the API enforces regardless of what this
 * screen shows.
 */
export function ReservationsPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const { hotelId } = useParams<{ hotelId: string }>()
  const navigate = useNavigate()

  const [hotel, setHotel] = useState<Hotel | null>(null)
  const [scope, setScope] = useState('arriving')
  const [reservations, setReservations] = useState<Booking[] | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [cancelling, setCancelling] = useState<Booking | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    if (!hotelId) {
      return
    }
    let cancelled = false

    async function load() {
      try {
        const [loadedHotel, page] = await Promise.all([
          hotels.get(hotelId!),
          hotels.reservations({ hotelId: hotelId!, scope, page: 0, size: 50 }),
        ])
        if (!cancelled) {
          setHotel(loadedHotel)
          setReservations(page.rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setReservations([])
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load reservations')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [hotelId, scope, reloadToken, message])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  async function act(booking: Booking, action: () => Promise<unknown>, success: string) {
    setBusyId(booking.id)
    try {
      await action()
      message.success(success)
      reload()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'That did not work')
    } finally {
      setBusyId(null)
    }
  }

  if (!hotel) {
    return <Flex justify="center" style={{ padding: 64 }}><Spin size="large" /></Flex>
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Flex align="center" justify="space-between" wrap gap={12}>
        <Space>
          <Button onClick={() => navigate('/hotel/hotels')}>Back</Button>
          <PageHead
        title="Reservations"
        description={'The front desk: who is arriving, who is in, and who is leaving.'}
      />
        </Space>
        <Button onClick={() => navigate(`/hotel/hotels/${hotel.id}/inventory`)}>
          Rates & inventory
        </Button>
      </Flex>

      <OccupancyCard hotelId={hotel.id} />

      <Segmented options={SCOPES} value={scope}
                 onChange={(value) => setScope(value as string)} />

      {reservations === null && <Flex justify="center" style={{ padding: 48 }}><Spin /></Flex>}
      {reservations?.length === 0 && (
        <Card><Empty description="Nothing in this list" /></Card>
      )}

      {reservations?.map((booking) => {
        const canCheckIn = booking.status === 'CONFIRMED'
        const canCheckOut = booking.status === 'CHECKED_IN'
        const cancellable = ['PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN'].includes(booking.status)

        return (
          <Card key={booking.id} size="small">
            <Flex gap={16} wrap justify="space-between">
              <Flex vertical gap={6} flex={1} style={{ minWidth: 300 }}>
                <Flex gap={8} align="center" wrap>
                  <Typography.Text strong>
                    {booking.listing?.roomTypeName ?? 'Room'}
                    {booking.listing?.rooms && booking.listing.rooms > 1
                      ? ` × ${booking.listing.rooms}`
                      : ''}
                  </Typography.Text>
                  <Tag color={BOOKING_STATUS_COLORS[booking.status]}>
                    {BOOKING_STATUS_LABELS[booking.status]}
                  </Tag>
                  <Typography.Text code style={{ fontSize: 12 }}>
                    {booking.reference}
                  </Typography.Text>
                </Flex>

                <Descriptions column={1} size="small" style={{ maxWidth: 480 }}>
                  <Descriptions.Item label="Stay">
                    {dayjs(booking.checkIn).format('ddd D MMM')} →
                    {' '}{dayjs(booking.checkOut).format('ddd D MMM YYYY')}
                    {' '}({booking.nights} night{booking.nights > 1 ? 's' : ''})
                  </Descriptions.Item>
                  <Descriptions.Item label="Guests">
                    {booking.guestCount}
                    {booking.counterpartyName ? ` · ${booking.counterpartyName}` : ''}
                  </Descriptions.Item>
                  <Descriptions.Item label="Your payout">
                    <Typography.Text strong>
                      {formatMoney(booking.hostPayout, booking.currency)}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                      after {formatMoney(booking.hostCommission, booking.currency)} platform fee
                    </Typography.Text>
                  </Descriptions.Item>
                </Descriptions>

                {booking.guestMessage && (
                  <Alert type="info" message={`"${booking.guestMessage}"`}
                         style={{ maxWidth: 480 }} />
                )}
                {booking.status === 'PENDING_PAYMENT' && booking.expiresAt && (
                  <Typography.Text type="warning" style={{ fontSize: 12 }}>
                    Rooms held until {dayjs(booking.expiresAt).format('D MMM HH:mm')} —
                    released automatically if unpaid.
                  </Typography.Text>
                )}
              </Flex>

              <Flex vertical gap={6} style={{ minWidth: 150 }}>
                {canCheckIn && (
                  <Button type="primary" block loading={busyId === booking.id}
                          onClick={() => act(booking, () => hotels.checkIn(booking.id),
                            'Checked in')}>
                    Check in
                  </Button>
                )}
                {canCheckOut && (
                  <Button type="primary" block loading={busyId === booking.id}
                          onClick={() => act(booking, () => hotels.checkOut(booking.id),
                            'Checked out')}>
                    Check out
                  </Button>
                )}
                {cancellable && (
                  <Button danger block onClick={() => setCancelling(booking)}>Cancel</Button>
                )}
              </Flex>
            </Flex>
          </Card>
        )
      })}

      <CancelReservationModal
        booking={cancelling}
        onClose={() => setCancelling(null)}
        onDone={() => { setCancelling(null); reload() }}
      />
    </Space>
  )
}

/** Occupancy, revenue and ADR for a window the desk can change. */
function OccupancyCard({ hotelId }: { hotelId: string }) {
  const { message } = AntApp.useApp()

  const [range, setRange] = useState<[Dayjs, Dayjs]>([
    dayjs().startOf('month'), dayjs().endOf('month'),
  ])
  const [report, setReport] = useState<OccupancyReport | null>(null)
  const [loading, setLoading] = useState(true)

  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      try {
        const loaded = await hotels.occupancy(hotelId, from, to)
        if (!cancelled) {
          setReport(loaded)
        }
      } catch (failure) {
        if (!cancelled) {
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load the report')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [hotelId, from, to, message])

  return (
    <Card
      size="small"
      title="Performance"
      extra={
        <DatePicker.RangePicker
          value={range}
          allowClear={false}
          onChange={(values) => {
            if (values?.[0] && values?.[1]) {
              setRange([values[0], values[1]])
            }
          }}
        />
      }
    >
      {loading && <Flex justify="center" style={{ padding: 16 }}><Spin /></Flex>}
      {!loading && report && (
        <Row gutter={[16, 16]}>
          <Col xs={12} md={6}>
            {/* Statistic truncates at `precision`, so 1.67 would read as 1.6; round
                first and the figure matches the room-nights underneath it. */}
            <Statistic title="Occupancy" suffix="%" precision={1}
                       value={Math.round(report.occupancyPercent * 10) / 10} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {report.roomNightsSold} of {report.roomNightsAvailable} room-nights
            </Typography.Text>
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="Room revenue"
                       value={formatMoney(report.roomRevenue, report.currency)} />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="Average daily rate"
                       value={formatMoney(report.averageDailyRate, report.currency)} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Per room-night sold
            </Typography.Text>
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="Reservations" value={report.reservations} />
          </Col>
        </Row>
      )}
    </Card>
  )
}

/** Cancelling refunds the guest in full, so it says so before you commit. */
function CancelReservationModal({
  booking, onClose, onDone,
}: { booking: Booking | null; onClose: () => void; onDone: () => void }) {
  const { message } = AntApp.useApp()

  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)

  async function confirm() {
    if (!booking) {
      return
    }
    setBusy(true)
    try {
      await hotels.cancelReservation(booking.id, reason || undefined)
      message.success('Reservation cancelled')
      setReason('')
      onDone()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'That did not work')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open={booking !== null}
      title="Cancel this reservation?"
      onCancel={onClose}
      onOk={confirm}
      okText="Cancel reservation"
      okButtonProps={{ danger: true, loading: busy }}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        The guest is refunded in full regardless of the cancellation policy, because they did
        not choose this. The rooms return to inventory immediately.
      </Typography.Paragraph>
      <Input.TextArea
        rows={3}
        maxLength={1000}
        placeholder="Reason (shown to the guest)"
        value={reason}
        onChange={(event) => setReason(event.target.value)}
      />
    </Modal>
  )
}
