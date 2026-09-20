import type { Metadata } from 'next'
import { LoginForm } from '@/components/LoginForm'
import { LoginHeading } from '@/components/LoginHeading'

export const metadata: Metadata = {
  title: 'Sign in',
}

export default function LoginPage() {
  return (
    <>
      <LoginHeading />
      <LoginForm />
    </>
  )
}
