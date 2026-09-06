import { useMemo } from 'react'
import { Avatar, Dropdown, Flex, Layout, Menu, Tag, Typography } from 'antd'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import type { Role } from '../types'
import { useAuth } from '../auth/AuthProvider'

interface NavItem {
  key: string
  label: string
  allow: Role[]
}

/**
 * Navigation is built from the signed-in account's roles, so one shell serves the
 * owner, hotel and admin sides rather than three separate applications.
 */
const NAV: NavItem[] = [
  { key: '/', label: 'Overview', allow: ['HOUSE_OWNER', 'HOTEL_MANAGER', 'HOTEL_STAFF', 'SUPER_ADMIN'] },
  { key: '/properties', label: 'Properties', allow: ['HOUSE_OWNER'] },
  { key: '/hotel/inventory', label: 'Rates & inventory', allow: ['HOTEL_MANAGER', 'HOTEL_STAFF'] },
  { key: '/admin/users', label: 'Users', allow: ['SUPER_ADMIN'] },
  { key: '/admin/audit-logs', label: 'Audit log', allow: ['SUPER_ADMIN'] },
]

const ROLE_LABELS: Record<Role, string> = {
  CLIENT: 'Guest',
  HOUSE_OWNER: 'House owner',
  HOTEL_MANAGER: 'Hotel manager',
  HOTEL_STAFF: 'Hotel staff',
  SUPER_ADMIN: 'Administrator',
}

export function AppShell() {
  const { user, roles, signOut } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const items = useMemo(
    () => NAV.filter((item) => item.allow.some((role) => roles.includes(role)))
      .map((item) => ({ key: item.key, label: <Link to={item.key}>{item.label}</Link> })),
    [roles],
  )

  // Longest matching prefix, so /admin/users/123 still highlights Users.
  const selected = useMemo(() => {
    const matches = NAV.map((item) => item.key)
      .filter((key) => key === '/' ? location.pathname === '/' : location.pathname.startsWith(key))
      .sort((left, right) => right.length - left.length)
    return matches.slice(0, 1)
  }, [location.pathname])

  const staffRoles = roles.filter((role) => role !== 'CLIENT')

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider breakpoint="lg" collapsedWidth={0} width={220} theme="light">
        <div style={{ padding: '20px 16px 12px' }}>
          <Typography.Text strong>Partner portal</Typography.Text>
        </div>
        <Menu mode="inline" selectedKeys={selected} items={items} style={{ borderInlineEnd: 0 }} />
      </Layout.Sider>

      <Layout>
        <Layout.Header style={{ background: '#fff', paddingInline: 24, borderBottom: '1px solid #f0f0f0' }}>
          <Flex align="center" justify="space-between" style={{ height: '100%' }}>
            <Flex gap={8} align="center">
              {staffRoles.map((role) => (
                <Tag key={role} color="blue">{ROLE_LABELS[role]}</Tag>
              ))}
            </Flex>
            <Dropdown
              menu={{
                items: [
                  { key: 'account', label: <Link to="/account">My account</Link> },
                  { type: 'divider' },
                  {
                    key: 'signout',
                    label: 'Sign out',
                    danger: true,
                    onClick: async () => {
                      await signOut()
                      navigate('/login', { replace: true })
                    },
                  },
                ],
              }}
            >
              <Flex gap={8} align="center" style={{ cursor: 'pointer' }}>
                <Avatar size="small">{(user?.fullName ?? user?.phone ?? '?').slice(0, 1)}</Avatar>
                <Typography.Text>{user?.fullName ?? user?.phone}</Typography.Text>
              </Flex>
            </Dropdown>
          </Flex>
        </Layout.Header>

        <Layout.Content style={{ padding: 24 }}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  )
}
