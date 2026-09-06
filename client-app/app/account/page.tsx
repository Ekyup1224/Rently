import type { Metadata } from 'next'
import { AccountView } from '@/components/AccountView'

export const metadata: Metadata = {
  title: 'Account',
}

export default function AccountPage() {
  return (
    <>
      <h1>Account</h1>
      <AccountView />
    </>
  )
}
