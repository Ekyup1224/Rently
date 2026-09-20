import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Descriptions, Empty, Flex, Image, Input, Modal,
  Segmented, Space, Spin, Table, Tag, Typography,
} from 'antd'
import dayjs from 'dayjs'
import { RequestError } from '../../api/client'
import { adminHotels } from '../../api/endpoints'
import type { Hotel, RoomType, SupplyStatus } from '../../types'
import { formatMoney } from '../owner/listingFormat'
import { HOTEL_STATUS_COLORS, HOTEL_STATUS_LABELS } from '../hotel/hotelFormat'
import { PageHead } from '../../ui/PageHead'

const QUEUES: { label: string; value: SupplyStatus | 'ALL' }[] = [
  { label: 'Awaiting review', value: 'PENDING_REVIEW' },
  { label: 'Live', value: 'APPROVED' },
  { label: 'Not approved', value: 'REJECTED' },
  { label: 'Suspended', value: 'SUSPENDED' },
  { label: 'All', value: 'ALL' },
]

/**
 * Hotel approval.
 *
 * <p>Shows the room types alongside the property, because a hotel cannot be
 * approved without something to sell and a reviewer needs to see what that is —
 * capacity and rates are as much part of the decision as the photos.
 */
export function HotelReviewPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const [queue, setQueue] = useState<SupplyStatus | 'ALL'>('PENDING_REVIEW')
  const [list, setList] = useState<Hotel[] | null>(null)
  const [decision, setDecision] = useState<{ hotel: Hotel; status: SupplyStatus } | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const page = await adminHotels.list({
          status: queue === 'ALL' ? undefined : queue, page: 0, size: 50,
        })
        if (!cancelled) {
          setList(page.rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setList([])
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load hotels')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [queue, reloadToken, message])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <PageHead
        title="Hotel review"
        description={'Hotels waiting to go on sale, with their room types.'}
      />

      <Segmented options={QUEUES} value={queue}
                 onChange={(value) => setQueue(value as SupplyStatus | 'ALL')} />

      {list === null && <Flex justify="center" style={{ padding: 48 }}><Spin /></Flex>}
      {list?.length === 0 && <Card><Empty description="Nothing in this queue" /></Card>}

      {list?.map((hotel) => (
        <Card key={hotel.id} size="small">
          <Flex gap={16} wrap>
            <Flex gap={6} wrap style={{ width: 260, flexShrink: 0 }}>
              {hotel.photos.length === 0
                ? <Flex align="center" justify="center"
                        style={{ width: 260, height: 120, background: '#fafafa',
                                 border: '1px dashed #d9d9d9', borderRadius: 8 }}>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      No photos
                    </Typography.Text>
                  </Flex>
                : hotel.photos.slice(0, 4).map((photo) => (
                    <Image key={photo.id} src={photo.url} alt={photo.altText ?? ''}
                           width={125} height={84}
                           style={{ objectFit: 'cover', borderRadius: 6 }} />
                  ))}
            </Flex>

            <Flex vertical flex={1} gap={6} style={{ minWidth: 320 }}>
              <Flex gap={8} align="center" wrap>
                <Typography.Text strong>{hotel.name}</Typography.Text>
                <Tag color={HOTEL_STATUS_COLORS[hotel.status]}>
                  {HOTEL_STATUS_LABELS[hotel.status]}
                </Tag>
                {hotel.starRating && <Tag>{'★'.repeat(hotel.starRating)}</Tag>}
              </Flex>

              <Descriptions column={2} size="small">
                <Descriptions.Item label="Business" span={2}>
                  {hotel.organizationName}
                </Descriptions.Item>
                <Descriptions.Item label="Where" span={2}>
                  {[hotel.addressLine, hotel.district, hotel.city].filter(Boolean).join(', ')}
                  {hotel.latitude != null && (
                    <Typography.Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                      ({hotel.latitude.toFixed(4)}, {hotel.longitude?.toFixed(4)})
                    </Typography.Text>
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="Policy" span={2}>
                  {hotel.cancellationPolicy.toLowerCase()}
                  {hotel.checkInFrom ? ` · check-in from ${hotel.checkInFrom.slice(0, 5)}` : ''}
                </Descriptions.Item>
                <Descriptions.Item label="Submitted" span={2}>
                  {dayjs(hotel.updatedAt).format('D MMM YYYY HH:mm')}
                </Descriptions.Item>
              </Descriptions>

              {hotel.description && (
                <Typography.Paragraph type="secondary" style={{ fontSize: 13, marginBottom: 0 }}
                                      ellipsis={{ rows: 3, expandable: true, symbol: 'more' }}>
                  {hotel.description}
                </Typography.Paragraph>
              )}

              <Table<RoomType>
                size="small"
                rowKey="id"
                pagination={false}
                dataSource={hotel.roomTypes}
                locale={{ emptyText: 'No room types — cannot be approved' }}
                style={{ maxWidth: 560 }}
                columns={[
                  { title: 'Room type', dataIndex: 'name' },
                  { title: 'Sleeps', dataIndex: 'capacity', width: 80 },
                  { title: 'Rooms', dataIndex: 'totalRooms', width: 80 },
                  {
                    title: 'Rate',
                    dataIndex: 'basePrice',
                    width: 120,
                    render: (price: number) => formatMoney(price, hotel.currency),
                  },
                  {
                    title: 'On sale',
                    dataIndex: 'status',
                    width: 90,
                    render: (status: string) => status === 'ACTIVE' ? 'Yes' : 'No',
                  },
                ]}
              />

              {hotel.amenities.length > 0 && (
                <Flex gap={4} wrap>
                  {hotel.amenities.map((amenity) => (
                    <Tag key={amenity} style={{ fontSize: 11 }}>
                      {amenity.toLowerCase().replace(/_/g, ' ')}
                    </Tag>
                  ))}
                </Flex>
              )}
              {hotel.readinessProblems.length > 0 && (
                <Alert type="warning" showIcon
                       message={`Incomplete: ${hotel.readinessProblems.join('; ')}`} />
              )}
              {hotel.rejectionReason && (
                <Alert type="error" showIcon
                       message={`Previously rejected: ${hotel.rejectionReason}`} />
              )}
            </Flex>

            <Flex vertical gap={6} style={{ minWidth: 140 }}>
              {hotel.status === 'PENDING_REVIEW' && (
                <>
                  <Button type="primary" block disabled={hotel.readinessProblems.length > 0}
                          onClick={() => setDecision({ hotel, status: 'APPROVED' })}>
                    Approve
                  </Button>
                  <Button block danger
                          onClick={() => setDecision({ hotel, status: 'REJECTED' })}>
                    Reject
                  </Button>
                </>
              )}
              {hotel.status === 'APPROVED' && (
                <Button block danger
                        onClick={() => setDecision({ hotel, status: 'SUSPENDED' })}>
                  Suspend
                </Button>
              )}
              {(hotel.status === 'SUSPENDED' || hotel.status === 'REJECTED') && (
                <Button block type="primary" disabled={hotel.readinessProblems.length > 0}
                        onClick={() => setDecision({ hotel, status: 'APPROVED' })}>
                  Approve
                </Button>
              )}
            </Flex>
          </Flex>
        </Card>
      ))}

      <HotelDecisionModal decision={decision} onClose={() => setDecision(null)}
                          onDone={() => { setDecision(null); reload() }} />
    </Space>
  )
}

function HotelDecisionModal({
  decision, onClose, onDone,
}: {
  decision: { hotel: Hotel; status: SupplyStatus } | null
  onClose: () => void
  onDone: () => void
}) {
  const { message } = AntApp.useApp()

  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const needsReason = decision?.status === 'REJECTED' || decision?.status === 'SUSPENDED'

  async function confirm() {
    if (!decision) {
      return
    }
    if (needsReason && !reason.trim()) {
      message.warning('A reason is required so the hotel knows what to fix')
      return
    }
    setBusy(true)
    try {
      await adminHotels.setStatus(decision.hotel.id, decision.status, reason.trim() || undefined)
      message.success(`Hotel ${decision.status.toLowerCase()}`)
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
      open={decision !== null}
      title={decision ? `${HOTEL_STATUS_LABELS[decision.status]}: ${decision.hotel.name}` : ''}
      onCancel={onClose}
      onOk={confirm}
      okText="Confirm"
      okButtonProps={{ danger: needsReason, loading: busy }}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        {decision?.status === 'APPROVED'
          ? 'The hotel goes live and its rooms become bookable immediately.'
          : decision?.status === 'REJECTED'
            ? 'The hotel can fix the problems and resubmit. Your reason is shown to them.'
            : 'The hotel is removed from search and the manager cannot reverse it. '
              + 'Existing reservations are not cancelled.'}
      </Typography.Paragraph>
      {needsReason && (
        <Input.TextArea rows={3} maxLength={2000} placeholder="What needs to change?"
                        value={reason} onChange={(event) => setReason(event.target.value)} />
      )}
    </Modal>
  )
}
