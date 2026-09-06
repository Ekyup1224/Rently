import { useState } from 'react'
import { Button, Card, Form, Input, Select, Space, Typography, message } from 'antd'
import { users } from '../api/endpoints'
import { RequestError } from '../api/client'
import { useAuth } from '../auth/AuthProvider'

/** Self-service profile editing, wired to PATCH /users/me. */
export function AccountPage() {
  const { user, reload } = useAuth()
  const [busy, setBusy] = useState(false)

  async function save(values: { fullName?: string; email?: string; locale?: string }) {
    setBusy(true)
    try {
      await users.updateProfile(values)
      await reload()
      message.success('Profile saved')
    } catch (failure) {
      message.error(
        failure instanceof RequestError && failure.code === 'email_taken'
          ? 'That email is already in use.'
          : 'Could not save your profile',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%', maxWidth: 560 }}>
      <Typography.Title level={3} style={{ margin: 0 }}>My account</Typography.Title>
      <Card>
        <Form
          layout="vertical"
          onFinish={save}
          initialValues={{
            fullName: user?.fullName,
            email: user?.email,
            locale: user?.locale ?? 'mn',
          }}
        >
          <Form.Item label="Phone">
            {/* Changing the account's primary identifier needs its own verified flow. */}
            <Input value={user?.phone} disabled />
          </Form.Item>
          <Form.Item label="Full name" name="fullName">
            <Input placeholder="Your name" />
          </Form.Item>
          <Form.Item
            label="Email"
            name="email"
            rules={[{ type: 'email', message: 'Enter a valid email address' }]}
            extra="Changing this clears its verified state until confirmed again."
          >
            <Input placeholder="you@example.com" />
          </Form.Item>
          <Form.Item label="Language" name="locale">
            <Select
              options={[
                { value: 'mn', label: 'Монгол' },
                { value: 'en', label: 'English' },
              ]}
            />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={busy}>Save changes</Button>
        </Form>
      </Card>
    </Space>
  )
}
