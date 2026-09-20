import { useCallback, useMemo, useRef, useState } from 'react'
import { App as AntApp, Button, Card, Flex, Input, Space, Tag } from 'antd'
import { AgGridReact } from 'ag-grid-react'
import type {
  ColDef, GridApi, GridReadyEvent, ICellRendererParams, IDatasource, IGetRowsParams,
  ValueFormatterParams,
} from 'ag-grid-community'
import dayjs from 'dayjs'
import { admin } from '../../api/endpoints'
import { RequestError } from '../../api/client'
import type { AuditLogRow } from '../../types'
import { PageHead } from '../../ui/PageHead'

const PAGE_SIZE = 50

/** Read-only view of the append-only audit trail. */
export function AuditLogPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const gridApi = useRef<GridApi<AuditLogRow> | null>(null)
  const [action, setAction] = useState('')
  const [targetId, setTargetId] = useState('')

  const filters = useRef({ action, targetId })
  filters.current = { action, targetId }

  const datasource = useMemo<IDatasource>(() => ({
    getRows: async (params: IGetRowsParams) => {
      try {
        const result = await admin.listAuditLogs({
          action: filters.current.action || undefined,
          targetId: filters.current.targetId || undefined,
          page: Math.floor(params.startRow / PAGE_SIZE),
          size: PAGE_SIZE,
        })
        params.successCallback(result.rows, result.total)
      } catch (failure) {
        message.error(failure instanceof RequestError ? failure.message : 'Could not load the audit log')
        params.failCallback()
      }
    },
  }), [message])

  const onGridReady = useCallback((event: GridReadyEvent<AuditLogRow>) => {
    gridApi.current = event.api
    event.api.setGridOption('datasource', datasource)
  }, [datasource])

  const reload = useCallback(() => {
    gridApi.current?.setGridOption('datasource', datasource)
  }, [datasource])

  const columns = useMemo<ColDef<AuditLogRow>[]>(() => [
    {
      field: 'createdAt',
      headerName: 'When',
      // flex is off on fixed-width columns; otherwise defaultColDef's flex wins
      // and squeezes timestamps and long action names into ellipses.
      flex: 0,
      width: 180,
      valueFormatter: ({ value }: ValueFormatterParams<AuditLogRow, string>) =>
        value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '',
    },
    {
      field: 'action',
      headerName: 'Action',
      flex: 0,
      width: 230,
      cellRenderer: ({ value }: ICellRendererParams<AuditLogRow, string>) =>
        value ? <Tag>{value}</Tag> : null,
    },
    { field: 'targetType', headerName: 'Target', flex: 0, width: 120 },
    { field: 'targetId', headerName: 'Target id', minWidth: 250 },
    { field: 'actorId', headerName: 'Actor', minWidth: 250 },
    { field: 'ip', headerName: 'IP', flex: 0, width: 120 },
    {
      colId: 'metadata',
      headerName: 'Details',
      minWidth: 260,
      valueGetter: ({ data }) =>
        data && Object.keys(data.metadata ?? {}).length > 0 ? JSON.stringify(data.metadata) : '',
    },
  ], [])

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <PageHead
        title="Audit log"
        description={'Every consequential action, who took it and when. Read-only by design.'}
      />

      <Card size="small">
        <Flex gap={12} wrap>
          <Input
            allowClear
            placeholder="Action, e.g. USER_ROLE_GRANTED"
            style={{ maxWidth: 300 }}
            value={action}
            onChange={(event) => setAction(event.target.value)}
            onPressEnter={reload}
          />
          <Input
            allowClear
            placeholder="Target id"
            style={{ maxWidth: 300 }}
            value={targetId}
            onChange={(event) => setTargetId(event.target.value)}
            onPressEnter={reload}
          />
          <Button type="primary" onClick={reload}>Apply</Button>
        </Flex>
      </Card>

      <div style={{ height: 'calc(100vh - 300px)', minHeight: 400 }}>
        <AgGridReact<AuditLogRow>
          columnDefs={columns}
          defaultColDef={{ resizable: true, flex: 1, sortable: false }}
          rowModelType="infinite"
          cacheBlockSize={PAGE_SIZE}
          maxBlocksInCache={6}
          onGridReady={onGridReady}
          getRowId={({ data }) => data.id}
          rowHeight={40}
        />
      </div>
    </Space>
  )
}
