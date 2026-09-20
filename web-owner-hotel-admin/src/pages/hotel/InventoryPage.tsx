import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Checkbox, Flex, InputNumber, Select, Space, Spin, Switch,
  Typography,
} from 'antd'
import { AgGridReact } from 'ag-grid-react'
import type { ColDef, ICellRendererParams } from 'ag-grid-community'
import { useNavigate, useParams } from 'react-router-dom'
import dayjs, { type Dayjs } from 'dayjs'
import { RequestError } from '../../api/client'
import { hotels } from '../../api/endpoints'
import type { Hotel, InventoryNight, RoomTypeInventory } from '../../types'
import { formatMoney } from '../owner/listingFormat'
import { nightAppearance } from './hotelFormat'
import { PageHead } from '../../ui/PageHead'
import { plural } from '../../ui/plural'

const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const WEEKDAY_NAMES = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

/** One grid row: a room type, with its nights keyed by date. */
interface InventoryRow {
  roomTypeId: string
  name: string
  totalRooms: number
  status: string
  nights: Record<string, InventoryNight>
}

/**
 * Rates and availability as a date-by-room-type matrix.
 *
 * <p>This is the screen the build spec singles out for AG Grid, and it is the one
 * place a grid genuinely beats a calendar: a hotelier reads across a room type to
 * see a season and down a date to see the whole house, which needs pinned rows,
 * many columns and horizontal scrolling.
 *
 * <p>Cells show what is left, not what is sold — that is the number a hotel acts
 * on. Sold counts are maintained by the database from the reservations themselves
 * and are read-only everywhere, which is why editing happens through the panel
 * below rather than by typing into a cell: the real action is "this room type,
 * these dates, these weekdays", not one night at a time.
 */
