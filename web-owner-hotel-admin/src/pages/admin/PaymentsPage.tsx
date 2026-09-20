import { useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Button, Col, Descriptions, Drawer, Empty, Flex, Input, InputNumber,
  Modal, Row, Segmented, Space, Spin, Table, Tag, Typography,
} from 'antd'
import { CreditCardOutlined, RollbackOutlined, WarningOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { RequestError } from '../../api/client'
import { adminPayments } from '../../api/endpoints'
import type { AdminPayment, PaymentDetail } from '../../types'
import { formatMoney } from '../owner/listingFormat'
import { PageHead } from '../../ui/PageHead'
import { StatTile } from '../../ui/StatTile'
import { plural } from '../../ui/plural'

const STATUS_TONE: Record<string, string> = {
  SUCCEEDED: 'green',
  PENDING: 'blue',
  CREATED: 'default',
  FAILED: 'red',
  CANCELLED: 'default',
  EXPIRED: 'default',
}

/**
 * Every transaction the platform has handled.
 *
 * <p>Exists so that answering "where is this guest's money" never means opening
 * a database console — which is how payment rows get changed by accident. The
 * only write here is a refund, and it goes through the same path a cancellation
 * uses, producing a real reversal rather than an edited number.
 */
export function PaymentsPage() {
  const { message } = AntApp.useApp()

  const [rows, setRows] = useState<AdminPayment[] | null>(null)
  const [total, setTotal] = useState(0)
  const [intent, setIntent] = useState<'all' | 'CHARGE' | 'REFUND'>('all')
  const [status, setStatus] = useState<string | undefined>()
  const [query, setQuery] = useState('')
  const [reloadToken, setReloadToken] = useState(0)

  const [detail, setDetail] = useState<PaymentDetail | null>(null)
  const [refunding, setRefunding] = useState<AdminPayment | null>(null)
  const [amount, setAmount] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    adminPayments.search({
      intent: intent === 'all' ? undefined : intent,
      status,
      query: query.trim() || undefined,
      page: 0,
      size: 50,
    })
      .then((page) => {
        if (!cancelled) {
          setRows(page.rows)
          setTotal(page.total)
        }
      })
      .catch((failure) => {
        if (!cancelled) {
          setRows([])
          message.error(failure instanceof RequestError ? failure.message : 'Could not load payments')
        }
      })
    return () => { cancelled = true }
  }, [intent, status, query, reloadToken, message])

  const columns: ColumnsType<AdminPayment> = [
    {
      title: 'Booking',
      render: (_, payment) => (
        <>
          <div><strong>{payment.bookingReference}</strong></div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {new Date(payment.createdAt).toLocaleString()}
          </Typography.Text>
        </>
      ),
    },
    {
      title: 'Amount',
      align: 'right',
      render: (_, payment) => (
        <span style={{ color: payment.intent === 'REFUND' ? '#cf1322' : undefined }}>
          {payment.intent === 'REFUND' ? '−' : ''}{formatMoney(payment.amount, payment.currency)}
        </span>
      ),
    },
    {
      title: 'Type',
      render: (_, payment) => (
        <Tag color={payment.intent === 'REFUND' ? 'orange' : 'default'}>
          {payment.intent.toLowerCase()}
        </Tag>
      ),
    },
    {
      title: 'Status',
      render: (_, payment) => (
        <Space orientation="vertical" size={2}>
          <Tag color={STATUS_TONE[payment.status] ?? 'default'}>
            {payment.status.toLowerCase()}
          </Tag>
          {payment.failureCode && (
            <Typography.Text type="danger" style={{ fontSize: 12 }}>
              {payment.failureCode}
            </Typography.Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Provider',
      render: (_, payment) => (
        <Space orientation="vertical" size={2}>
          <span>{payment.provider.toLowerCase()}</span>
          {payment.providerRef && (
            <Typography.Text type="secondary" copyable style={{ fontSize: 11 }}>
              {payment.providerRef}
            </Typography.Text>
          )}
        </Space>
      ),
    },
    {
      title: '',
      align: 'right',
      render: (_, payment) => (
        <Space>
          <Button
            size="small"
            onClick={async () => {
              try {
                setDetail(await adminPayments.detail(payment.id))
              } catch (failure) {
                message.error(failure instanceof RequestError
                  ? failure.message : 'Could not load the payment')
              }
            }}
          >
            Trail
          </Button>
          {payment.intent === 'CHARGE' && payment.status === 'SUCCEEDED' && (
            <Button
              size="small"
              danger
              onClick={() => {
                setRefunding(payment)
                setAmount(payment.amount)
                setReason('')
              }}
            >
              Refund
            </Button>
          )}
        </Space>
      ),
    },
  ]

  const visible = rows ?? []
  const currency = visible[0]?.currency ?? 'MNT'
  const settled = (row: AdminPayment) => row.status === 'SUCCEEDED'
  const charged = visible.filter((row) => row.intent === 'CHARGE' && settled(row))
    .reduce((sum, row) => sum + row.amount, 0)
  const refunded = visible.filter((row) => row.intent === 'REFUND' && settled(row))
    .reduce((sum, row) => sum + Math.abs(row.amount), 0)
  const counts = {
    charges: visible.filter((row) => row.intent === 'CHARGE').length,
    refunds: visible.filter((row) => row.intent === 'REFUND').length,
    failed: visible.filter((row) => row.status === 'FAILED').length,
  }

  return (
    <>
      <PageHead
        title="Payments"
        description={'Every charge and refund that passed through the gateway. Refunds are '
          + 'shown against the charge they reversed, so a booking reads as one story. The '
          + 'figures below cover what the filters have selected, not all time.'}
      />

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} lg={6}>
          <StatTile label="Charged" value={formatMoney(charged, currency)} tone="brand"
                    icon={<CreditCardOutlined />}
                    hint={`${plural(counts.charges, 'charge')} in view`} />
        </Col>
        <Col xs={12} lg={6}>
          <StatTile label="Refunded" value={formatMoney(refunded, currency)} tone="rose"
                    icon={<RollbackOutlined />}
                    hint={`${plural(counts.refunds, 'refund')} in view`} />
        </Col>
        <Col xs={12} lg={6}>
          <StatTile label="Net" value={formatMoney(charged - refunded, currency)} tone="teal"
                    hint="what the platform actually took in" />
        </Col>
        <Col xs={12} lg={6}>
          <StatTile label="Failed" value={counts.failed} tone="accent"
                    icon={<WarningOutlined />}
                    hint={counts.failed > 0 ? 'guests who could not pay' : 'none in view'} />
        </Col>
      </Row>

      <Flex gap={8} wrap style={{ marginBottom: 12 }}>
        <Segmented
          value={intent}
          onChange={(value) => setIntent(value as typeof intent)}
          options={[
            { label: 'All', value: 'all' },
            { label: 'Charges', value: 'CHARGE' },
            { label: 'Refunds', value: 'REFUND' },
          ]}
        />
        <Segmented
          value={status ?? 'any'}
          onChange={(value) => setStatus(value === 'any' ? undefined : String(value))}
          options={[
            { label: 'Any status', value: 'any' },
            { label: 'Settled', value: 'SUCCEEDED' },
            { label: 'Failed', value: 'FAILED' },
            { label: 'Pending', value: 'PENDING' },
          ]}
        />
        <Input.Search
          allowClear
          placeholder="Booking or provider reference"
          style={{ maxWidth: 300 }}
          onSearch={setQuery}
        />
      </Flex>

      {rows === null ? (
        <Flex justify="center" style={{ padding: 32 }}><Spin /></Flex>
      ) : (
        <>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {total} transaction{total === 1 ? '' : 's'}
          </Typography.Text>
          <Table
            rowKey="id"
            size="small"
            style={{ marginTop: 8 }}
            dataSource={rows}
            columns={columns}
            pagination={false}
            locale={{ emptyText: <Empty description="Nothing matches" /> }}
          />
        </>
      )}

      <Drawer
        open={detail !== null}
        width={560}
        title={detail ? `Payment on ${detail.payment.bookingReference}` : ''}
        onClose={() => setDetail(null)}
      >
        {detail && (
          <>
            <Descriptions size="small" column={1} styles={{ label: { width: 150 } }}>
              <Descriptions.Item label="Amount">
                {formatMoney(detail.payment.amount, detail.payment.currency)}
              </Descriptions.Item>
              <Descriptions.Item label="Status">{detail.payment.status}</Descriptions.Item>
              <Descriptions.Item label="Provider">{detail.payment.provider}</Descriptions.Item>
              <Descriptions.Item label="Provider reference">
                {detail.payment.providerRef ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Settled">
                {detail.payment.paidAt ? new Date(detail.payment.paidAt).toLocaleString() : '—'}
              </Descriptions.Item>
              {detail.payment.failureMessage && (
                <Descriptions.Item label="Failure">
                  {detail.payment.failureMessage}
                </Descriptions.Item>
              )}
            </Descriptions>

            <Typography.Title level={5} style={{ marginTop: 20 }}>
              What the provider sent
            </Typography.Title>
            {detail.events.length === 0 ? (
              <Typography.Text type="secondary">
                No callbacks recorded — this payment was settled directly.
              </Typography.Text>
            ) : (
              <Space orientation="vertical" size={8} style={{ width: '100%' }}>
                {detail.events.map((event) => (
                  <Alert
                    key={event.id}
                    type={event.processingError ? 'error'
                      : event.signatureVerified ? 'success' : 'warning'}
                    message={`${event.eventType} · ${new Date(event.receivedAt).toLocaleString()}`}
                    description={event.processingError
                      ?? (event.signatureVerified
                        ? 'Signature verified'
                        : 'Unsigned or mis-signed callback')}
                  />
                ))}
              </Space>
            )}
          </>
        )}
      </Drawer>

      <Modal
        open={refunding !== null}
        title="Refund this charge"
        okText="Refund"
        okButtonProps={{ danger: true, loading: busy, disabled: !reason.trim() || !amount }}
        onCancel={() => setRefunding(null)}
        onOk={async () => {
          if (!refunding || !amount) {
            return
          }
          setBusy(true)
          try {
            await adminPayments.refund(refunding.id, amount, reason)
            message.success('Refunded')
            setRefunding(null)
            setReloadToken((token) => token + 1)
          } catch (failure) {
            message.error(failure instanceof RequestError ? failure.message : 'Could not refund')
          } finally {
            setBusy(false)
          }
        }}
      >
        <Typography.Paragraph type="secondary">
          This reverses money at the provider and records a refund against the booking.
          It does not cancel the stay — do that from the booking if the guest is not coming.
        </Typography.Paragraph>
        <InputNumber
          style={{ width: '100%' }}
          min={1}
          max={refunding?.amount}
          value={amount}
          onChange={setAmount}
          addonAfter={refunding?.currency}
        />
        <Input.TextArea
          rows={2}
          style={{ marginTop: 10 }}
          placeholder="Why (kept in the audit log)"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </Modal>
    </>
  )
}
