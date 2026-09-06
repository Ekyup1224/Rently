import type { Metadata } from 'next'
import { LoginForm } from '@/components/LoginForm'

export const metadata: Metadata = {
  title: 'Sign in',
}

export default function LoginPage() {
  return (
    <>
      <h1>Sign in</h1>
      <p className="muted" style={{ marginTop: 0 }}>
        Enter your phone number and we will text you a code. No password needed.
      </p>
      <LoginForm />
    </>
  )
}
