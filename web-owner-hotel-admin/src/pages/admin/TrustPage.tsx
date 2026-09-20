import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Badge, Button, Card, Col, Descriptions, Empty, Flex, Input, Modal, Row,
  Space, Spin, Table, Tabs, Tag, Typography,
} from 'antd'
import { FlagOutlined, IdcardOutlined, LockOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { RequestError } from '../../api/client'
import { trust } from '../../api/endpoints'
import type { KycSubmission, ListingFlag, Payout, PayoutStatus } from '../../types'
import { formatMoney } from '../owner/listingFormat'
import { PageHead } from '../../ui/PageHead'
import { StatTile } from '../../ui/StatTile'
import { BreakdownChart } from '../../ui/charts'
import { palette } from '../../theme'
import { plural } from '../../ui/plural'

/** Why a payout is held, in words a reviewer can act on. */
const BLOCKED_REASONS: Record<string, string> = {
  stay_not_started: 'Nobody checked in',
  kyc_required: 'Host is not verified',
  listing_flagged: 'Listing has an open flag',
}

const PAYOUT_TONE: Record<PayoutStatus, string> = {
  PENDING: 'default',
  BLOCKED: 'red',
  RELEASED: 'blue',
  PAID: 'green',
  CANCELLED: 'default',
}

/**
 * The trust and safety desk.
 *
 * <p>Three queues, one screen, because they are one decision: should this money
 * go out? A flag freezes a listing's payouts and an unverified host cannot be
 * paid, so a reviewer clearing either is really deciding the same thing — and
 * seeing them apart would hide that.
 */
export function TrustPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const [tab, setTab] = useState('payouts')
  const [reloadToken, setReloadToken] = useState(0)
  const reload = () => setReloadToken((token) => token + 1)

  // A summary band above the tabs, so the answer to "which queue needs me" does
  // not require opening all three.
  const [summary, setSummary] = useState<{
    payouts: Payout[]
    flags: number
    kyc: number
  } | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([
      trust.payouts({ page: 0, size: 200 }).then((page) => page.rows).catch((): Payout[] => []),
      trust.flags({ status: ['OPEN'], page: 0, size: 1 }).then((page) => page.total)
        .catch(() => 0),
      trust.kyc({ status: ['PENDING'], page: 0, size: 1 }).then((page) => page.total)
        .catch(() => 0),
    ]).then(([payouts, flags, kyc]) => {
      if (!cancelled) {
        setSummary({ payouts, flags, kyc })
      }
    })
    return () => { cancelled = true }
  }, [reloadToken])

  const byStatus = (status: PayoutStatus) =>
    (summary?.payouts ?? []).filter((row) => row.status === status)
  const sum = (rows: Payout[]) => rows.reduce((total, row) => total + row.amount, 0)
  const held = byStatus('BLOCKED')
  const waiting = byStatus('PENDING')
  const released = byStatus('RELEASED')
  const currency = summary?.payouts[0]?.currency ?? 'MNT'

  return (
    <>
      <PageHead
        title="Trust and safety"
        description={'Held money, flagged listings and identity checks — the three things that decide whether a host gets paid.'}
      />
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        title="Money waits until the guest has arrived"
        description="A host is paid 24 hours after check-in, and only when the stay actually
          started, the account is verified, and nothing is flagged against the listing. A
          listing that does not exist never passes the first of those."
      />
      <Row gutter={[16, 16]} style={{ marginTop: 16, marginBottom: 16 }}>
        <Col xs={24} lg={16}>
          <Row gutter={[16, 16]}>
            <Col xs={12} lg={8}>
              <StatTile
                label="Held"
                value={formatMoney(sum(held), currency)}
                hint={`${plural(held.length, 'payout')} need a decision`}
                icon={<LockOutlined />}
                tone={held.length > 0 ? 'rose' : 'teal'}
              />
            </Col>
            <Col xs={12} lg={8}>
              <StatTile
                label="Open flags"
                value={summary?.flags ?? 0}
                hint={summary?.flags ? 'each one freezes a listing' : 'nothing reported'}
                icon={<FlagOutlined />}
                tone={summary?.flags ? 'accent' : 'teal'}
              />
            </Col>
            <Col xs={12} lg={8}>
              <StatTile
                label="Identity checks"
                value={summary?.kyc ?? 0}
                hint={summary?.kyc ? 'hosts waiting to be paid' : 'queue is clear'}
                icon={<IdcardOutlined />}
                tone={summary?.kyc ? 'violet' : 'teal'}
              />
            </Col>
          </Row>
        </Col>
        <Col xs={24} lg={8}>
          <BreakdownChart
            title="Money by stage"
            height={200}
            note="Everything owed to hosts right now."
            slices={[
              { name: 'Waiting for the hold', value: sum(waiting), colour: palette.brand },
              { name: 'Held', value: sum(held), colour: palette.rose },
              { name: 'Ready to send', value: sum(released), colour: palette.teal },
            ]}
          />
        </Col>
      </Row>

      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: 'payouts',
            label: <Badge count={held.length} size="small" offset={[10, -2]}>Payouts</Badge>,
            children: <PayoutQueue reloadToken={reloadToken} onChanged={reload} message={message} />,
          },
          {
            key: 'flags',
            label: (
              <Badge count={summary?.flags ?? 0} size="small" offset={[10, -2]}>
                Flagged listings
              </Badge>
            ),
            children: <FlagQueue reloadToken={reloadToken} onChanged={reload} message={message} />,
          },
          {
            key: 'kyc',
            label: (
              <Badge count={summary?.kyc ?? 0} size="small" offset={[10, -2]}>
                Identity checks
              </Badge>
            ),
            children: <KycQueue reloadToken={reloadToken} onChanged={reload} message={message} />,
          },
        ]}
      />
    </>
  )
}

