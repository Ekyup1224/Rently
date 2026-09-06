import { useCallback, useEffect, useState } from 'react'
import {
  Alert, Button, Card, Descriptions, Empty, Flex, Input, Modal, Segmented, Space, Spin, Tag,
  Typography, message,
} from 'antd'
import dayjs from 'dayjs'
import { RequestError } from '../../api/client'
import { owner } from '../../api/endpoints'
import type { Booking } from '../../types'
import { BOOKING_STATUS_COLORS, BOOKING_STATUS_LABELS, formatMoney } from './listingFormat'

const SCOPES = [
  { label: 'Needs response', value: 'pending' },
  { label: 'Upcoming', value: 'upcoming' },
  { label: 'Past', value: 'past' },
  { label: 'Cancelled', value: 'cancelled' },
  { label: 'All', value: 'all' },
]

/**
 * The host's reservation inbox.
 *
 * <p>Shows the host's payout and the commission withheld — never the guest's total
 * or service fee, which are not the host's economics.
 */
export function BookingsPage() {
  const [scope, setScope] = useState('pending')
  const [bookings, setBookings] = useState<Booking[] | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [decision, setDecision] = useState<{ booking: Booking; kind: 'approve' | 'decline' | 'cancel' } | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const page = await owner.listBookings({ scope, page: 0, size: 50 })
        if (!cancelled) {
          setBookings(page.rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setBookings([])
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load reservations')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [scope, reloadToken])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>Reservations</Typography.Title>

      <Segmented options={SCOPES} value={scope}
                 onChange={(value) => setScope(value as string)} />

      {bookings === null && <Flex justify="center" style={{ padding: 48 }}><Spin /></Flex>}
      {bookings?.length === 0 && (
        <Card><Empty description="Nothing here yet" /></Card>
      )}

      {bookings?.map((booking) => {
        const awaitingResponse = booking.status === 'PENDING_HOST_APPROVAL'
        const cancellable = ['PENDING_HOST_APPROVAL', 'PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN']
          .includes(booking.status)

        return (
          <Card key={booking.id} size="small">
            <Flex gap={16} wrap justify="space-between">
              <Flex vertical gap={6} flex={1} style={{ minWidth: 280 }}>
                <Flex gap={8} align="center" wrap>
                  <Typography.Text strong>{booking.listing?.title ?? 'Listing'}</Typography.Text>
                  <Tag color={BOOKING_STATUS_COLORS[booking.status]}>
                    {BOOKING_STATUS_LABELS[booking.status]}
                  </Tag>
                  <Typography.Text code style={{ fontSize: 12 }}>{booking.reference}</Typography.Text>
                </Flex>

                <Descriptions column={1} size="small" style={{ maxWidth: 460 }}>
                  <Descriptions.Item label="Dates">
                    {dayjs(booking.checkIn).format('D MMM')} → {dayjs(booking.checkOut).format('D MMM YYYY')}
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
                         style={{ maxWidth: 460 }} />
                )}
                {awaitingResponse && booking.expiresAt && (
                  <Typography.Text type="warning" style={{ fontSize: 12 }}>
                    Respond by {dayjs(booking.expiresAt).format('D MMM HH:mm')} or the request expires.
                  </Typography.Text>
                )}
                {booking.refundAmount != null && booking.refundAmount > 0 && (
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    Refunded to guest: {formatMoney(booking.refundAmount, booking.currency)}
                  </Typography.Text>
                )}
              </Flex>

              <Flex vertical gap={6} style={{ minWidth: 150 }}>
                {awaitingResponse && (
                  <>
                    <Button type="primary" block
                            onClick={() => setDecision({ booking, kind: 'approve' })}>
                      Accept
                    </Button>
                    <Button block onClick={() => setDecision({ booking, kind: 'decline' })}>
                      Decline
                    </Button>
                  </>
                )}
                {cancellable && !awaitingResponse && (
                  <Button danger block onClick={() => setDecision({ booking, kind: 'cancel' })}>
                    Cancel
                  </Button>
                )}
              </Flex>
            </Flex>
          </Card>
        )
      })}

      <DecisionModal decision={decision} onClose={() => setDecision(null)}
                     onDone={() => { setDecision(null); reload() }} />
    </Space>
  )
}

/** Confirms a host decision, with the consequence spelled out before they commit. */
function DecisionModal({
  decision, onClose, onDone,
}: {
  decision: { booking: Booking; kind: 'approve' | 'decline' | 'cancel' } | null
  onClose: () => void
  onDone: () => void
}) {
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)

  const copy = {
    approve: {
      title: 'Accept this request?',
      body: 'The guest gets a short window to pay. The dates stay held until then.',
      okText: 'Accept',
      danger: false,
    },
    decline: {
      title: 'Decline this request?',
      body: 'The dates are released immediately and the guest is told.',
      okText: 'Decline',
      danger: true,
    },
    cancel: {
      title: 'Cancel this reservation?',
      body: 'A paid guest is refunded in full regardless of your cancellation policy, '
        + 'because they did not choose this. Repeated host cancellations are visible to '
        + 'the platform team.',
      okText: 'Cancel reservation',
      danger: true,
    },
  }[decision?.kind ?? 'approve']

  async function confirm() {
    if (!decision) {
      return
    }
    setBusy(true)
    try {
      const { booking, kind } = decision
      if (kind === 'approve') {
        await owner.approveBooking(booking.id, note || undefined)
        message.success('Request accepted')
      } else if (kind === 'decline') {
        await owner.declineBooking(booking.id, note || undefined)
        message.success('Request declined')
      } else {
        await owner.cancelBooking(booking.id, note || undefined)
        message.success('Reservation cancelled')
      }
      setNote('')
      onDone()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'That did not work')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open={decision !== null}
      title={copy.title}
      onCancel={onClose}
      onOk={confirm}
      okText={copy.okText}
      okButtonProps={{ danger: copy.danger, loading: busy }}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">{copy.body}</Typography.Paragraph>
      <Input.TextArea
        rows={3}
        maxLength={1000}
        placeholder={decision?.kind === 'approve'
          ? 'Optional note to the guest' : 'Reason (shown to the guest)'}
        value={note}
        onChange={(event) => setNote(event.target.value)}
      />
    </Modal>
  )
}
