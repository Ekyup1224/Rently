import { Card, Result, Space, Typography } from 'antd'

/** Placeholder for the Step 2 listing CRUD. */
export function PropertiesPage() {
  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>Properties</Typography.Title>
      <Card>
        <Result
          status="info"
          title="Listings arrive in Step 2"
          subTitle={
            'This is where house and apartment listings will be created and edited: '
            + 'photos, amenities, house rules, pricing rules and the availability calendar.'
          }
        />
      </Card>
    </Space>
  )
}
