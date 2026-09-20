import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Badge, Button, Card, Col, Empty, Flex, Input, Modal, Rate, Row, Segmented,
  Skeleton, Table, Tag, Typography,
} from 'antd'
import { EyeInvisibleOutlined, UndoOutlined, WarningOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { RequestError } from '../../api/client'
import { moderation } from '../../api/endpoints'
import type { FlaggedMessage, ModeratedReview } from '../../types'
import { StatTile } from '../../ui/StatTile'
import { palette } from '../../theme'
import { PageHead } from '../../ui/PageHead'

const STATUS_COLOURS: Record<ModeratedReview['status'], string> = {
  PENDING: 'gold',
  PUBLISHED: 'green',
  HIDDEN: 'red',
}

/** Why a message was flagged, in words rather than a code. */
const REASONS: Record<string, string> = {
  bank_details_shared: 'Shared bank details',
  off_platform_request: 'Asked to pay off-platform',
  payment_details_requested: 'Asked for payment details',
}

/**
 * Two queues, one screen: what people wrote about each other, and what a host
 * tried to arrange privately.
 *
 * <p>They sit together because they are the same shift's work, and because a
 * flagged message is usually the explanation for the bad review three rows
 * above it.
 */
export function ModerationPage() {
  const { message } = AntApp.useApp()
  const [tab, setTab] = useState<'reviews' | 'messages'>('reviews')

  const [reviews, setReviews] = useState<ModeratedReview[]>([])
  const [reviewTotal, setReviewTotal] = useState(0)
  const [reviewPage, setReviewPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<'ALL' | ModeratedReview['status']>('ALL')

  const [flagged, setFlagged] = useState<FlaggedMessage[]>([])
  const [flaggedTotal, setFlaggedTotal] = useState(0)
  const [flaggedPage, setFlaggedPage] = useState(0)

  const [loading, setLoading] = useState(true)
  const [hiding, setHiding] = useState<ModeratedReview | null>(null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [reviewPageRows, flaggedRows] = await Promise.all([
        moderation.reviews(statusFilter === 'ALL' ? undefined : [statusFilter], reviewPage),
        moderation.flaggedMessages(flaggedPage),
      ])
      setReviews(reviewPageRows.rows)
      setReviewTotal(reviewPageRows.total)
      setFlagged(flaggedRows.rows)
      setFlaggedTotal(flaggedRows.total)
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not load the queues')
    } finally {
      setLoading(false)
    }
  }, [statusFilter, reviewPage, flaggedPage, message])

  useEffect(() => { void load() }, [load])

  async function hide() {
    if (!hiding || !reason.trim()) {
      return
    }
    setBusy(true)
    try {
      await moderation.hideReview(hiding.id, reason.trim())
      message.success('Review hidden')
      setHiding(null)
      setReason('')
      await load()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not hide it')
    } finally {
      setBusy(false)
    }
  }

  async function restore(review: ModeratedReview) {
    try {
      await moderation.restoreReview(review.id)
      message.success('Review restored')
      await load()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not restore it')
    }
  }

  const hidden = reviews.filter((review) => review.status === 'HIDDEN').length
  const waiting = reviews.filter((review) => review.status === 'PENDING').length

  return (
    <>
      <PageHead
        title="Reviews and messages"
        description={'Reviews published on listings, and the messages our off-platform '
          + 'detector was unhappy about. Whole conversations are never shown here — only '
          + 'the flagged message.'}
      />

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} lg={8}>
          <StatTile label="Reviews in view" value={reviewTotal} tone="brand" />
        </Col>
        <Col xs={12} lg={8}>
          <StatTile label="Still blind" value={waiting} tone="accent"
                    hint="waiting on the other side" />
        </Col>
        <Col xs={12} lg={8}>
          <StatTile label="Flagged messages" value={flaggedTotal} tone="rose"
                    icon={<WarningOutlined />} hint="each one froze a payout" />
        </Col>
      </Row>

      <Segmented
        value={tab}
        onChange={(value) => setTab(value as 'reviews' | 'messages')}
        style={{ marginBottom: 16 }}
        options={[
          { label: `Reviews (${reviewTotal})`, value: 'reviews' },
          {
            label: (
              <Flex gap={8} align="center">
                Flagged messages
                {flaggedTotal > 0 && <Badge count={flaggedTotal} color={palette.danger} />}
              </Flex>
            ),
            value: 'messages',
          },
        ]}
      />

      {loading ? <Skeleton active paragraph={{ rows: 6 }} /> : tab === 'reviews' ? (
        <Card
          size="small"
          extra={
            <Segmented
              size="small"
              value={statusFilter}
              onChange={(value) => {
                setStatusFilter(value as typeof statusFilter)
                setReviewPage(0)
              }}
              options={['ALL', 'PENDING', 'PUBLISHED', 'HIDDEN']}
            />
          }
          title={`${hidden} hidden in this page`}
        >
          <Table<ModeratedReview>
            rowKey="id"
            dataSource={reviews}
            size="middle"
            pagination={{
              current: reviewPage + 1,
              pageSize: 25,
              total: reviewTotal,
              onChange: (next) => setReviewPage(next - 1),
              showSizeChanger: false,
            }}
            columns={[
              {
                title: 'Rating',
                dataIndex: 'rating',
                width: 130,
                render: (rating: number) => (
                  <Rate disabled value={rating} style={{ fontSize: 13 }} />
                ),
              },
              {
                title: 'Author',
                dataIndex: 'authorName',
                width: 150,
                render: (name: string, row) => (
                  <Flex vertical gap={2}>
                    <Typography.Text>{name}</Typography.Text>
                    <Tag variant="filled" color={row.subject === 'SUPPLY' ? 'blue' : 'purple'}>
                      {row.subject === 'SUPPLY' ? 'Guest → place' : 'Host → guest'}
                    </Tag>
                  </Flex>
                ),
              },
              {
                title: 'Comment',
                dataIndex: 'comment',
                render: (comment: string | null, row) => (
                  <Flex vertical gap={4}>
                    <Typography.Paragraph style={{ margin: 0 }}
                                          ellipsis={{ rows: 3, expandable: true }}>
                      {comment || <span className="muted">No comment written</span>}
                    </Typography.Paragraph>
                    {row.hiddenReason && (
                      <Typography.Text type="danger" style={{ fontSize: 12 }}>
                        Hidden: {row.hiddenReason}
                      </Typography.Text>
                    )}
                  </Flex>
                ),
              },
              {
                title: 'Booking',
                dataIndex: 'bookingReference',
                width: 130,
                render: (reference: string, row) => (
                  <Flex vertical gap={2}>
                    <Typography.Text code style={{ fontSize: 12 }}>{reference}</Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                      {dayjs(row.createdAt).format('D MMM YYYY')}
                    </Typography.Text>
                  </Flex>
                ),
              },
              {
                title: 'Status',
                dataIndex: 'status',
                width: 110,
                render: (status: ModeratedReview['status']) => (
                  <Tag color={STATUS_COLOURS[status]} variant="filled">
                    {status.toLowerCase()}
                  </Tag>
                ),
              },
              {
                title: '',
                key: 'actions',
                width: 110,
                render: (_, row) => row.status === 'HIDDEN' ? (
                  <Button size="small" icon={<UndoOutlined />} onClick={() => restore(row)}>
                    Restore
                  </Button>
                ) : (
                  <Button size="small" danger icon={<EyeInvisibleOutlined />}
                          onClick={() => setHiding(row)}>
                    Hide
                  </Button>
                ),
              },
            ]}
          />
        </Card>
      ) : (
        <Flex vertical gap={12}>
          <Alert
            type="warning"
            showIcon
            message="Each of these froze the host's payout when it was sent"
            description={'A host asking to be paid directly is trying to get around the hold '
              + 'that protects the guest. The listing already has an open flag; resolve it on '
              + 'the Trust and safety queue.'}
          />
          {flagged.length === 0 ? (
            <Card size="small">
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
                     description="No flagged messages. Good news." />
            </Card>
          ) : (
            <Table<FlaggedMessage>
              rowKey="id"
              dataSource={flagged}
              size="middle"
              pagination={{
                current: flaggedPage + 1,
                pageSize: 25,
                total: flaggedTotal,
                onChange: (next) => setFlaggedPage(next - 1),
                showSizeChanger: false,
              }}
              columns={[
                {
                  title: 'Sender',
                  dataIndex: 'senderName',
                  width: 180,
                  render: (name: string, row) => (
                    <Flex vertical gap={2}>
                      <Typography.Text strong>{name}</Typography.Text>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {row.senderPhone}
                      </Typography.Text>
                    </Flex>
                  ),
                },
                {
                  title: 'Why',
                  dataIndex: 'reason',
                  width: 200,
                  render: (code: string) => (
                    <Tag color="red" variant="filled">{REASONS[code] ?? code}</Tag>
                  ),
                },
                {
                  title: 'Message',
                  dataIndex: 'body',
                  render: (body: string) => (
                    <Typography.Paragraph style={{ margin: 0 }}
                                          ellipsis={{ rows: 3, expandable: true }}>
                      {body}
                    </Typography.Paragraph>
                  ),
                },
                {
                  title: 'Booking',
                  dataIndex: 'bookingReference',
                  width: 130,
                  render: (reference: string, row) => (
                    <Flex vertical gap={2}>
                      <Typography.Text code style={{ fontSize: 12 }}>{reference}</Typography.Text>
                      <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                        {dayjs(row.sentAt).format('D MMM HH:mm')}
                      </Typography.Text>
                    </Flex>
                  ),
                },
              ]}
            />
          )}
        </Flex>
      )}

      <Modal
        open={hiding !== null}
        title="Hide this review"
        okText="Hide it"
        okButtonProps={{ danger: true, disabled: !reason.trim(), loading: busy }}
        onOk={hide}
        onCancel={() => { setHiding(null); setReason('') }}
      >
        <Typography.Paragraph type="secondary" style={{ fontSize: 13 }}>
          The reason is shown to whoever wrote it, so write it to be read by them.
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          placeholder="Why this review is coming down"
        />
      </Modal>
    </>
  )
}
