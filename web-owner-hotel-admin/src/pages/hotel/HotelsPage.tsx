import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Empty, Flex, Form, Image, Input, Modal, Select, Space,
  Spin, Tag, Typography,
} from 'antd'
import { useNavigate } from 'react-router-dom'
import { RequestError } from '../../api/client'
import { hotels } from '../../api/endpoints'
import type { Hotel, SupplyStatus } from '../../types'
import { formatMoney } from '../owner/listingFormat'
import { HOTEL_STATUS_COLORS, HOTEL_STATUS_LABELS } from './hotelFormat'
import { PageHead } from '../../ui/PageHead'

/** The hotels a manager runs, or the ones a staff account is assigned to. */
export function HotelsPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const navigate = useNavigate()
  const [list, setList] = useState<Hotel[] | null>(null)
  const [status, setStatus] = useState<SupplyStatus | undefined>()
  const [creating, setCreating] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const page = await hotels.list({ status, page: 0, size: 50 })
        if (!cancelled) {
          setList(page.rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setList([])
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load your hotels')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [status, reloadToken, message])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  async function act(hotel: Hotel, action: () => Promise<unknown>, success: string) {
    setBusyId(hotel.id)
    try {
      await action()
      message.success(success)
      reload()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'That did not work')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Flex align="center" justify="space-between" wrap gap={12}>
        <PageHead
        title="Hotels"
        description={'The properties you manage, their room types and how full they are.'}
      />
        <Space>
          <Select<SupplyStatus>
            allowClear
            placeholder="Any status"
            style={{ minWidth: 180 }}
            value={status}
            onChange={setStatus}
            options={(Object.keys(HOTEL_STATUS_LABELS) as SupplyStatus[])
              .map((value) => ({ value, label: HOTEL_STATUS_LABELS[value] }))}
          />
          <Button type="primary" onClick={() => setCreating(true)}>New hotel</Button>
        </Space>
      </Flex>

      {list === null && <Flex justify="center" style={{ padding: 48 }}><Spin /></Flex>}
      {list?.length === 0 && (
        <Card>
          <Empty description="No hotels yet">
            <Button type="primary" onClick={() => setCreating(true)}>Add your first hotel</Button>
          </Empty>
        </Card>
      )}

      {list?.map((hotel) => {
        const sellableRooms = hotel.roomTypes.filter((room) => room.status === 'ACTIVE').length
        return (
          <Card key={hotel.id} size="small">
            <Flex gap={16} wrap>
              <div style={{ width: 160, flexShrink: 0 }}>
                {hotel.photos[0]
                  ? <Image src={hotel.photos[0].url} alt="" width={160} height={110}
                           style={{ objectFit: 'cover', borderRadius: 8 }} />
                  : <Flex align="center" justify="center"
                          style={{ width: 160, height: 110, background: '#fafafa',
                                   border: '1px dashed #d9d9d9', borderRadius: 8 }}>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        No photos
                      </Typography.Text>
                    </Flex>}
              </div>

              <Flex vertical flex={1} gap={4} style={{ minWidth: 240 }}>
                <Flex gap={8} align="center" wrap>
                  <Typography.Text strong>{hotel.name}</Typography.Text>
                  <Tag color={HOTEL_STATUS_COLORS[hotel.status]}>
                    {HOTEL_STATUS_LABELS[hotel.status]}
                  </Tag>
                  {hotel.starRating && <Tag>{'★'.repeat(hotel.starRating)}</Tag>}
                </Flex>
                <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                  {hotel.district ? `${hotel.district}, ` : ''}{hotel.city} ·
                  {' '}{sellableRooms} room type{sellableRooms === 1 ? '' : 's'} on sale
                </Typography.Text>
                {hotel.roomTypes.length > 0 && (
                  <Typography.Text style={{ fontSize: 13 }}>
                    from {formatMoney(Math.min(...hotel.roomTypes
                      .filter((room) => room.status === 'ACTIVE')
                      .map((room) => room.basePrice)), hotel.currency)} / night
                  </Typography.Text>
                )}

                {hotel.status === 'REJECTED' && hotel.rejectionReason && (
                  <Alert type="error" showIcon style={{ marginTop: 4 }}
                         message="Not approved" description={hotel.rejectionReason} />
                )}
                {(hotel.status === 'DRAFT' || hotel.status === 'REJECTED')
                  && hotel.readinessProblems.length > 0 && (
                  <Typography.Text type="warning" style={{ fontSize: 12 }}>
                    Before submitting: {hotel.readinessProblems.join('; ')}
                  </Typography.Text>
                )}
              </Flex>

              <Flex vertical gap={6} style={{ minWidth: 160 }}>
                <Button block onClick={() => navigate(`/hotel/hotels/${hotel.id}`)}>
                  Edit
                </Button>
                <Button block onClick={() => navigate(`/hotel/hotels/${hotel.id}/inventory`)}>
                  Rates & inventory
                </Button>
                <Button block onClick={() => navigate(`/hotel/hotels/${hotel.id}/reservations`)}>
                  Front desk
                </Button>
                <Button block onClick={() => navigate(`/hotel/hotels/${hotel.id}/payouts`)}>
                  Payouts
                </Button>

                {(hotel.status === 'DRAFT' || hotel.status === 'REJECTED') && (
                  <Button
                    block type="primary" loading={busyId === hotel.id}
                    disabled={hotel.readinessProblems.length > 0}
                    onClick={() => act(hotel, () => hotels.submit(hotel.id),
                      'Submitted for review')}
                  >
                    Submit for review
                  </Button>
                )}
                {hotel.status === 'APPROVED' && (
                  <Button block loading={busyId === hotel.id}
                          onClick={() => act(hotel, () => hotels.pause(hotel.id),
                            'Hotel paused')}>
                    Pause
                  </Button>
                )}
                {hotel.status === 'PAUSED' && (
                  <Button block type="primary" loading={busyId === hotel.id}
                          onClick={() => act(hotel, () => hotels.resume(hotel.id),
                            'Hotel is live again')}>
                    Resume
                  </Button>
                )}
              </Flex>
            </Flex>
          </Card>
        )
      })}

      <CreateHotelModal open={creating} onClose={() => setCreating(false)}
                        onCreated={(hotel) => navigate(`/hotel/hotels/${hotel.id}`)} />
    </Space>
  )
}

