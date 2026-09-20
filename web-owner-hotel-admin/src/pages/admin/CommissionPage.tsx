import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Form, Input, InputNumber, Space, Spin, Table, Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { RequestError } from '../../api/client'
import { adminListings } from '../../api/endpoints'
import type { CommissionRule } from '../../types'
import { PageHead } from '../../ui/PageHead'

/**
 * The platform's take rate.
 *
 * <p>Rates are never edited: a change closes the current rule and opens a new one,
 * so a booking priced months ago can still be explained. The table is therefore a
 * history, not a list of settings.
 */
export function CommissionPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const [rules, setRules] = useState<CommissionRule[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)
  const [form] = Form.useForm()

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const loaded = await adminListings.commissionRules()
        if (!cancelled) {
          setRules(loaded)
        }
      } catch (failure) {
        if (!cancelled) {
          setRules([])
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load commission rules')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [reloadToken, message])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  async function submit(values: { hostFeePercent: number; guestFeePercent: number; note?: string }) {
    setBusy(true)
    try {
      await adminListings.createCommissionRule({ scope: 'GLOBAL', ...values })
      message.success('New rate is in force for bookings from now on')
      form.resetFields()
      reload()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not save the rate')
    } finally {
      setBusy(false)
    }
  }

  const current = rules?.find((rule) => !rule.effectiveTo)

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%', maxWidth: 900 }}>
      <PageHead
        title="Commission"
        description={'What the platform takes, and from whom. A new rule applies to bookings made after it starts; it never re-prices one already made.'}
      />

      <Alert
        type="info"
        showIcon
        message="Changing the rate does not touch existing bookings"
        description="Every booking records the rule it was priced under, so past payouts and
          invoices stay explainable. A change takes effect for bookings made from that moment on."
      />

      {rules === null && <Spin />}

      {current && (
        <Card size="small" title="In force now">
          <Space size="large" wrap>
            <Typography.Text>
              Host fee <Typography.Text strong>{current.hostFeePercent}%</Typography.Text>
            </Typography.Text>
            <Typography.Text>
              Guest service fee <Typography.Text strong>{current.guestFeePercent}%</Typography.Text>
            </Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              since {dayjs(current.effectiveFrom).format('D MMM YYYY')}
            </Typography.Text>
          </Space>
          {current.note && (
            <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginTop: 8, marginBottom: 0 }}>
              {current.note}
            </Typography.Paragraph>
          )}
        </Card>
      )}

      <Card size="small" title="Set a new rate">
        <Form form={form} layout="inline" onFinish={submit}
              initialValues={{ hostFeePercent: current?.hostFeePercent ?? 10,
                               guestFeePercent: current?.guestFeePercent ?? 0 }}>
          <Form.Item label="Host fee %" name="hostFeePercent" rules={[{ required: true }]}>
            <InputNumber min={0} max={100} step={0.5} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item label="Guest fee %" name="guestFeePercent" rules={[{ required: true }]}>
            <InputNumber min={0} max={100} step={0.5} style={{ width: 110 }} />
          </Form.Item>
          <Form.Item label="Why" name="note" style={{ minWidth: 260 }}>
            <Input placeholder="Worth recording for whoever asks later" maxLength={255} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={busy}>Apply new rate</Button>
          </Form.Item>
        </Form>
      </Card>

      <Card size="small" title="History">
        <Table
          size="small"
          rowKey="id"
          pagination={false}
          dataSource={rules ?? []}
          columns={[
            {
              title: 'Scope',
              dataIndex: 'scope',
              render: (scope: string, rule: CommissionRule) =>
                rule.category ? `${scope.toLowerCase()} · ${rule.category}` : scope.toLowerCase(),
            },
            { title: 'Host %', dataIndex: 'hostFeePercent', width: 90 },
            { title: 'Guest %', dataIndex: 'guestFeePercent', width: 90 },
            {
              title: 'From',
              dataIndex: 'effectiveFrom',
              render: (value: string) => dayjs(value).format('D MMM YYYY HH:mm'),
            },
            {
              title: 'Until',
              dataIndex: 'effectiveTo',
              render: (value?: string) => value
                ? dayjs(value).format('D MMM YYYY HH:mm')
                : <Tag color="green">current</Tag>,
            },
            { title: 'Note', dataIndex: 'note', ellipsis: true },
          ]}
        />
      </Card>
    </Space>
  )
}
