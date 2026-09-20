import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Alert, App as AntApp, Empty, Flex, Spin, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { RequestError } from '../../api/client'
import { hostPayouts } from '../../api/endpoints'
import type { Payout, PayoutStatus } from '../../types'
import { formatMoney } from './listingFormat'
import { PageHead } from '../../ui/PageHead'

/**
 * What the host is owed and when it arrives.
 *
 * <p>Shown plainly, including the holds. A host who can see "held until the day
 * after your guest arrives" understands the rule; one who just sees nothing
 * assumes they have been cheated and starts asking guests to pay them directly —
 * which is precisely the situation the hold exists to prevent.
 */
const STATUS_COPY: Record<PayoutStatus, { tone: string; label: string; meaning: string }> = {
  PENDING: { tone: 'default', label: 'scheduled', meaning: 'Releases after your guest checks in' },
  BLOCKED: { tone: 'red', label: 'on hold', meaning: 'Something needs checking first' },
  RELEASED: { tone: 'blue', label: 'approved', meaning: 'Cleared for transfer' },
  PAID: { tone: 'green', label: 'paid', meaning: 'Sent to your account' },
  CANCELLED: { tone: 'default', label: 'cancelled', meaning: 'The stay did not happen' },
}

/** The same reasons the admin sees, said to the person they affect. */
const HELD_BECAUSE: Record<string, string> = {
  stay_not_started: 'Waiting for the guest to check in',
  kyc_required: 'Verify your identity to receive payouts',
  listing_flagged: 'A listing of yours is under review',
}

export function PayoutsPage() {
  const { message } = AntApp.useApp()
  // Present for a hotel's payouts, absent for a house owner's own: the money
  // belongs to the organization in one case and to the person in the other.
  const { hotelId } = useParams<{ hotelId: string }>()
  const [rows, setRows] = useState<Payout[] | null>(null)

  useEffect(() => {
    let cancelled = false
    const loading = hotelId
      ? hostPayouts.forHotel(hotelId, { page: 0, size: 50 })
      : hostPayouts.mine({ page: 0, size: 50 })
    loading
      .then((page) => { if (!cancelled) setRows(page.rows) })
      .catch((failure) => {
        if (!cancelled) {
          setRows([])
          message.error(failure instanceof RequestError ? failure.message : 'Could not load payouts')
        }
      })
    return () => { cancelled = true }
  }, [message, hotelId])

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
      title: 'You receive',
      align: 'right',
      render: (_, payout) => <strong>{formatMoney(payout.amount, payout.currency)}</strong>,
    },
    {
      title: 'Status',
      render: (_, payout) => (
        <Flex vertical gap={2}>
          <span><Tag color={STATUS_COPY[payout.status].tone}>
            {STATUS_COPY[payout.status].label}
          </Tag></span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {payout.blockedReason
              ? HELD_BECAUSE[payout.blockedReason] ?? STATUS_COPY[payout.status].meaning
              : STATUS_COPY[payout.status].meaning}
          </Typography.Text>
        </Flex>
      ),
    },
    {
      title: 'Expected',
      render: (_, payout) => (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {payout.paidAt
            ? `Paid ${new Date(payout.paidAt).toLocaleDateString()}`
            : new Date(payout.releaseAfter).toLocaleDateString()}
        </Typography.Text>
      ),
    },
  ]

  if (rows === null) {
    return <Flex justify="center" style={{ padding: 32 }}><Spin /></Flex>
  }

  return (
    <>
      <PageHead
        title="Payouts"
        description={'Where each amount you have earned has got to, and what is holding anything back.'}
      />
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        title="When you get paid"
        description="Guests pay us when they book, and we send it on 24 hours after they check
          in. The wait protects both sides: guests know their money is safe until they arrive,
          and you are covered against a chargeback afterwards. Verify your identity once and
          payouts release on their own."
      />
      <Table
        rowKey="id"
        size="small"
        dataSource={rows}
        columns={columns}
        pagination={false}
        locale={{ emptyText: <Empty description="No payouts yet" /> }}
      />
    </>
  )
}
