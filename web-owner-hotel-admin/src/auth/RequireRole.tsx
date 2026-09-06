import { Button, Flex, Result, Spin } from 'antd'
import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import type { Role } from '../types'
import { useAuth } from './AuthProvider'

/**
 * Route guard. This is convenience, not security — it only decides what to render.
 * Every underlying API call is authorized independently on the server, so editing
 * the client state buys access to nothing.
 */
export function RequireRole({ allow, children }: { allow: Role[]; children: ReactNode }) {
  const { user, initializing, hasRole } = useAuth()
  const location = useLocation()

  if (initializing) {
    return (
      <Flex align="center" justify="center" style={{ minHeight: '60vh' }}>
        <Spin size="large" />
      </Flex>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (!hasRole(...allow)) {
    return (
      <Result
        status="403"
        title="Not available for your account"
        subTitle={`This section needs one of: ${allow.join(', ')}.`}
        extra={<Button type="primary" href="/">Back to overview</Button>}
      />
    )
  }

  return <>{children}</>
}
