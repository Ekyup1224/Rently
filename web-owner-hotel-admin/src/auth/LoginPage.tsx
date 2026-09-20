import { useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import {
  Alert, Button, Card, Flex, Form, Input, Segmented, Space, Typography,
} from 'antd'
import { auth } from '../api/endpoints'
import { RequestError } from '../api/client'
import { useAuth } from './AuthProvider'

const DEVICE_LABEL = 'Owner/Hotel/Admin portal'

/**
 * Sign-in for the staff-facing portal. Phone + code is the primary path, matching
 * the backend; email + password is offered for accounts that have set one.
 */
export function LoginPage() {
  const { adopt, user, initializing } = useAuth()
  const location = useLocation()
  const [method, setMethod] = useState<'phone' | 'email'>('phone')
  const [error, setError] = useState<string | null>(null)

  // Once a session exists, leave the login screen -- either back to where the
  // guard bounced the user from, or to the overview.
  if (user && !initializing) {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from && from !== '/login' ? from : '/'} replace />
  }

  return (
    <Flex align="center" justify="center" style={{ minHeight: '100vh', padding: 24 }}>
      <Card style={{ width: 420 }}>
        <Space orientation="vertical" size="large" style={{ width: '100%' }}>
          <div>
            <Typography.Title level={4} style={{ marginBottom: 4 }}>
              Partner portal
            </Typography.Title>
            <Typography.Text type="secondary">
              For property owners, hotels and platform administrators.
            </Typography.Text>
          </div>

          <Segmented
            block
            value={method}
            onChange={(value) => {
              setMethod(value as 'phone' | 'email')
              setError(null)
            }}
            options={[
              { label: 'Phone code', value: 'phone' },
              { label: 'Email & password', value: 'email' },
            ]}
          />

          {error && <Alert type="error" message={error} showIcon />}

          {method === 'phone'
            ? <PhoneForm onError={setError} onSignedIn={adopt} />
            : <EmailForm onError={setError} onSignedIn={adopt} />}
        </Space>
      </Card>
    </Flex>
  )
}

type SignedIn = ReturnType<typeof useAuth>['adopt']

function PhoneForm({ onError, onSignedIn }: { onError: (message: string | null) => void; onSignedIn: SignedIn }) {
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [resendIn, setResendIn] = useState(0)
  const [devCode, setDevCode] = useState<string | null>(null)

  // Mirrors the server's resend cooldown so the button is disabled for exactly as
  // long as a retry would be rejected.
  useEffect(() => {
    if (resendIn <= 0) {
      return
    }
    const timer = setInterval(() => setResendIn((seconds) => Math.max(0, seconds - 1)), 1000)
    return () => clearInterval(timer)
  }, [resendIn])

  async function sendCode() {
    setBusy(true)
    onError(null)
    try {
      const challenge = await auth.requestOtp(phone)
      setCodeSent(true)
      setResendIn(challenge.resendAfterSeconds)
      // A development server hands the code straight back. Fill it in, replacing
      // any earlier one, or a resend leaves the stale code sitting in the box.
      setDevCode(challenge.devCode ?? null)
      setCode(challenge.devCode ?? '')
    } catch (failure) {
      onError(describe(failure))
    } finally {
      setBusy(false)
    }
  }

  async function verify() {
    setBusy(true)
    onError(null)
    try {
      onSignedIn(await auth.verifyOtp(phone, code, DEVICE_LABEL))
    } catch (failure) {
      onError(describe(failure))
    } finally {
      setBusy(false)
    }
  }

  if (!codeSent) {
    return (
      <Form layout="vertical" onFinish={sendCode}>
        <Form.Item
          label="Phone number"
          required
          extra="Mongolian numbers can be typed as 8 digits; others need a country code."
        >
          <Input
            size="large"
            placeholder="9911 2233"
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            autoFocus
          />
        </Form.Item>
        <Button type="primary" size="large" block htmlType="submit" loading={busy} disabled={!phone.trim()}>
          Send code
        </Button>
      </Form>
    )
  }

  return (
    <Form layout="vertical" onFinish={verify}>
      {devCode && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={`Development code: ${devCode}`}
          description="This server logs codes instead of sending them, so it is filled in for
            you. A deployed server sends it by SMS and never returns it here."
        />
      )}
      <Form.Item label={`Code sent to ${phone}`} required>
        <Input
          size="large"
          placeholder="123456"
          maxLength={6}
          autoFocus
          inputMode="numeric"
          value={code}
          onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
        />
      </Form.Item>
      <Space orientation="vertical" style={{ width: '100%' }}>
        <Button type="primary" size="large" block htmlType="submit" loading={busy}
                disabled={code.length < 4}>
          Sign in
        </Button>
        <Flex justify="space-between">
          <Button type="link" size="small"
                  onClick={() => { setCodeSent(false); setCode(''); setDevCode(null); onError(null) }}>
            Change number
          </Button>
          <Button type="link" size="small" disabled={resendIn > 0 || busy} onClick={sendCode}>
            {resendIn > 0 ? `Resend in ${resendIn}s` : 'Resend code'}
          </Button>
        </Flex>
      </Space>
    </Form>
  )
}

function EmailForm({ onError, onSignedIn }: { onError: (message: string | null) => void; onSignedIn: SignedIn }) {
  const [busy, setBusy] = useState(false)

  async function submit(values: { email: string; password: string }) {
    setBusy(true)
    onError(null)
    try {
      onSignedIn(await auth.login(values.email, values.password, DEVICE_LABEL))
    } catch (failure) {
      onError(describe(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Form layout="vertical" onFinish={submit}>
      <Form.Item label="Email" name="email" rules={[{ required: true, type: 'email' }]}>
        <Input size="large" placeholder="you@example.com" autoFocus />
      </Form.Item>
      <Form.Item label="Password" name="password" rules={[{ required: true }]}>
        <Input.Password size="large" />
      </Form.Item>
      <Button type="primary" size="large" block htmlType="submit" loading={busy}>
        Sign in
      </Button>
    </Form>
  )
}

/** Turns a backend error code into something a partner can act on. */
function describe(failure: unknown): string {
  if (!(failure instanceof RequestError)) {
    return 'Could not reach the server. Check your connection and try again.'
  }
  switch (failure.code) {
    case 'invalid_phone':
      return 'That does not look like a valid phone number.'
    case 'otp_invalid':
      return 'That code is incorrect.'
    case 'otp_expired':
      return 'That code has expired. Request a new one.'
    case 'otp_cooldown':
      return 'A code was just sent. Wait a moment before requesting another.'
    case 'otp_rate_limited':
      return 'Too many codes requested for this number. Try again in an hour.'
    case 'otp_locked':
      return 'Too many incorrect codes. This number is locked for a short while.'
    case 'invalid_credentials':
      return 'Email or password is incorrect.'
    case 'phone_not_verified':
      return 'Verify your phone number before signing in with a password.'
    case 'account_not_active':
      return 'This account cannot sign in. Contact platform support.'
    default:
      return failure.message
  }
}