type Messenger = ReturnType<typeof AntApp.useApp>['message']

function PayoutQueue({ reloadToken, onChanged, message }: {
  reloadToken: number
  onChanged: () => void
  message: Messenger
}) {
  const [rows, setRows] = useState<Payout[] | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [paying, setPaying] = useState<Payout | null>(null)
  const [reference, setReference] = useState('')

  useEffect(() => {
    let cancelled = false
    trust.payouts({ page: 0, size: 50 })
      .then((page) => { if (!cancelled) setRows(page.rows) })
      .catch((failure) => {
        if (!cancelled) {
          setRows([])
          message.error(failure instanceof RequestError ? failure.message : 'Could not load payouts')
        }
      })
    return () => { cancelled = true }
  }, [reloadToken, message])

  async function act(payout: Payout, run: () => Promise<unknown>, done: string) {
    setBusyId(payout.id)
    try {
      await run()
      message.success(done)
      onChanged()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not update payout')
    } finally {
      setBusyId(null)
    }
  }

  const columns: ColumnsType<Payout> = [
    {
      title: 'Stay',
      render: (_, payout) => (
        <>
          <div><strong>{payout.listingTitle}</strong></div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {payout.bookingReference} · {payout.checkIn} → {payout.checkOut}
          </Typography.Text>
        </>
      ),
    },
    {
      title: 'Amount',
      align: 'right',
      render: (_, payout) => formatMoney(payout.amount, payout.currency),
    },
    {
      title: 'Status',
      render: (_, payout) => (
        <Space orientation="vertical" size={2}>
          <Tag color={PAYOUT_TONE[payout.status]}>{payout.status.toLowerCase()}</Tag>
          {payout.blockedReason && (
            <Typography.Text type="danger" style={{ fontSize: 12 }}>
              {BLOCKED_REASONS[payout.blockedReason] ?? payout.blockedReason}
            </Typography.Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Releases',
      render: (_, payout) => (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {new Date(payout.releaseAfter).toLocaleString()}
        </Typography.Text>
      ),
    },
    {
      title: '',
      align: 'right',
      render: (_, payout) => (
        <Space>
          {(payout.status === 'BLOCKED' || payout.status === 'PENDING') && (
            <Button
              size="small"
              loading={busyId === payout.id}
              onClick={() => act(payout, () => trust.releasePayout(payout.id, 'Released by review'),
                'Released')}
            >
              Release
            </Button>
          )}
          {payout.status === 'RELEASED' && (
            <Button size="small" type="primary" onClick={() => { setPaying(payout); setReference('') }}>
              Mark paid
            </Button>
          )}
          {payout.status !== 'PAID' && payout.status !== 'CANCELLED'
            && payout.status !== 'BLOCKED' && (
            <Button
              size="small"
              danger
              loading={busyId === payout.id}
              onClick={() => act(payout, () => trust.holdPayout(payout.id, 'manual_review'), 'Held')}
            >
              Hold
            </Button>
          )}
        </Space>
      ),
    },
  ]

  if (rows === null) {
    return <Flex justify="center" style={{ padding: 24 }}><Spin /></Flex>
  }

  return (
    <>
      <Table
        rowKey="id"
        size="small"
        dataSource={rows}
        columns={columns}
        pagination={false}
        locale={{ emptyText: <Empty description="Nothing owed yet" /> }}
      />
      <Modal
        open={paying !== null}
        title="Record the transfer"
        okText="Mark paid"
        okButtonProps={{ disabled: !reference.trim() }}
        onCancel={() => setPaying(null)}
        onOk={async () => {
          if (!paying) {
            return
          }
          await act(paying, () => trust.markPaid(paying.id, reference), 'Recorded as paid')
          setPaying(null)
        }}
      >
        <Typography.Paragraph type="secondary">
          The money moves in the bank, not here. This records the reference so the
          payment can be traced afterwards.
        </Typography.Paragraph>
        <Input
          placeholder="Bank reference"
          value={reference}
          onChange={(event) => setReference(event.target.value)}
        />
      </Modal>
    </>
  )
}

function FlagQueue({ reloadToken, onChanged, message }: {
  reloadToken: number
  onChanged: () => void
  message: Messenger
}) {
  const [rows, setRows] = useState<ListingFlag[] | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    trust.flags({ status: ['OPEN'], page: 0, size: 50 })
      .then((page) => { if (!cancelled) setRows(page.rows) })
      .catch((failure) => {
        if (!cancelled) {
          setRows([])
          message.error(failure instanceof RequestError ? failure.message : 'Could not load flags')
        }
      })
    return () => { cancelled = true }
  }, [reloadToken, message])

  async function resolve(flag: ListingFlag, uphold: boolean) {
    setBusyId(flag.id)
    try {
      await (uphold
        ? trust.upholdFlag(flag.id, 'Confirmed on review')
        : trust.dismissFlag(flag.id, 'No problem found'))
      message.success(uphold ? 'Flag upheld — the listing stays frozen' : 'Flag dismissed')
      onChanged()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not resolve flag')
    } finally {
      setBusyId(null)
    }
  }

  if (rows === null) {
    return <Flex justify="center" style={{ padding: 24 }}><Spin /></Flex>
  }
  if (rows.length === 0) {
    return <Empty description="Nothing flagged" />
  }

  return (
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      {rows.map((flag) => (
        <Card key={flag.id} size="small">
          <Flex justify="space-between" align="flex-start" gap={12}>
            <div style={{ minWidth: 0 }}>
              <Space>
                <Tag color={flag.type === 'DUPLICATE_PHOTO' ? 'orange' : 'red'}>
                  {flag.type === 'DUPLICATE_PHOTO' ? 'duplicate photo' : 'guest report'}
                </Tag>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {flag.supplyKind.toLowerCase()} · {new Date(flag.createdAt).toLocaleString()}
                </Typography.Text>
              </Space>
              <Typography.Paragraph style={{ margin: '6px 0' }}>{flag.summary}</Typography.Paragraph>
              <FlagEvidence flag={flag} />
            </div>
            <Space orientation="vertical">
              <Button size="small" danger loading={busyId === flag.id}
                      onClick={() => resolve(flag, true)}>
                Uphold
              </Button>
              <Button size="small" loading={busyId === flag.id}
                      onClick={() => resolve(flag, false)}>
                Dismiss
              </Button>
            </Space>
          </Flex>
        </Card>
      ))}
    </Space>
  )
}

/** The reason a reviewer can act: which listing it matched, or what the guest said. */
function FlagEvidence({ flag }: { flag: ListingFlag }) {
  const details = flag.details ?? {}
  const matches = Array.isArray(details.matches)
    ? (details.matches as Array<Record<string, unknown>>)
    : []

  if (matches.length > 0) {
    return (
      <Descriptions size="small" column={1} styles={{ label: { width: 130 } }}>
        {matches.map((match, index) => (
          <Descriptions.Item key={index} label={`Also on ${String(match.supplyKind).toLowerCase()}`}>
            <Typography.Text code copyable style={{ fontSize: 12 }}>
              {String(match.supplyId)}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12, marginInlineStart: 8 }}>
              {/* 0 is identical; anything under 10 is the same picture. */}
              {String(match.distance)} bits different
            </Typography.Text>
          </Descriptions.Item>
        ))}
      </Descriptions>
    )
  }

  if (typeof details.details === 'string') {
    return <Alert type="warning" message={`"${details.details}"`} style={{ maxWidth: 560 }} />
  }
  return null
}

