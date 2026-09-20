import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Descriptions, Empty, Flex, Image, Input, Modal,
  Segmented, Space, Spin, Tag, Typography,
} from 'antd'
import dayjs from 'dayjs'
import { RequestError } from '../../api/client'
import { adminListings } from '../../api/endpoints'
import type { Property, PropertyStatus } from '../../types'
import { STATUS_COLORS, STATUS_LABELS, formatMoney } from '../owner/listingFormat'
import { PageHead } from '../../ui/PageHead'
import { plural } from '../../ui/plural'

const QUEUES: { label: string; value: PropertyStatus | 'ALL' }[] = [
  { label: 'Awaiting review', value: 'PENDING_REVIEW' },
  { label: 'Live', value: 'APPROVED' },
  { label: 'Not approved', value: 'REJECTED' },
  { label: 'Suspended', value: 'SUSPENDED' },
  { label: 'All', value: 'ALL' },
]

/**
 * The listing approval queue.
 *
 * <p>Everything a reviewer needs to make the call is on the card — photos,
 * location, price and rules — because opening five screens per listing is how a
 * queue stops getting worked.
 */
export function ListingReviewPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const [queue, setQueue] = useState<PropertyStatus | 'ALL'>('PENDING_REVIEW')
  const [listings, setListings] = useState<Property[] | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [decision, setDecision] =
    useState<{ listing: Property; status: PropertyStatus } | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const page = await adminListings.list({
          status: queue === 'ALL' ? undefined : queue,
          page: 0,
          size: 50,
        })
        if (!cancelled) {
          setListings(page.rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setListings([])
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load listings')
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
        title="Listing review"
        description={'Houses and apartments waiting to go on sale. Nothing reaches search until it passes through here.'}
      />

      <Segmented options={QUEUES} value={queue}
                 onChange={(value) => setQueue(value as PropertyStatus | 'ALL')} />

      {listings === null && <Flex justify="center" style={{ padding: 48 }}><Spin /></Flex>}
      {listings?.length === 0 && (
        <Card><Empty description="Nothing in this queue" /></Card>
      )}

      {listings?.map((listing) => (
        <Card key={listing.id} size="small">
          <Flex gap={16} wrap>
            <Flex gap={6} wrap style={{ width: 260, flexShrink: 0 }}>
              {listing.photos.length === 0
                ? <Flex align="center" justify="center"
                        style={{ width: 260, height: 120, background: '#fafafa',
                                 border: '1px dashed #d9d9d9', borderRadius: 8 }}>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>No photos</Typography.Text>
                  </Flex>
                : listing.photos.slice(0, 4).map((photo) => (
                    <Image key={photo.id} src={photo.url} alt={photo.altText ?? ''}
                           width={125} height={84}
                           style={{ objectFit: 'cover', borderRadius: 6 }} />
                  ))}
            </Flex>

            <Flex vertical flex={1} gap={6} style={{ minWidth: 300 }}>
              <Flex gap={8} align="center" wrap>
                <Typography.Text strong>{listing.title}</Typography.Text>
                <Tag color={STATUS_COLORS[listing.status]}>{STATUS_LABELS[listing.status]}</Tag>
                {listing.instantBook && <Tag color="green">Instant book</Tag>}
              </Flex>

              <Descriptions column={2} size="small">
                <Descriptions.Item label="Type">
                  {listing.propertyType.toLowerCase()}
                </Descriptions.Item>
                <Descriptions.Item label="Sleeps">{listing.maxGuests}</Descriptions.Item>
                <Descriptions.Item label="Price">
                  {formatMoney(listing.basePrice, listing.currency)}/night
                </Descriptions.Item>
                <Descriptions.Item label="Cleaning">
                  {formatMoney(listing.cleaningFee, listing.currency)}
                </Descriptions.Item>
                <Descriptions.Item label="Where" span={2}>
                  {[listing.addressLine, listing.district, listing.city]
                    .filter(Boolean).join(', ')}
                  {listing.latitude != null && (
                    <Typography.Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                      ({listing.latitude.toFixed(4)}, {listing.longitude?.toFixed(4)})
                    </Typography.Text>
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="Policy" span={2}>
                  {listing.cancellationPolicy.toLowerCase()} · min {plural(listing.minStayNights, 'night')}
                </Descriptions.Item>
                <Descriptions.Item label="Submitted" span={2}>
                  {dayjs(listing.updatedAt).format('D MMM YYYY HH:mm')}
                </Descriptions.Item>
              </Descriptions>

              {listing.description && (
                <Typography.Paragraph type="secondary" style={{ fontSize: 13, marginBottom: 0 }}
                                      ellipsis={{ rows: 3, expandable: true, symbol: 'more' }}>
                  {listing.description}
                </Typography.Paragraph>
              )}
              {listing.amenities.length > 0 && (
                <Flex gap={4} wrap>
                  {listing.amenities.map((amenity) => (
                    <Tag key={amenity} style={{ fontSize: 11 }}>
                      {amenity.toLowerCase().replace(/_/g, ' ')}
                    </Tag>
                  ))}
                </Flex>
              )}
              {listing.readinessProblems.length > 0 && (
                <Alert type="warning" showIcon style={{ marginTop: 4 }}
                       message={`Incomplete: ${listing.readinessProblems.join('; ')}`} />
              )}
              {listing.rejectionReason && (
                <Alert type="error" showIcon message={`Previously rejected: ${listing.rejectionReason}`} />
              )}
            </Flex>

            <Flex vertical gap={6} style={{ minWidth: 140 }}>
              {listing.status === 'PENDING_REVIEW' && (
                <>
                  <Button
                    type="primary" block
                    disabled={listing.readinessProblems.length > 0}
                    onClick={() => setDecision({ listing, status: 'APPROVED' })}
                  >
                    Approve
                  </Button>
                  <Button block danger
                          onClick={() => setDecision({ listing, status: 'REJECTED' })}>
                    Reject
                  </Button>
                </>
              )}
              {listing.status === 'APPROVED' && (
                <Button block danger
                        onClick={() => setDecision({ listing, status: 'SUSPENDED' })}>
                  Suspend
                </Button>
              )}
              {(listing.status === 'SUSPENDED' || listing.status === 'REJECTED') && (
                <Button block type="primary"
                        disabled={listing.readinessProblems.length > 0}
                        onClick={() => setDecision({ listing, status: 'APPROVED' })}>
                  Approve
                </Button>
              )}
            </Flex>
          </Flex>
        </Card>
      ))}

      <ReviewDecisionModal
        decision={decision}
        onClose={() => setDecision(null)}
        onDone={() => { setDecision(null); reload() }}
      />
    </Space>
  )
}

/** Captures the decision and, where the owner needs it, the reason. */
function ReviewDecisionModal({
  decision, onClose, onDone,
}: {
  decision: { listing: Property; status: PropertyStatus } | null
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
      message.warning('A reason is required so the owner knows what to fix')
      return
    }
    setBusy(true)
    try {
      await adminListings.setStatus(decision.listing.id, decision.status,
        reason.trim() || undefined)
      message.success(`Listing ${decision.status.toLowerCase()}`)
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
      title={decision ? `${STATUS_LABELS[decision.status]}: ${decision.listing.title}` : ''}
      onCancel={onClose}
      onOk={confirm}
      okText="Confirm"
      okButtonProps={{ danger: needsReason, loading: busy }}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        {decision?.status === 'APPROVED'
          ? 'The listing goes live and becomes bookable immediately.'
          : decision?.status === 'REJECTED'
            ? 'The owner can fix the problems and resubmit. Your reason is shown to them.'
            : 'The listing is removed from search and the owner cannot reverse it.'}
      </Typography.Paragraph>
      {needsReason && (
        <Input.TextArea
          rows={3}
          maxLength={2000}
          placeholder="What needs to change?"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      )}
    </Modal>
  )
}