/** Starts a hotel with the essentials; the rest is filled in on the editor. */
function CreateHotelModal({
  open, onClose, onCreated,
}: { open: boolean; onClose: () => void; onCreated: (hotel: Hotel) => void }) {
  const { message } = AntApp.useApp()

  const [busy, setBusy] = useState(false)

  async function submit(values: { name: string; city: string }) {
    setBusy(true)
    try {
      onCreated(await hotels.create(values))
    } catch (failure) {
      message.error(failure instanceof RequestError
        ? failure.message : 'Could not create the hotel')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title="New hotel" onCancel={onClose} okText="Create draft"
           okButtonProps={{ loading: busy }}
           onOk={() => document.getElementById('create-hotel-submit')?.click()}
           destroyOnHidden>
      <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
        Just the basics. You will add room types, photos and rates next — a hotel needs
        at least one room type on sale before it can go live.
      </Typography.Paragraph>
      <Form layout="vertical" onFinish={submit} initialValues={{ city: 'Ulaanbaatar' }}>
        <Form.Item label="Hotel name" name="name" rules={[{ required: true }]}>
          <Input placeholder="Blue Sky Hotel" maxLength={180} />
        </Form.Item>
        <Form.Item label="City" name="city" rules={[{ required: true }]}>
          <Input maxLength={120} />
        </Form.Item>
        <button id="create-hotel-submit" type="submit" hidden />
      </Form>
    </Modal>
  )
}
