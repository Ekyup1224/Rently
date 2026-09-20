import { useCallback, useMemo, useRef, useState } from 'react'
import {
  App as AntApp, Button, Card, Flex, Input, Modal, Select, Space, Tag, Typography,
} from 'antd'
import { AgGridReact } from 'ag-grid-react'
import type {
  ColDef, GridReadyEvent, ICellRendererParams, IDatasource, IGetRowsParams,
  GridApi, ValueFormatterParams,
} from 'ag-grid-community'
import dayjs from 'dayjs'
import { admin } from '../../api/endpoints'
import { RequestError } from '../../api/client'
import type { KycStatus, Role, User, UserStatus } from '../../types'
import { PageHead } from '../../ui/PageHead'
import { plural } from '../../ui/plural'

const PAGE_SIZE = 25

const STATUS_COLORS: Record<UserStatus, string> = {
  ACTIVE: 'green',
  PENDING_VERIFICATION: 'orange',
  SUSPENDED: 'red',
  DELETED: 'default',
}

const GRANTABLE_ROLES: Role[] = ['HOUSE_OWNER', 'HOTEL_MANAGER', 'HOTEL_STAFF', 'SUPER_ADMIN']

/**
 * The one fully wired screen in Step 1: it proves the whole stack end to end —
 * grid paging and sorting hitting the real paged endpoint, and role/status
 * mutations landing in the audit trail.
 *
 * <p>Uses AG Grid's infinite row model so rows are fetched a block at a time; the
 * table is expected to outgrow anything worth loading in one request.
 */
