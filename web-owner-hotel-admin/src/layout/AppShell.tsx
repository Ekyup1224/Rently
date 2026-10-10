import { useMemo } from 'react'
import type { ReactNode } from 'react'
import { Avatar, Badge, Dropdown, Flex, Layout, Menu, Tag, Typography } from 'antd'
import type { MenuProps } from 'antd'
import {
  AppstoreOutlined, AuditOutlined, BankOutlined, BarChartOutlined, CalendarOutlined,
  CreditCardOutlined, DashboardOutlined, DownOutlined, HomeOutlined, IdcardOutlined,
  LogoutOutlined, MessageOutlined, PercentageOutlined, SafetyCertificateOutlined,
  ShopOutlined, StarOutlined, TeamOutlined, UserOutlined, WalletOutlined,
} from '@ant-design/icons'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import type { Role } from '../types'
import { useAuth } from '../auth/AuthProvider'
import { palette } from '../theme'

interface NavItem {
  key: string
  label: string
  icon: ReactNode
  allow: Role[]
  /** Which heading this sits under. Items with the same group stay together. */
  group: string
}

/**
 * Navigation is built from the signed-in account's roles, so one shell serves the
 * owner, hotel and admin sides rather than three separate applications.
 *
 * <p>Grouped by what someone is trying to do, not by which module the endpoint
 * lives in. An admin has fourteen destinations; an ungrouped list of fourteen is
 * a list nobody reads past the fifth item.
 */
const NAV: NavItem[] = [
  { key: '/', label: 'Overview', icon: <DashboardOutlined />, group: '',
    allow: ['HOUSE_OWNER', 'HOTEL_MANAGER', 'HOTEL_STAFF', 'SUPER_ADMIN'] },
  { key: '/messages', label: 'Messages', icon: <MessageOutlined />, group: '',
    allow: ['HOUSE_OWNER', 'HOTEL_MANAGER', 'HOTEL_STAFF'] },

  { key: '/properties', label: 'Properties', icon: <HomeOutlined />, group: 'Your places',
    allow: ['HOUSE_OWNER'] },
  { key: '/bookings', label: 'Reservations', icon: <CalendarOutlined />, group: 'Your places',
    allow: ['HOUSE_OWNER'] },
  { key: '/hotel/hotels', label: 'Hotels', icon: <ShopOutlined />, group: 'Your places',
    allow: ['HOTEL_MANAGER', 'HOTEL_STAFF'] },

  { key: '/earnings', label: 'Earnings', icon: <BarChartOutlined />, group: 'Money',
    allow: ['HOUSE_OWNER'] },
  { key: '/payouts', label: 'Payouts', icon: <WalletOutlined />, group: 'Money',
    allow: ['HOUSE_OWNER'] },

  { key: '/admin/analytics', label: 'Analytics', icon: <BarChartOutlined />, group: 'Business',
    allow: ['SUPER_ADMIN'] },
  { key: '/admin/payments', label: 'Payments', icon: <CreditCardOutlined />, group: 'Business',
    allow: ['SUPER_ADMIN'] },
  { key: '/admin/payout-batches', label: 'Payout runs', icon: <BankOutlined />, group: 'Business',
    allow: ['SUPER_ADMIN'] },
  { key: '/admin/commission', label: 'Commission', icon: <PercentageOutlined />, group: 'Business',
    allow: ['SUPER_ADMIN'] },

  { key: '/admin/applications', label: 'Host applications', icon: <IdcardOutlined />,
    group: 'Queues', allow: ['SUPER_ADMIN'] },
  { key: '/admin/listings', label: 'Listing review', icon: <AppstoreOutlined />, group: 'Queues',
    allow: ['SUPER_ADMIN'] },
  { key: '/admin/hotels', label: 'Hotel review', icon: <ShopOutlined />, group: 'Queues',
    allow: ['SUPER_ADMIN'] },
  { key: '/admin/moderation', label: 'Reviews and messages', icon: <StarOutlined />,
    group: 'Queues', allow: ['SUPER_ADMIN'] },
  { key: '/admin/trust', label: 'Trust and safety', icon: <SafetyCertificateOutlined />,
    group: 'Queues', allow: ['SUPER_ADMIN'] },

  { key: '/admin/users', label: 'Users', icon: <TeamOutlined />, group: 'System',
    allow: ['SUPER_ADMIN'] },
  { key: '/admin/audit-logs', label: 'Audit log', icon: <AuditOutlined />, group: 'System',
    allow: ['SUPER_ADMIN'] },
]

const ROLE_LABELS: Record<Role, string> = {
  CLIENT: 'Guest',
  HOUSE_OWNER: 'House owner',
  HOTEL_MANAGER: 'Hotel manager',
  HOTEL_STAFF: 'Hotel staff',
  SUPER_ADMIN: 'Administrator',
}

