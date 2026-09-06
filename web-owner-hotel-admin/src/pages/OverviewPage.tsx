import { Alert, Card, Col, Descriptions, Row, Space, Tag, Typography } from 'antd'
import { useAuth } from '../auth/AuthProvider'

/**
 * Landing page. Deliberately shows account and role facts rather than fake
 * metrics — the KPI cards arrive in Step 4 with real GMV and booking data.
 */
export function OverviewPage() {
  const { user, roles } = useAuth()
  const isStaff = roles.some((role) => role !== 'CLIENT')

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>
        Overview
      </Typography.Title>

      {!isStaff && (
        <Alert
          type="info"
          showIcon
          message="This account has no partner role yet"
          description={
            'You are signed in as a guest. Apply to become a house owner or hotel from '
            + 'the guest app, and an administrator will review it.'
          }
        />
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="Account">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Phone">{user?.phone}</Descriptions.Item>
              <Descriptions.Item label="Email">{user?.email ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={user?.status === 'ACTIVE' ? 'green' : 'orange'}>{user?.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Identity check">
                <Tag>{user?.kycStatus}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Roles">
                {roles.map((role) => <Tag key={role}>{role}</Tag>)}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="What is built so far">
            <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
              Step 1 of the build plan: accounts, roles and the admin console shell.
            </Typography.Paragraph>
            <ul style={{ paddingInlineStart: 20, margin: 0 }}>
              <li>Phone-code and password sign-in, rotating sessions</li>
              <li>Role grants, including hotel roles scoped to a business</li>
              <li>Admin user administration and the audit trail</li>
            </ul>
            <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
              Next: property listings and the guest booking flow (Step 2).
            </Typography.Paragraph>
          </Card>
        </Col>
      </Row>
    </Space>
  )
}