export function UsersPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const gridApi = useRef<GridApi<User> | null>(null)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<UserStatus | undefined>()
  const [role, setRole] = useState<Role | undefined>()
  const [total, setTotal] = useState<number | null>(null)
  const [selected, setSelected] = useState<User | null>(null)

  // Filters are read through a ref inside the datasource: AG Grid calls getRows
  // asynchronously, and a stale closure would silently query the old filters.
  const filters = useRef({ search, status, role })
  filters.current = { search, status, role }

  const datasource = useMemo<IDatasource>(() => ({
    getRows: async (params: IGetRowsParams) => {
      const page = Math.floor(params.startRow / PAGE_SIZE)
      const [sort] = params.sortModel
      try {
        const result = await admin.listUsers({
          q: filters.current.search || undefined,
          status: filters.current.status,
          role: filters.current.role,
          page,
          size: PAGE_SIZE,
          sort: sort ? `${sort.colId},${sort.sort}` : 'createdAt,desc',
        })
        setTotal(result.total)
        params.successCallback(result.rows, result.total)
      } catch (failure) {
        message.error(failure instanceof RequestError ? failure.message : 'Could not load users')
        params.failCallback()
      }
    },
  }), [message])

  const onGridReady = useCallback((event: GridReadyEvent<User>) => {
    gridApi.current = event.api
    event.api.setGridOption('datasource', datasource)
  }, [datasource])

  /** Re-runs the query from row zero. Called after a filter change or a mutation. */
  const reload = useCallback(() => {
    gridApi.current?.setGridOption('datasource', datasource)
  }, [datasource])

  const columns = useMemo<ColDef<User>[]>(() => [
    { field: 'phone', headerName: 'Phone', sortable: true, minWidth: 150 },
    { field: 'fullName', headerName: 'Name', sortable: true, minWidth: 160, valueFormatter: dash },
    { field: 'email', headerName: 'Email', sortable: true, minWidth: 200, valueFormatter: dash },
    {
      field: 'status',
      headerName: 'Status',
      sortable: true,
      flex: 0,
      width: 170,
      cellRenderer: ({ value }: ICellRendererParams<User, UserStatus>) =>
        value ? <Tag color={STATUS_COLORS[value]}>{value.replace('_', ' ').toLowerCase()}</Tag> : null,
    },
    {
      field: 'kycStatus',
      headerName: 'Identity',
      sortable: true,
      flex: 0,
      width: 120,
      cellRenderer: ({ value }: ICellRendererParams<User, KycStatus>) =>
        value && value !== 'NONE' ? <Tag>{value.toLowerCase()}</Tag> : <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      colId: 'roles',
      headerName: 'Roles',
      minWidth: 220,
      // Role grants come from a separate table, so this column cannot be sorted
      // server-side without a join that would multiply rows.
      sortable: false,
      valueGetter: ({ data }) => data?.roles.map((grant) => grant.role).join(', '),
      cellRenderer: ({ data }: ICellRendererParams<User>) => (
        <Space size={4} wrap>
          {data?.roles.map((grant) => (
            <Tag key={`${grant.role}:${grant.organizationId ?? ''}`}
                 color={grant.role === 'SUPER_ADMIN' ? 'purple' : undefined}>
              {grant.role}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      field: 'createdAt',
      headerName: 'Joined',
      sortable: true,
      sort: 'desc',
      flex: 0,
      width: 160,
      valueFormatter: ({ value }: ValueFormatterParams<User, string>) =>
        value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '',
    },
    {
      colId: 'actions',
      headerName: '',
      flex: 0,
      width: 100,
      pinned: 'right',
      sortable: false,
      cellRenderer: ({ data }: ICellRendererParams<User>) =>
        data ? <Button type="link" size="small" onClick={() => setSelected(data)}>Manage</Button> : null,
    },
  ], [])

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Flex align="baseline" justify="space-between" wrap gap={12}>
        <PageHead
        title="Users"
        description={'Every account, its roles and whether it can sign in.'}
      />
        {total !== null && (
          <Typography.Text type="secondary">{plural(total, 'matching account')}</Typography.Text>
        )}
      </Flex>

      <Card size="small">
        <Flex gap={12} wrap>
          <Input.Search
            allowClear
            placeholder="Phone, email or name"
            style={{ maxWidth: 280 }}
            onSearch={(value) => { setSearch(value); reload() }}
            onChange={(event) => { if (!event.target.value) { setSearch(''); } }}
          />
          <Select<UserStatus>
            allowClear
            placeholder="Any status"
            style={{ minWidth: 190 }}
            value={status}
            onChange={(value) => { setStatus(value); reload() }}
            options={(['ACTIVE', 'PENDING_VERIFICATION', 'SUSPENDED', 'DELETED'] as UserStatus[])
              .map((value) => ({ value, label: value.replace('_', ' ').toLowerCase() }))}
          />
          <Select<Role>
            allowClear
            placeholder="Any role"
            style={{ minWidth: 180 }}
            value={role}
            onChange={(value) => { setRole(value); reload() }}
            options={(['CLIENT', ...GRANTABLE_ROLES] as Role[]).map((value) => ({ value, label: value }))}
          />
          <Button onClick={reload}>Refresh</Button>
        </Flex>
      </Card>

      <div style={{ height: 'calc(100vh - 320px)', minHeight: 400 }}>
        <AgGridReact<User>
          columnDefs={columns}
          defaultColDef={{ resizable: true, flex: 1 }}
          rowModelType="infinite"
          cacheBlockSize={PAGE_SIZE}
          // One block of overscan is enough for smooth scrolling without
          // prefetching pages nobody looks at.
          maxBlocksInCache={10}
          onGridReady={onGridReady}
          getRowId={({ data }) => data.id}
          rowHeight={44}
        />
      </div>

      <ManageUserModal
        user={selected}
        onClose={() => setSelected(null)}
        onChanged={() => { setSelected(null); reload() }}
      />
    </Space>
  )
}

function dash({ value }: ValueFormatterParams<User, string | undefined>): string {
  return value ?? '—'
}

/** Status changes and role grants for one account. */
function ManageUserModal({
  user, onClose, onChanged,
}: { user: User | null; onClose: () => void; onChanged: () => void }) {
  const { message } = AntApp.useApp()

  const [busy, setBusy] = useState(false)
  const [roleToGrant, setRoleToGrant] = useState<Role | undefined>()
  const [organizationId, setOrganizationId] = useState('')

  const heldRoles = user?.roles.map((grant) => grant.role) ?? []
  const needsOrganization = roleToGrant === 'HOTEL_MANAGER' || roleToGrant === 'HOTEL_STAFF'

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true)
    try {
      await action()
      message.success(success)
      onChanged()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'The change failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open={user !== null}
      title={user ? `Manage ${user.fullName ?? user.phone}` : ''}
      onCancel={onClose}
      footer={null}
      destroyOnHidden
    >
      {user && (
        <Space orientation="vertical" size="large" style={{ width: '100%' }}>
          <div>
            <Typography.Text type="secondary">Status</Typography.Text>
            <Flex gap={8} style={{ marginTop: 8 }} wrap>
              {user.status !== 'ACTIVE' && (
                <Button
                  loading={busy}
                  onClick={() => run(
                    () => admin.changeStatus(user.id, 'ACTIVE', 'Reactivated from admin console'),
                    'Account reactivated',
                  )}
                >
                  Reactivate
                </Button>
              )}
              {user.status !== 'SUSPENDED' && (
                <Button
                  danger
                  loading={busy}
                  onClick={() => run(
                    () => admin.changeStatus(user.id, 'SUSPENDED', 'Suspended from admin console'),
                    'Account suspended and its sessions revoked',
                  )}
                >
                  Suspend
                </Button>
              )}
            </Flex>
          </div>

          <div>
            <Typography.Text type="secondary">Grant a role</Typography.Text>
            <Flex gap={8} style={{ marginTop: 8 }} wrap>
              <Select<Role>
                placeholder="Role"
                style={{ minWidth: 180 }}
                value={roleToGrant}
                onChange={setRoleToGrant}
                options={GRANTABLE_ROLES
                  .filter((value) => !heldRoles.includes(value))
                  .map((value) => ({ value, label: value }))}
              />
              {needsOrganization && (
                <Input
                  placeholder="Organization id"
                  style={{ minWidth: 260 }}
                  value={organizationId}
                  onChange={(event) => setOrganizationId(event.target.value)}
                />
              )}
              <Button
                type="primary"
                disabled={!roleToGrant || (needsOrganization && !organizationId.trim())}
                loading={busy}
                onClick={() => run(
                  () => admin.grantRole(user.id, roleToGrant!, needsOrganization ? organizationId.trim() : undefined),
                  'Role granted',
                )}
              >
                Grant
              </Button>
            </Flex>
            {needsOrganization && (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Hotel roles must be scoped to an organization. Organization management
                arrives with the hotel module in Step 3.
              </Typography.Text>
            )}
          </div>

          <div>
            <Typography.Text type="secondary">Current roles</Typography.Text>
            <Flex gap={8} style={{ marginTop: 8 }} wrap>
              {user.roles.map((grant) => (
                <Tag
                  key={`${grant.role}:${grant.organizationId ?? ''}`}
                  closable={grant.role !== 'CLIENT'}
                  onClose={(event) => {
                    event.preventDefault()
                    run(
                      () => admin.revokeRole(user.id, grant.role, grant.organizationId),
                      'Role revoked and sessions ended',
                    )
                  }}
                >
                  {grant.role}
                  {grant.organizationName ? ` · ${grant.organizationName}` : ''}
                </Tag>
              ))}
            </Flex>
          </div>
        </Space>
      )}
    </Modal>
  )
}