export function InventoryPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const { hotelId } = useParams<{ hotelId: string }>()
  const navigate = useNavigate()

  const [hotel, setHotel] = useState<Hotel | null>(null)
  const [matrix, setMatrix] = useState<RoomTypeInventory[] | null>(null)
  const [start, setStart] = useState<Dayjs>(dayjs().startOf('day'))
  const [span, setSpan] = useState(21)
  const [reloadToken, setReloadToken] = useState(0)
  const [busy, setBusy] = useState(false)

  const [roomTypeId, setRoomTypeId] = useState<string | undefined>()
  const [from, setFrom] = useState<string | null>(null)
  const [to, setTo] = useState<string | null>(null)
  const [weekdays, setWeekdays] = useState<string[]>([])
  const [available, setAvailable] = useState<number | null>(null)
  const [rate, setRate] = useState<number | null>(null)
  const [minStay, setMinStay] = useState<number | null>(null)
  const [stopSell, setStopSell] = useState<boolean | null>(null)

  const rangeStart = start.format('YYYY-MM-DD')
  const rangeEnd = start.add(span - 1, 'day').format('YYYY-MM-DD')

  useEffect(() => {
    if (!hotelId) {
      return
    }
    let cancelled = false

    async function load() {
      try {
        const [loadedHotel, loadedMatrix] = await Promise.all([
          hotels.get(hotelId!),
          hotels.inventory(hotelId!, rangeStart, rangeEnd),
        ])
        if (!cancelled) {
          setHotel(loadedHotel)
          setMatrix(loadedMatrix)
        }
      } catch (failure) {
        if (!cancelled) {
          setMatrix([])
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load inventory')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [hotelId, rangeStart, rangeEnd, reloadToken, message])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  const dates = useMemo(
    () => Array.from({ length: span }, (_, index) => start.add(index, 'day').format('YYYY-MM-DD')),
    [start, span],
  )

  const rows = useMemo<InventoryRow[]>(() => (matrix ?? []).map((roomType) => {
    const nights: Record<string, InventoryNight> = {}
    roomType.nights.forEach((night) => {
      nights[night.date] = night
    })
    return {
      roomTypeId: roomType.roomTypeId,
      name: roomType.name,
      totalRooms: roomType.totalRooms,
      status: roomType.status,
      nights,
    }
  }), [matrix])

  /** Clicking a cell prefills the panel with that room type and that night. */
  const selectNight = useCallback((row: InventoryRow, date: string) => {
    const night = row.nights[date]
    setRoomTypeId(row.roomTypeId)
    setFrom(date)
    setTo(date)
    setWeekdays([])
    setAvailable(night ? night.available : row.totalRooms)
    setRate(night?.overridden ? night.rate : null)
    setMinStay(night?.minStay ?? null)
    setStopSell(night?.stopSell ?? false)
  }, [])

  const columns = useMemo<ColDef<InventoryRow>[]>(() => {
    const roomTypeColumn: ColDef<InventoryRow> = {
      headerName: 'Room type',
      field: 'name',
      pinned: 'left',
      width: 200,
      cellRenderer: ({ data }: ICellRendererParams<InventoryRow>) => data ? (
        <Flex vertical gap={2} style={{ lineHeight: 1.3, paddingTop: 4 }}>
          <Typography.Text strong style={{ fontSize: 13 }}>{data.name}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {data.totalRooms} room{data.totalRooms === 1 ? '' : 's'}
            {data.status === 'INACTIVE' ? ' · not on sale' : ''}
          </Typography.Text>
        </Flex>
      ) : null,
    }

    const dateColumns: ColDef<InventoryRow>[] = dates.map((date) => {
      const day = dayjs(date)
      const isWeekend = day.day() === 0 || day.day() === 6
      return {
        colId: date,
        headerName: day.format('ddd D MMM'),
        width: 92,
        sortable: false,
        headerClass: isWeekend ? 'inventory-weekend-header' : undefined,
        valueGetter: ({ data }) => data?.nights[date]?.remaining ?? null,
        cellRenderer: ({ data }: ICellRendererParams<InventoryRow>) => {
          const night = data?.nights[date]
          if (!data || !night) {
            return null
          }
          const look = nightAppearance(night)
          return (
            <button
              type="button"
              onClick={() => selectNight(data, date)}
              title={`${night.remaining} of ${night.available} left`
                + ` · ${night.booked} sold${night.stopSell ? ' · closed' : ''}`
                + (night.minStay ? ` · min ${night.minStay} nights` : '')}
              style={{
                width: '100%', height: '100%', border: `1px solid ${look.border}`,
                background: look.background, borderRadius: 6, cursor: 'pointer',
                font: 'inherit', padding: '2px 4px', textAlign: 'center', lineHeight: 1.25,
              }}
            >
              <div style={{ fontWeight: 600, fontSize: 13 }}>
                {night.stopSell ? '—' : night.remaining}
                <span style={{ fontWeight: 400, opacity: 0.55, fontSize: 11 }}>
                  /{night.available}
                </span>
              </div>
              <div style={{ fontSize: 10, opacity: 0.7 }}>
                {(night.rate / 1000).toFixed(0)}k{night.overridden ? '*' : ''}
              </div>
            </button>
          )
        },
      }
    })

    return [roomTypeColumn, ...dateColumns]
  }, [dates, selectNight])

  async function apply() {
    if (!roomTypeId || !from) {
      return
    }
    setBusy(true)
    try {
      const result = await hotels.updateInventory(roomTypeId, {
        from,
        to: to ?? from,
        weekdays: weekdays.length > 0 ? weekdays : undefined,
        availableCount: available ?? undefined,
        rate: rate ?? undefined,
        clearRate: rate === null,
        stopSell: stopSell ?? undefined,
        minStayNights: minStay ?? undefined,
        clearMinStay: minStay === null,
      })
      message.success(`${plural(result.nightsUpdated, 'night')} updated`)
      reload()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not save')
    } finally {
      setBusy(false)
    }
  }

  async function reset() {
    if (!roomTypeId || !from) {
      return
    }
    setBusy(true)
    try {
      const result = await hotels.clearInventory(roomTypeId, from, to ?? from)
      message.success(`${plural(result.nightsCleared, 'night')} reset to defaults`)
      reload()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not reset')
    } finally {
      setBusy(false)
    }
  }

  if (!hotel || matrix === null) {
    return <Flex justify="center" style={{ padding: 64 }}><Spin size="large" /></Flex>
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <style>{`.inventory-weekend-header { background: #fafafa; }`}</style>

      <Flex align="center" justify="space-between" wrap gap={12}>
        <Space>
          <Button onClick={() => navigate('/hotel/hotels')}>Back</Button>
          <PageHead
        title="Inventory"
        description={'Rooms on sale and their rates, night by night.'}
      />
        </Space>
        <Space wrap>
          <Button onClick={() => setStart(start.subtract(span, 'day'))}>←</Button>
          <Typography.Text strong>
            {start.format('D MMM')} – {start.add(span - 1, 'day').format('D MMM YYYY')}
          </Typography.Text>
          <Button onClick={() => setStart(start.add(span, 'day'))}>→</Button>
          <Select
            value={span}
            style={{ width: 110 }}
            onChange={setSpan}
            options={[{ value: 14, label: '2 weeks' }, { value: 21, label: '3 weeks' },
                      { value: 30, label: '30 days' }, { value: 60, label: '60 days' }]}
          />
          <Button onClick={() => setStart(dayjs().startOf('day'))}>Today</Button>
        </Space>
      </Flex>

      {hotel.roomTypes.length === 0 && (
        <Alert
          type="warning"
          showIcon
          message="No room types yet"
          description="A hotel sells room types, not itself. Add one on the hotel editor before setting rates."
          action={<Button size="small" onClick={() => navigate(`/hotel/hotels/${hotel.id}`)}>
            Add a room type
          </Button>}
        />
      )}

      {hotel.status !== 'APPROVED' && hotel.roomTypes.length > 0 && (
        <Alert type="info" showIcon
               message="This hotel is not live yet, so nothing can be booked"
               description="You can still set rates and availability ahead of approval." />
      )}

      <Card size="small" styles={{ body: { padding: 8 } }}>
        <div style={{ height: Math.min(520, 120 + rows.length * 56) }}>
          <AgGridReact<InventoryRow>
            columnDefs={columns}
            rowData={rows}
            defaultColDef={{ resizable: false, sortable: false, suppressMovable: true }}
            getRowId={({ data }) => data.roomTypeId}
            rowHeight={54}
            headerHeight={40}
            suppressCellFocus
          />
        </div>

        <Flex gap={16} wrap style={{ marginTop: 10, paddingInline: 4 }}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Each cell shows <strong>rooms left / offered</strong> and the nightly rate
            (* = override). Click one to edit.
          </Typography.Text>
          {['Open', 'Nearly full', 'Sold out', 'Closed'].map((label) => {
            const sample = nightAppearance({
              date: '', available: 4, booked: label === 'Sold out' ? 4 : 0,
              remaining: label === 'Sold out' ? 0 : label === 'Nearly full' ? 1 : 4,
              rate: 0, overridden: false, stopSell: label === 'Closed', minStay: undefined,
            })
            return (
              <Flex key={label} align="center" gap={6}>
                <span style={{ width: 12, height: 12, borderRadius: 3,
                               background: sample.background,
                               border: `1px solid ${sample.border}` }} />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>{label}</Typography.Text>
              </Flex>
            )
          })}
        </Flex>
      </Card>

      <Card
        size="small"
        title={roomTypeId
          ? `Editing ${rows.find((row) => row.roomTypeId === roomTypeId)?.name ?? ''}`
          : 'Select a night, or pick a room type to edit a range'}
      >
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Flex gap={16} wrap align="flex-end">
            <Flex vertical gap={4}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>Room type</Typography.Text>
              <Select
                placeholder="Room type"
                style={{ minWidth: 200 }}
                value={roomTypeId}
                onChange={setRoomTypeId}
                options={rows.map((row) => ({ value: row.roomTypeId, label: row.name }))}
              />
            </Flex>
            <Flex vertical gap={4}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>From</Typography.Text>
              <input type="date" value={from ?? ''} onChange={(event) => setFrom(event.target.value)}
                     style={{ padding: '5px 8px', border: '1px solid #d9d9d9', borderRadius: 6 }} />
            </Flex>
            <Flex vertical gap={4}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                To (inclusive)
              </Typography.Text>
              <input type="date" value={to ?? ''} min={from ?? undefined}
                     onChange={(event) => setTo(event.target.value)}
                     style={{ padding: '5px 8px', border: '1px solid #d9d9d9', borderRadius: 6 }} />
            </Flex>
          </Flex>

          <Flex gap={20} wrap align="flex-end">
            <Flex vertical gap={4}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Rooms on sale
              </Typography.Text>
              <InputNumber
                min={0}
                max={rows.find((row) => row.roomTypeId === roomTypeId)?.totalRooms}
                style={{ width: 130 }}
                value={available ?? undefined}
                onChange={(value) => setAvailable(value ?? null)}
              />
            </Flex>
            <Flex vertical gap={4}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Rate per night (₮)
              </Typography.Text>
              <InputNumber
                min={0} step={10000} style={{ width: 150 }} placeholder="Base rate"
                value={rate ?? undefined}
                onChange={(value) => setRate(value ?? null)}
              />
            </Flex>
            <Flex vertical gap={4}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Minimum nights
              </Typography.Text>
              <InputNumber min={1} style={{ width: 130 }} placeholder="None"
                           value={minStay ?? undefined}
                           onChange={(value) => setMinStay(value ?? null)} />
            </Flex>
            <Flex vertical gap={4}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Closed to new bookings
              </Typography.Text>
              <Switch checked={stopSell ?? false} onChange={setStopSell} />
            </Flex>
          </Flex>

          <div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Only these weekdays (empty means every day) — for weekend rates
            </Typography.Text>
            <Checkbox.Group
              style={{ marginTop: 6 }}
              value={weekdays}
              onChange={(values) => setWeekdays(values as string[])}
              options={WEEKDAY_NAMES.map((name, index) => ({
                value: name, label: WEEKDAY_LABELS[index],
              }))}
            />
          </div>

          <Flex gap={8} wrap align="center">
            <Button type="primary" loading={busy} disabled={!roomTypeId || !from}
                    onClick={apply}>
              Apply
            </Button>
            <Button loading={busy} disabled={!roomTypeId || !from} onClick={reset}>
              Reset to defaults
            </Button>
            {hotel.roomTypes.length > 0 && (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Base rates come from the room type
                {rows.length > 0 && `: ${rows.map((row) =>
                  `${row.name} ${formatMoney(
                    matrix.find((entry) => entry.roomTypeId === row.roomTypeId)?.basePrice,
                    hotel.currency)}`).join(' · ')}`}
              </Typography.Text>
            )}
          </Flex>

          <Alert
            type="info"
            showIcon
            message="Rooms already sold cannot be closed"
            description="Reducing what is on sale below the rooms booked for a night is refused —
              those stays are already paid for. Close the nights that are still open instead."
          />
        </Space>
      </Card>
    </Space>
  )
}
