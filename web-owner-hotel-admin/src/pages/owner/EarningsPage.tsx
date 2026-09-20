import { useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Card, Col, DatePicker, Flex, Row, Skeleton, Table,
} from 'antd'
import { CalendarOutlined, PercentageOutlined, RiseOutlined } from '@ant-design/icons'
import dayjs, { type Dayjs } from 'dayjs'
import { RequestError } from '../../api/client'
import { owner } from '../../api/endpoints'
import type { EarningsSummary } from '../../types'
import { formatMoney } from './listingFormat'
import { StatTile } from '../../ui/StatTile'
import { BreakdownChart, TrendChart, type ChartRow } from '../../ui/charts'
import { palette } from '../../theme'
import { plural } from '../../ui/plural'
import { PageHead } from '../../ui/PageHead'

/**
 * A host's earnings.
 *
 * <p>Labelled as earned rather than paid, deliberately. Earning happens when a
 * stay ends; being paid happens after the guest has checked in, the host is
 * verified and the listing is unflagged. A host who reconciles this page against
 * their bank statement will not enjoy the experience, so Payouts is linked from
 * the first line.
 */
export function EarningsPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

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
  }, [from, to, message])

  const months: ChartRow[] = (summary?.byMonth ?? []).map((entry) => ({
    month: dayjs(entry.month + '-01').format('MMM YY'),
    earned: entry.earned,
    stays: entry.stays,
  }))

  return (
    <>
      <PageHead
        title="Earnings"
        description={'What you have earned, attributed to when each stay ended. Money is '
          + 'released for transfer after your guest checks in — see Payouts for where each '
          + 'amount has got to.'}
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
      />

      {loading && <Skeleton active paragraph={{ rows: 8 }} />}

      {!loading && summary && (
        <Flex vertical gap={16}>
          <Row gutter={[16, 16]}>
            <Col xs={24} md={8}>
              <StatTile
                label="Earned from completed stays"
                value={formatMoney(summary.earnedFromCompletedStays, summary.currency)}
                hint={plural(summary.completedStays, 'stay')}
                icon={<RiseOutlined />}
                tone="brand"
              />
            </Col>
            <Col xs={24} md={8}>
              <StatTile
                label="Confirmed upcoming"
                value={formatMoney(summary.confirmedUpcoming, summary.currency)}
                hint={`${plural(summary.upcomingStays, 'stay')} booked`}
                icon={<CalendarOutlined />}
                tone="teal"
              />
            </Col>
            <Col xs={24} md={8}>
              <StatTile
                label="Platform fee withheld"
                value={formatMoney(summary.commissionWithheld, summary.currency)}
                hint="On completed stays"
                icon={<PercentageOutlined />}
                tone="accent"
              />
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            <Col xs={24} lg={15}>
              <TrendChart
                title="Earned by month"
                data={months}
                xKey="month"
                series={[{ key: 'earned', label: 'Earned' }]}
                format={(value) => value >= 1_000_000
                  ? `${(value / 1_000_000).toFixed(1)}M` : `${Math.round(value / 1000)}K`}
                note="Your share, after commission."
                height={250}
              />
            </Col>
            <Col xs={24} lg={9}>
              <BreakdownChart
                title="Your share of what guests paid"
                height={250}
                total={formatMoney(summary.earnedFromCompletedStays, summary.currency)}
                totalLabel="yours"
                note="On completed stays in this period."
                slices={[
                  { name: 'You keep', value: summary.earnedFromCompletedStays,
                    colour: palette.brand },
                  { name: 'Commission', value: summary.commissionWithheld,
                    colour: palette.accent },
                ]}
              />
            </Col>
          </Row>

          {summary.confirmedUpcoming > 0 && (
            <Alert
              type="info"
              showIcon
              message="Upcoming earnings are not yet yours to spend"
              description={'A booking can still be cancelled, and nothing is released until '
                + 'the guest actually checks in.'}
            />
          )}

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
        </Flex>
      )}
    </>
  )
}
