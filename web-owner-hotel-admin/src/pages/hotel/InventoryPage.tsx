import { Card, Result, Space, Typography } from 'antd'

/** Placeholder for the Step 3 date x room-type rate and inventory grid. */
export function InventoryPage() {
  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>Rates & inventory</Typography.Title>
      <Card>
        <Result
          status="info"
          title="The inventory calendar arrives in Step 3"
          subTitle={
            'A date by room-type grid with editable rates, available counts, stop-sell '
            + 'and minimum-stay rules, plus bulk edits across a date range.'
          }
        />
      </Card>
    </Space>
  )
}