function KycQueue({ reloadToken, onChanged, message }: {
  reloadToken: number
  onChanged: () => void
  message: Messenger
}) {
  const [rows, setRows] = useState<KycSubmission[] | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(() => {
    trust.kyc({ status: ['PENDING'], page: 0, size: 50 })
      .then((page) => setRows(page.rows))
      .catch((failure) => {
        setRows([])
        message.error(failure instanceof RequestError ? failure.message : 'Could not load documents')
      })
  }, [message])

  useEffect(() => { load() }, [load, reloadToken])

  async function review(submission: KycSubmission, outcome: 'VERIFIED' | 'REJECTED') {
    setBusyId(submission.id)
    try {
      await trust.reviewKyc(submission.id, outcome,
        outcome === 'REJECTED' ? 'Documents did not match the account' : undefined)
      message.success(outcome === 'VERIFIED'
        ? 'Verified — their held payouts can now release'
        : 'Rejected')
      onChanged()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not review')
    } finally {
      setBusyId(null)
    }
  }

  if (rows === null) {
    return <Flex justify="center" style={{ padding: 24 }}><Spin /></Flex>
  }
  if (rows.length === 0) {
    return <Empty description="No documents waiting" />
  }

  return (
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      {rows.map((submission) => (
        <Card key={submission.id} size="small">
          <Flex justify="space-between" align="flex-start" gap={12}>
            <Descriptions size="small" column={1} styles={{ label: { width: 140 } }}>
              <Descriptions.Item label="Name on document">{submission.fullName}</Descriptions.Item>
              <Descriptions.Item label="Account">
                {submission.userName ?? '—'} · {submission.userPhone}
              </Descriptions.Item>
              <Descriptions.Item label="Document">
                {submission.documentType.replace('_', ' ').toLowerCase()}
                {submission.documentNumber ? ` · ${submission.documentNumber}` : ''}
              </Descriptions.Item>
              <Descriptions.Item label="Submitted">
                {new Date(submission.createdAt).toLocaleString()}
              </Descriptions.Item>
            </Descriptions>
            <Space orientation="vertical">
              <Button size="small" type="primary" loading={busyId === submission.id}
                      onClick={() => review(submission, 'VERIFIED')}>
                Verify
              </Button>
              <Button size="small" danger loading={busyId === submission.id}
                      onClick={() => review(submission, 'REJECTED')}>
                Reject
              </Button>
            </Space>
          </Flex>
        </Card>
      ))}
    </Space>
  )
}
