import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Col, Empty, Flex, Input, Modal, Popconfirm, Row, Skeleton,
  Table, Tag, Typography,
} from 'antd'
import {
  BankOutlined, CheckCircleOutlined, DownloadOutlined, PlusOutlined, SendOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { RequestError } from '../../api/client'
import { payoutBatches, trust } from '../../api/endpoints'
import type { PayoutBatch, TransferLine } from '../../types'
import { formatMoney } from '../owner/listingFormat'
import { StatTile } from '../../ui/StatTile'
import { BreakdownChart } from '../../ui/charts'
import { palette } from '../../theme'
import { plural } from '../../ui/plural'
import { PageHead } from '../../ui/PageHead'

const STATUS: Record<PayoutBatch['status'], { colour: string; label: string }> = {
  OPEN: { colour: 'blue', label: 'Ready to send' },
  EXPORTED: { colour: 'gold', label: 'At the bank' },
  SETTLED: { colour: 'green', label: 'Paid' },
}

/** The bank file, as a CSV a person can actually upload. */
function toCsv(lines: TransferLine[]): string {
  const header = 'payee,phone,organization,amount,currency,bookings'
  const rows = lines.map((line) => [
    line.payeeName,
    line.payeePhone,
    line.organizationName ?? '',
    String(line.amount),
    line.currency,
    line.bookings.join(' '),
  ].map((cell) => `"${cell.replace(/"/g, '""')}"`).join(','))
  return [header, ...rows].join('\n')
}

/**
 * Paying hosts, one run at a time.
 *
 * <p>The last step is deliberately manual: no Mongolian bank offers an API this
 * can call, so the platform decides what is owed, groups it per host, and hands
 * a person one file. Pretending to send money we cannot send would be worse than
 * admitting where the automation stops.
 */
export function PayoutBatchesPage() {
  const { message } = AntApp.useApp()
  const [batches, setBatches] = useState<PayoutBatch[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)

  const [viewing, setViewing] = useState<PayoutBatch | null>(null)
  const [lines, setLines] = useState<TransferLine[] | null>(null)

  const [settling, setSettling] = useState<PayoutBatch | null>(null)
  const [providerRef, setProviderRef] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const found = await payoutBatches.list(page)
      setBatches(found.rows)
      setTotal(found.total)
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not load runs')
    } finally {
      setLoading(false)
    }
  }, [page, message])

  useEffect(() => { void load() }, [load])

  /** Applies the release rules now, rather than waiting for the nightly job. */
  async function releaseDue() {
    setBusy(true)
    try {
      const { released } = await trust.releaseDue()
      message.success(released > 0
        ? `Released ${plural(released, 'payout')}`
        : 'Nothing was due. Payouts wait for check-in, identity and a clear listing.')
      await load()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not run the sweep')
    } finally {
      setBusy(false)
    }
  }

  async function assemble() {
    setBusy(true)
    try {
      const batch = await payoutBatches.assemble()
      message.success(`Run assembled: ${plural(batch.payoutCount, 'payout')}`)
      await load()
    } catch (failure) {
      message.error(failure instanceof RequestError
        ? failure.message : 'Could not assemble a run')
    } finally {
      setBusy(false)
    }
  }

  async function openLines(batch: PayoutBatch) {
    setViewing(batch)
    setLines(null)
    try {
      setLines(await payoutBatches.lines(batch.id))
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not load the lines')
      setViewing(null)
    }
  }

  function download() {
    if (!viewing || !lines) {
      return
    }
    const blob = new Blob([toCsv(lines)], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `payout-run-${viewing.id.slice(0, 8)}.csv`
    anchor.click()
    URL.revokeObjectURL(url)
  }

  async function markExported(batch: PayoutBatch) {
    try {
      await payoutBatches.markExported(batch.id)
      message.success('Marked as sent to the bank')
      setViewing(null)
      await load()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not mark it')
    }
  }

  async function settle() {
    if (!settling || !providerRef.trim()) {
      return
    }
    setBusy(true)
    try {
      await payoutBatches.settle(settling.id, providerRef.trim())
      message.success('Run settled — every payout in it is now paid')
      setSettling(null)
      setProviderRef('')
      await load()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not settle it')
    } finally {
      setBusy(false)
    }
  }

  const currency = batches[0]?.currency ?? 'MNT'
  const open = batches.filter((batch) => batch.status === 'OPEN')
  const atBank = batches.filter((batch) => batch.status === 'EXPORTED')
  const owed = open.reduce((sum, batch) => sum + batch.total, 0)
  const inFlight = atBank.reduce((sum, batch) => sum + batch.total, 0)
  const settled = batches.filter((batch) => batch.status === 'SETTLED')
    .reduce((sum, batch) => sum + batch.total, 0)

  return (
    <>
      <PageHead
        title="Payout runs"
        description={'Everything released and not yet sent, grouped one line per host. '
          + 'Assemble a run, download the file, upload it at the bank, then record what '
          + 'the bank gave you back.'}
        extra={[
          <Button key="sweep" icon={<ThunderboltOutlined />} loading={busy} onClick={releaseDue}>
            Release what is due
          </Button>,
          <Button key="assemble" type="primary" icon={<PlusOutlined />} loading={busy}
                  onClick={assemble}>
            Assemble a run
          </Button>,
        ]}
      />

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} lg={16}>
          <Row gutter={[16, 16]}>
            <Col xs={12} lg={8}>
              <StatTile label="Ready to send" value={formatMoney(owed, currency)} tone="brand"
                        icon={<SendOutlined />} hint={plural(open.length, 'run')} />
            </Col>
            <Col xs={12} lg={8}>
              <StatTile label="At the bank" value={formatMoney(inFlight, currency)} tone="accent"
                        icon={<BankOutlined />} hint={plural(atBank.length, 'run')} />
            </Col>
            <Col xs={12} lg={8}>
              <StatTile label="Paid" value={formatMoney(settled, currency)} tone="teal"
                        icon={<CheckCircleOutlined />} hint="in this page" />
            </Col>
          </Row>
        </Col>
        <Col xs={24} lg={8}>
          <BreakdownChart
            title="Money by stage"
            height={200}
            slices={[
              { name: 'Ready to send', value: owed, colour: palette.brand },
              { name: 'At the bank', value: inFlight, colour: palette.accent },
              { name: 'Paid', value: settled, colour: palette.teal },
            ]}
          />
        </Col>
      </Row>

      {open.length === 0 && atBank.length === 0 && (
        <Alert
          style={{ marginBottom: 16 }}
          type="info"
          showIcon
          message="Nothing is waiting"
          description={'Payouts only become available once the guest has checked in, the host '
            + 'is identity-verified, and the listing has no open flags. "Release what is due" '
            + 'applies those rules now instead of waiting for tonight.'}
        />
      )}

      {loading ? <Skeleton active paragraph={{ rows: 6 }} /> : (
        <Card size="small">
          <Table<PayoutBatch>
            rowKey="id"
            dataSource={batches}
            size="middle"
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
                                        description="No runs yet" /> }}
            pagination={{
              current: page + 1,
              pageSize: 25,
              total,
              onChange: (next) => setPage(next - 1),
              showSizeChanger: false,
            }}
            columns={[
              {
                title: 'Run',
                dataIndex: 'id',
                width: 130,
                render: (id: string) => (
                  <Typography.Text code style={{ fontSize: 12 }}>{id.slice(0, 8)}</Typography.Text>
                ),
              },
              {
                title: 'Assembled',
                dataIndex: 'createdAt',
                width: 160,
                render: (at: string) => dayjs(at).format('D MMM YYYY HH:mm'),
              },
              {
                title: 'Payouts',
                dataIndex: 'payoutCount',
                width: 100,
                align: 'right',
              },
              {
                title: 'Total',
                dataIndex: 'total',
                width: 160,
                align: 'right',
                render: (amount: number, row) => (
                  <Typography.Text strong>{formatMoney(amount, row.currency)}</Typography.Text>
                ),
              },
              {
                title: 'Stage',
                dataIndex: 'status',
                width: 140,
                render: (status: PayoutBatch['status']) => (
                  <Tag color={STATUS[status].colour} variant="filled">
                    {STATUS[status].label}
                  </Tag>
                ),
              },
              {
                title: '',
                key: 'actions',
                width: 200,
                render: (_, row) => (
                  <Flex gap={6} wrap>
                    <Button size="small" onClick={() => openLines(row)}>Lines</Button>
                    {row.status === 'EXPORTED' && (
                      <Button size="small" type="primary" onClick={() => setSettling(row)}>
                        Confirm paid
                      </Button>
                    )}
                  </Flex>
                ),
              },
            ]}
          />
        </Card>
      )}

      <Modal
        open={viewing !== null}
        title={`Transfer lines · ${viewing?.payoutCount ?? 0} payouts`}
        width={760}
        onCancel={() => setViewing(null)}
        footer={
          <Flex gap={8} justify="flex-end">
            <Button onClick={() => setViewing(null)}>Close</Button>
            <Button icon={<DownloadOutlined />} onClick={download} disabled={!lines}>
              Download CSV
            </Button>
            {viewing?.status === 'OPEN' && (
              <Popconfirm
                title="Sent to the bank?"
                description="Only say yes once the file is actually uploaded."
                onConfirm={() => markExported(viewing)}
              >
                <Button type="primary" icon={<SendOutlined />}>Mark as sent</Button>
              </Popconfirm>
            )}
          </Flex>
        }
      >
        {lines === null ? <Skeleton active /> : (
          <Table<TransferLine>
            rowKey="payeeId"
            dataSource={lines}
            size="small"
            pagination={false}
            columns={[
              {
                title: 'Payee',
                dataIndex: 'payeeName',
                render: (name: string, row) => (
                  <Flex vertical gap={2}>
                    <Typography.Text strong>{name}</Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {row.organizationName ? `${row.organizationName} · ` : ''}{row.payeePhone}
                    </Typography.Text>
                  </Flex>
                ),
              },
              {
                title: 'Stays',
                dataIndex: 'bookings',
                width: 220,
                render: (bookings: string[]) => (
                  <Flex gap={4} wrap>
                    {bookings.map((reference) => (
                      <Tag key={reference} variant="filled" style={{ fontSize: 11 }}>
                        {reference}
                      </Tag>
                    ))}
                  </Flex>
                ),
              },
              {
                title: 'Amount',
                dataIndex: 'amount',
                width: 150,
                align: 'right',
                render: (amount: number, row) => (
                  <Typography.Text strong>{formatMoney(amount, row.currency)}</Typography.Text>
                ),
              },
            ]}
          />
        )}
      </Modal>

      <Modal
        open={settling !== null}
        title="Confirm the bank moved it"
        okText="Mark every payout paid"
        okButtonProps={{ disabled: !providerRef.trim(), loading: busy }}
        onOk={settle}
        onCancel={() => { setSettling(null); setProviderRef('') }}
      >
        <Typography.Paragraph type="secondary" style={{ fontSize: 13 }}>
          This marks all {settling?.payoutCount} payouts in the run as paid against one
          reference. It cannot be undone, so only do it once the transfer has cleared.
        </Typography.Paragraph>
        <Input
          value={providerRef}
          maxLength={128}
          onChange={(event) => setProviderRef(event.target.value)}
          placeholder="Bank reference, e.g. KHAN-2026-0913-01"
        />
      </Modal>
    </>
  )
}
