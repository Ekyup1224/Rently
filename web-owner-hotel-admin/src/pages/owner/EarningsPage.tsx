import { useEffect, useState } from 'react'
import {
  Alert, Card, Col, DatePicker, Flex, Row, Space, Spin, Statistic, Table, Typography, message,
} from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { RequestError } from '../../api/client'
import { owner } from '../../api/endpoints'
import type { EarningsSummary } from '../../types'
import { formatMoney } from './listingFormat'

/**
 * A host's earnings.
 *
 * <p>Labelled as earned rather than paid, deliberately: payout automation is Step
 * 5, and a host who reads this as money-in-the-bank and reconciles against their
 * account will not enjoy the experience.
 */
export function EarningsPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([
    dayjs().startOf('year'), dayjs().endOf('year'),
  ])
  const [summary, setSummary] = useState<EarningsSummary | null>(null)
  const [loading, setLoading] = useState(true)

  const from = range[0].format('YYYY-MM-DD')
  const to = range[1].format('YYYY-MM-DD')

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      try {
        const loaded = await owner.earnings(from, to)
        if (!cancelled) {
          setSummary(loaded)
        }
      } catch (failure) {
        if (!cancelled) {
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load earnings')
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
  }, [from, to])

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%', maxWidth: 900 }}>
      <Flex align="center" justify="space-between" wrap gap={12}>
        <Typography.Title level={3} style={{ margin: 0 }}>Earnings</Typography.Title>
        <DatePicker.RangePicker
          value={range}
          allowClear={false}
          onChange={(values) => {
            if (values?.[0] && values?.[1]) {
              setRange([values[0], values[1]])
            }
          }}
        />
      </Flex>

      <Alert
        type="info"
        showIcon
        message="Earned, not yet paid out"
        description="Amounts are attributed to when each stay ended. Automated payouts arrive in a later release."
      />

      {loading && <Flex justify="center" style={{ padding: 48 }}><Spin /></Flex>}

      {!loading && summary && (
        <>
          <Row gutter={[16, 16]}>
            <Col xs={24} md={8}>
              <Card>
                <Statistic
                  title="Earned from completed stays"
                  value={formatMoney(summary.earnedFromCompletedStays, summary.currency)}
                />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {summary.completedStays} stay(s)
                </Typography.Text>
              </Card>
            </Col>
            <Col xs={24} md={8}>
              <Card>
                <Statistic
                  title="Confirmed upcoming"
                  value={formatMoney(summary.confirmedUpcoming, summary.currency)}
                />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {summary.upcomingStays} booked stay(s)
                </Typography.Text>
              </Card>
            </Col>
            <Col xs={24} md={8}>
              <Card>
                <Statistic
                  title="Platform fee withheld"
                  value={formatMoney(summary.commissionWithheld, summary.currency)}
                />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  On completed stays
                </Typography.Text>
              </Card>
            </Col>
          </Row>

          <Card title="By month" size="small">
            <Table
              size="small"
              pagination={false}
              rowKey="month"
              dataSource={summary.byMonth}
              locale={{ emptyText: 'No completed stays in this period' }}
              columns={[
                {
                  title: 'Month',
                  dataIndex: 'month',
                  render: (month: string) => dayjs(month).format('MMMM YYYY'),
                },
                { title: 'Stays', dataIndex: 'stays', width: 100 },
                {
                  title: 'Earned',
                  dataIndex: 'earned',
                  align: 'right' as const,
                  render: (earned: number) => formatMoney(earned, summary.currency),
                },
              ]}
            />
          </Card>
        </>
      )}
    </Space>
  )
}