const ROLE_COLOURS: Record<Role, string> = {
  CLIENT: 'default',
  HOUSE_OWNER: 'blue',
  HOTEL_MANAGER: 'cyan',
  HOTEL_STAFF: 'geekblue',
  SUPER_ADMIN: 'purple',
}

export function AppShell() {
  const { user, roles, signOut } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const items = useMemo<MenuProps['items']>(() => {
    const visible = NAV.filter((item) => item.allow.some((role) => roles.includes(role)))

    // Preserve NAV order, both of the groups and within them.
    const groups: string[] = []
    for (const item of visible) {
      if (!groups.includes(item.group)) {
        groups.push(item.group)
      }
    }

    const built: NonNullable<MenuProps['items']> = []
    for (const group of groups) {
      const children = visible.filter((item) => item.group === group)
        .map((item) => ({
          key: item.key,
          icon: item.icon,
          label: <Link to={item.key}>{item.label}</Link>,
        }))
      // The ungrouped items at the top need no heading above them.
      if (group === '') {
        built.push(...children)
      } else {
        built.push({ key: `group:${group}`, type: 'group', label: group, children })
      }
    }
    return built
  }, [roles])

  // Longest matching prefix, so /admin/users/123 still highlights Users.
  const selected = useMemo(() => {
    const matches = NAV.map((item) => item.key)
      .filter((key) => key === '/' ? location.pathname === '/' : location.pathname.startsWith(key))
      .sort((left, right) => right.length - left.length)
    return matches.slice(0, 1)
  }, [location.pathname])

  const staffRoles = roles.filter((role) => role !== 'CLIENT')
  const current = NAV.find((item) => item.key === selected[0])
  const initial = (user?.fullName ?? user?.phone ?? '?').trim().slice(0, 1).toUpperCase()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider breakpoint="lg" collapsedWidth={0} width={236} theme="light"
                    className="shell__sider">
        <div className="shell__brand">
          <span className="shell__mark" aria-hidden="true">
            <svg viewBox="0 0 64 64" fill="none" width={18} height={18}>
              <circle cx="32" cy="32" r="25" stroke="currentColor" strokeWidth="5" />
              <g stroke="currentColor" strokeWidth="5" strokeLinecap="round">
                <line x1="32" y1="8" x2="32" y2="21" />
                <line x1="32" y1="43" x2="32" y2="56" />
                <line x1="8" y1="32" x2="21" y2="32" />
                <line x1="43" y1="32" x2="56" y2="32" />
              </g>
              <circle cx="32" cy="32" r="7.5" fill="currentColor" />
            </svg>
          </span>
          <div style={{ lineHeight: 1.2 }}>
            <Typography.Text strong style={{ display: 'block' }}>Rently</Typography.Text>
            <Typography.Text style={{ fontSize: 11, color: palette.inkMuted }}>
              Partner portal
            </Typography.Text>
          </div>
        </div>
        <Menu mode="inline" selectedKeys={selected} items={items}
              style={{ borderInlineEnd: 0, paddingBottom: 24 }} />
      </Layout.Sider>

      <Layout>
        <Layout.Header className="shell__header" style={{ paddingInline: 24 }}>
          <Flex align="center" justify="space-between" style={{ height: '100%' }} gap={16}>
            <Flex gap={10} align="center" style={{ minWidth: 0 }}>
              <Typography.Text strong style={{ fontSize: 15 }}>
                {current?.label ?? 'Portal'}
              </Typography.Text>
              <Flex gap={6} align="center">
                {staffRoles.map((role) => (
                  <Tag key={role} color={ROLE_COLOURS[role]} variant="filled">
                    {ROLE_LABELS[role]}
                  </Tag>
                ))}
              </Flex>
            </Flex>

            <Dropdown
              menu={{
                items: [
                  { key: 'account', icon: <UserOutlined />,
                    label: <Link to="/account">My account</Link> },
                  { type: 'divider' },
                  {
                    key: 'signout',
                    icon: <LogoutOutlined />,
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
              <div className="shell__user">
                <Badge dot color={palette.success} offset={[-2, 28]}>
                  <Avatar size={30} style={{ background: palette.brand, fontWeight: 600 }}>
                    {initial}
                  </Avatar>
                </Badge>
                <Typography.Text style={{ fontSize: 13 }}>
                  {user?.fullName ?? user?.phone}
                </Typography.Text>
                <DownOutlined style={{ fontSize: 10, color: palette.inkMuted }} />
              </div>
            </Dropdown>
          </Flex>
        </Layout.Header>

        <Layout.Content style={{ padding: '24px 24px 40px' }}>
          <div style={{ maxWidth: 1280, margin: '0 auto' }}>
            <Outlet />
          </div>
        </Layout.Content>
      </Layout>
    </Layout>
  )
}
