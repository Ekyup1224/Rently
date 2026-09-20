import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Checkbox, Col, Flex, Form, Input, InputNumber,
  Popconfirm, Row, Select, Space, Spin, Switch, Table, Tabs, Tag, TimePicker, Typography,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { RequestError } from '../../api/client'
import { hotels, putToStorage } from '../../api/endpoints'
import type { CancellationPolicy, Hotel, RoomType } from '../../types'
import { formatMoney } from '../owner/listingFormat'
import {
  HOTEL_AMENITY_GROUPS, HOTEL_STATUS_COLORS, HOTEL_STATUS_LABELS, ROOM_AMENITY_GROUPS,
  ROOM_TYPE_STATUS_COLORS,
} from './hotelFormat'

const CANCELLATION_POLICIES: { value: CancellationPolicy; label: string }[] = [
  { value: 'FLEXIBLE', label: 'Flexible — full refund until 24h before' },
  { value: 'MODERATE', label: 'Moderate — full refund until 5 days before' },
  { value: 'STRICT', label: 'Strict — 50% until 7 days before' },
]

/**
 * The hotel editor.
 *
 * <p>Split into tabs because a hotel is four separate jobs: the property itself,
 * the room types it sells, its photos, and who works the desk. A hotel cannot go
 * live without at least one room type on sale, which the readiness banner says
 * plainly rather than leaving the owner to discover on submission.
 */
export function HotelEditorPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const { hotelId } = useParams<{ hotelId: string }>()
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [hotel, setHotel] = useState<Hotel | null>(null)
  const [saving, setSaving] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    if (!hotelId) {
      return
    }
    let cancelled = false

    async function load() {
      try {
        const loaded = await hotels.get(hotelId!)
        if (!cancelled) {
          setHotel(loaded)
          form.setFieldsValue({
            ...loaded,
            checkInFrom: loaded.checkInFrom ? dayjs(loaded.checkInFrom, 'HH:mm:ss') : undefined,
            checkOutBy: loaded.checkOutBy ? dayjs(loaded.checkOutBy, 'HH:mm:ss') : undefined,
          })
        }
      } catch (failure) {
        if (!cancelled) {
          message.error(failure instanceof RequestError ? failure.message : 'Hotel not found')
          navigate('/hotel/hotels')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [hotelId, form, navigate, reloadToken, message])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  async function save(values: Record<string, unknown>) {
    if (!hotelId) {
      return
    }
    setSaving(true)
    try {
      const updated = await hotels.update(hotelId, {
        ...values,
        checkInFrom: values.checkInFrom
          ? (values.checkInFrom as dayjs.Dayjs).format('HH:mm:ss') : undefined,
        checkOutBy: values.checkOutBy
          ? (values.checkOutBy as dayjs.Dayjs).format('HH:mm:ss') : undefined,
      })
      setHotel(updated)
      message.success(updated.status === 'PENDING_REVIEW' && hotel?.status === 'APPROVED'
        ? 'Saved. Because the address or location changed, the hotel is back under review.'
        : 'Saved')
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not save')
    } finally {
      setSaving(false)
    }
  }

  if (!hotel) {
    return <Flex justify="center" style={{ padding: 64 }}><Spin size="large" /></Flex>
  }

  const submittable = hotel.status === 'DRAFT' || hotel.status === 'REJECTED'
  const ready = hotel.readinessProblems.length === 0

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%', maxWidth: 1000 }}>
      <Flex align="center" justify="space-between" wrap gap={12}>
        <Space>
          <Button onClick={() => navigate('/hotel/hotels')}>Back</Button>
          <Typography.Title level={3} style={{ margin: 0 }}>{hotel.name}</Typography.Title>
          <Tag color={HOTEL_STATUS_COLORS[hotel.status]}>
            {HOTEL_STATUS_LABELS[hotel.status]}
          </Tag>
        </Space>
        <Space>
          <Button onClick={() => navigate(`/hotel/hotels/${hotel.id}/inventory`)}>
            Rates & inventory
          </Button>
          {submittable && (
            <Button type="primary" disabled={!ready}
                    onClick={async () => {
                      try {
                        setHotel(await hotels.submit(hotel.id))
                        message.success('Submitted for review')
                      } catch (failure) {
                        message.error(failure instanceof RequestError
                          ? failure.message : 'Could not submit')
                      }
                    }}>
              Submit for review
            </Button>
          )}
        </Space>
      </Flex>

      {hotel.status === 'REJECTED' && hotel.rejectionReason && (
        <Alert type="error" showIcon message="Not approved"
               description={hotel.rejectionReason} />
      )}
      {submittable && !ready && (
        <Alert type="warning" showIcon message="Not ready to submit yet"
               description={<ul style={{ margin: 0, paddingInlineStart: 20 }}>
                 {hotel.readinessProblems.map((problem) => <li key={problem}>{problem}</li>)}
               </ul>} />
      )}
      {hotel.status === 'SUSPENDED' && (
        <Alert type="error" showIcon message="This hotel is suspended"
               description="Contact platform support. It cannot be edited while suspended." />
      )}

      <Tabs
        items={[
          {
            key: 'details',
            label: 'Hotel details',
            children: (
              <Form form={form} layout="vertical" onFinish={save}
                    disabled={hotel.status === 'SUSPENDED'}>
                <Card size="small" title="The basics">
                  <Form.Item label="Name" name="name" rules={[{ required: true }]}>
                    <Input maxLength={180} />
                  </Form.Item>
                  <Form.Item label="Description" name="description"
                             extra="What guests see on the hotel page. Required before submitting.">
                    <Input.TextArea rows={5} maxLength={8000} showCount />
                  </Form.Item>
                  <Row gutter={12}>
                    <Col xs={12} md={8}>
                      <Form.Item label="Star rating" name="starRating"
                                 extra="Leave empty if unrated">
                        <Select allowClear options={[1, 2, 3, 4, 5].map((value) => ({
                          value, label: '★'.repeat(value),
                        }))} />
                      </Form.Item>
                    </Col>
                    <Col xs={12} md={16}>
                      <Form.Item label="Cancellation policy" name="cancellationPolicy">
                        <Select options={CANCELLATION_POLICIES} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={12}>
                    <Col xs={12}>
                      <Form.Item label="Check-in from" name="checkInFrom">
                        <TimePicker format="HH:mm" minuteStep={30} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={12}>
                      <Form.Item label="Check-out by" name="checkOutBy">
                        <TimePicker format="HH:mm" minuteStep={30} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Form.Item label="Policies" name="policies"
                             extra="Deposits, ID requirements, extra beds — shown on the hotel page.">
                    <Input.TextArea rows={3} maxLength={4000} />
                  </Form.Item>
                </Card>

                <Card size="small" title="Where it is" style={{ marginTop: 12 }}>
                  <Alert type="info" showIcon style={{ marginBottom: 12 }}
                         message="Changing the address or coordinates of a live hotel sends it back for review." />
                  <Form.Item label="Street address" name="addressLine"
                             extra="Shown publicly — a hotel is a business at a published address.">
                    <Input maxLength={255} />
                  </Form.Item>
                  <Row gutter={12}>
                    <Col xs={12}>
                      <Form.Item label="District" name="district"><Input maxLength={120} /></Form.Item>
                    </Col>
                    <Col xs={12}>
                      <Form.Item label="City" name="city" rules={[{ required: true }]}>
                        <Input maxLength={120} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Row gutter={12}>
                    <Col xs={12}>
                      <Form.Item label="Latitude" name="latitude">
                        <InputNumber min={-90} max={90} step={0.0001} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={12}>
                      <Form.Item label="Longitude" name="longitude">
                        <InputNumber min={-180} max={180} step={0.0001} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                  </Row>
                </Card>

                <Card size="small" title="Hotel facilities" style={{ marginTop: 12 }}
                      extra={<Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        What the building offers. Room features are set per room type.
                      </Typography.Text>}>
                  <Form.Item name="amenities" noStyle>
                    <Checkbox.Group style={{ width: '100%' }}>
                      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
                        {HOTEL_AMENITY_GROUPS.map((group) => (
                          <div key={group.label}>
                            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                              {group.label}
                            </Typography.Text>
                            <Flex gap={8} wrap style={{ marginTop: 6 }}>
                              {group.amenities.map((amenity) => (
                                <Checkbox key={amenity} value={amenity}>
                                  {amenity.toLowerCase().replace(/_/g, ' ')}
                                </Checkbox>
                              ))}
                            </Flex>
                          </div>
                        ))}
                      </Space>
                    </Checkbox.Group>
                  </Form.Item>
                </Card>

                <Flex justify="flex-end" style={{ marginTop: 16 }}>
                  <Button type="primary" htmlType="submit" loading={saving} size="large">
                    Save changes
                  </Button>
                </Flex>
              </Form>
            ),
          },
          {
            key: 'rooms',
            label: `Room types (${hotel.roomTypes.length})`,
            children: <RoomTypesTab hotel={hotel} onChanged={reload} />,
          },
          {
            key: 'photos',
            label: `Photos (${hotel.photos.length})`,
            children: <HotelPhotosTab hotel={hotel} onChanged={reload} />,
          },
          {
            key: 'staff',
            label: 'Front-desk staff',
            children: <StaffTab hotelId={hotel.id} />,
          },
        ]}
      />
    </Space>
  )
}

/** Room types: what a guest actually books. */
function RoomTypesTab({ hotel, onChanged }: { hotel: Hotel; onChanged: () => void }) {
  const { message } = AntApp.useApp()

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<RoomType | null>(null)
  const [busy, setBusy] = useState(false)
  const [form] = Form.useForm()

  async function create(values: {
    name: string; capacity: number; totalRooms: number; basePrice: number
  }) {
    setBusy(true)
    try {
      await hotels.createRoomType(hotel.id, values)
      message.success('Room type added')
      setCreating(false)
      form.resetFields()
      onChanged()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not add')
    } finally {
      setBusy(false)
    }
  }

  async function remove(roomType: RoomType) {
    setBusy(true)
    try {
      await hotels.deleteRoomType(roomType.id)
      message.success('Room type deleted')
      onChanged()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not delete')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="A hotel sells room types, not itself"
        description="Each room type has a number of physical rooms, which is the default
          availability for any night you have not changed. A hotel needs at least one active
          room type with a price before it can go live."
      />

      <Table<RoomType>
        size="small"
        rowKey="id"
        pagination={false}
        dataSource={hotel.roomTypes}
        locale={{ emptyText: 'No room types yet' }}
        columns={[
          {
            title: 'Room type',
            dataIndex: 'name',
            render: (name: string, room) => (
              <Flex vertical gap={2}>
                <Typography.Text strong>{name}</Typography.Text>
                {room.bedConfig && (
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {room.bedConfig}
                  </Typography.Text>
                )}
              </Flex>
            ),
          },
          { title: 'Sleeps', dataIndex: 'capacity', width: 90 },
          { title: 'Rooms', dataIndex: 'totalRooms', width: 90 },
          {
            title: 'Base rate',
            dataIndex: 'basePrice',
            width: 130,
            render: (price: number) => formatMoney(price, hotel.currency),
          },
          {
            title: 'Status',
            dataIndex: 'status',
            width: 110,
            render: (status: RoomType['status']) => (
              <Tag color={ROOM_TYPE_STATUS_COLORS[status]}>{status.toLowerCase()}</Tag>
            ),
          },
          {
            title: '',
            width: 170,
            render: (_, room) => (
              <Space size={4}>
                <Button type="link" size="small" onClick={() => setEditing(room)}>Edit</Button>
                <Popconfirm
                  title="Delete this room type?"
                  description="Only possible if it has never been booked."
                  okButtonProps={{ danger: true }}
                  onConfirm={() => remove(room)}
                >
                  <Button type="text" size="small" danger disabled={busy}>Delete</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      {!creating && (
        <Button type="primary" onClick={() => setCreating(true)}>Add a room type</Button>
      )}

      {creating && (
        <Card size="small" title="New room type">
          <Form form={form} layout="inline" onFinish={create}
                initialValues={{ capacity: 2, totalRooms: 1 }}>
            <Form.Item label="Name" name="name" rules={[{ required: true }]}>
              <Input placeholder="Standard double" style={{ width: 200 }} maxLength={120} />
            </Form.Item>
            <Form.Item label="Sleeps" name="capacity" rules={[{ required: true }]}
                       tooltip="Guests per room, not per booking">
              <InputNumber min={1} max={20} style={{ width: 90 }} />
            </Form.Item>
            <Form.Item label="Rooms" name="totalRooms" rules={[{ required: true }]}
                       tooltip="How many of this room the hotel physically has">
              <InputNumber min={1} max={2000} style={{ width: 100 }} />
            </Form.Item>
            <Form.Item label="Rate (₮)" name="basePrice" rules={[{ required: true }]}>
              <InputNumber min={0} step={10000} style={{ width: 130 }} />
            </Form.Item>
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" loading={busy}>Add</Button>
                <Button onClick={() => setCreating(false)}>Cancel</Button>
              </Space>
            </Form.Item>
          </Form>
        </Card>
      )}

      {editing && (
        <RoomTypeEditor
          roomType={editing}
          currency={hotel.currency}
          onClose={() => setEditing(null)}
          onChanged={() => { setEditing(null); onChanged() }}
        />
      )}
    </Space>
  )
}

/** Full detail for one room type, including its own photos. */
function RoomTypeEditor({
  roomType, currency, onClose, onChanged,
}: { roomType: RoomType; currency: string; onClose: () => void; onChanged: () => void }) {
  const { message } = AntApp.useApp()

  const [form] = Form.useForm()
  const [busy, setBusy] = useState(false)
  const [photos, setPhotos] = useState(roomType.photos)

  useEffect(() => {
    form.setFieldsValue(roomType)
    setPhotos(roomType.photos)
  }, [roomType, form])

  async function save(values: Record<string, unknown>) {
    setBusy(true)
    try {
      await hotels.updateRoomType(roomType.id, values)
      message.success('Room type saved')
      onChanged()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not save')
    } finally {
      setBusy(false)
    }
  }

  async function uploadPhoto(file: File) {
    setBusy(true)
    try {
      const presigned = await hotels.roomTypePhotoUploadUrl(roomType.id,
        { contentType: file.type, sizeBytes: file.size })
      await putToStorage(presigned.uploadUrl, file)
      const photo = await hotels.confirmRoomTypePhoto(roomType.id,
        { storageKey: presigned.storageKey, altText: file.name })
      setPhotos((current) => [...current, photo])
      message.success('Photo added')
    } catch (failure) {
      message.error(failure instanceof RequestError
        ? failure.message : 'The upload did not complete')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card size="small" title={`Editing ${roomType.name}`}
          extra={<Button size="small" onClick={onClose}>Close</Button>}>
      <Form form={form} layout="vertical" onFinish={save}>
        <Row gutter={12}>
          <Col xs={24} md={12}>
            <Form.Item label="Name" name="name"><Input maxLength={120} /></Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item label="Sleeps per room" name="capacity">
              <InputNumber min={1} max={20} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item label="Rooms of this type" name="totalRooms"
                       extra="Cannot go below rooms already sold on a night">
              <InputNumber min={1} max={2000} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={12}>
          <Col xs={12} md={6}>
            <Form.Item label={`Base rate (${currency})`} name="basePrice">
              <InputNumber min={0} step={10000} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item label="Size (m²)" name="sizeSqm">
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item label="Minimum nights" name="minStayNights">
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item label="Maximum nights" name="maxStayNights" extra="Empty for no limit">
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={12}>
          <Col xs={24} md={16}>
            <Form.Item label="Beds" name="bedConfig">
              <Input placeholder="1 queen bed, or 2 singles" maxLength={160} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item label="On sale" name="status" getValueProps={(value) => ({
              checked: value === 'ACTIVE',
            })} getValueFromEvent={(checked) => (checked ? 'ACTIVE' : 'INACTIVE')}
                       extra="Turning this off stops new bookings; existing stays are kept.">
              <Switch />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item label="Description" name="description">
          <Input.TextArea rows={3} maxLength={4000} />
        </Form.Item>

        <Typography.Text type="secondary" style={{ fontSize: 12 }}>Room features</Typography.Text>
        <Form.Item name="amenities" noStyle>
          <Checkbox.Group style={{ width: '100%', marginTop: 6 }}>
            <Space orientation="vertical" size="small" style={{ width: '100%' }}>
              {ROOM_AMENITY_GROUPS.map((group) => (
                <div key={group.label}>
                  <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                    {group.label}
                  </Typography.Text>
                  <Flex gap={8} wrap style={{ marginTop: 4 }}>
                    {group.amenities.map((amenity) => (
                      <Checkbox key={amenity} value={amenity}>
                        {amenity.toLowerCase().replace(/_/g, ' ')}
                      </Checkbox>
                    ))}
                  </Flex>
                </div>
              ))}
            </Space>
          </Checkbox.Group>
        </Form.Item>

        <Flex gap={12} wrap align="center" style={{ marginTop: 12 }}>
          {photos.map((photo) => (
            <Flex key={photo.id} vertical gap={4} style={{ width: 140 }}>
              <img src={photo.url} alt={photo.altText ?? ''} width={140} height={96}
                   style={{ objectFit: 'cover', borderRadius: 6 }} />
              <Button type="text" size="small" danger disabled={busy}
                      onClick={async () => {
                        await hotels.deleteRoomTypePhoto(roomType.id, photo.id)
                        setPhotos((current) => current.filter((item) => item.id !== photo.id))
                      }}>
                Remove
              </Button>
            </Flex>
          ))}
          <label style={{ cursor: 'pointer' }}>
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              hidden
              onChange={(event) => {
                const file = event.target.files?.[0]
                if (file) {
                  uploadPhoto(file)
                }
                event.target.value = ''
              }}
            />
            <Flex align="center" justify="center"
                  style={{ width: 140, height: 96, border: '1px dashed #d9d9d9',
                           borderRadius: 6, background: '#fafafa' }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Add a room photo
              </Typography.Text>
            </Flex>
          </label>
        </Flex>

        <Flex justify="flex-end" style={{ marginTop: 16 }}>
          <Button type="primary" htmlType="submit" loading={busy}>Save room type</Button>
        </Flex>
      </Form>
    </Card>
  )
}

/** Hotel-level photos: the building, lobby, restaurant, pool. */
function HotelPhotosTab({ hotel, onChanged }: { hotel: Hotel; onChanged: () => void }) {
  const { message } = AntApp.useApp()

  const [busy, setBusy] = useState(false)

  async function upload(file: File) {
    setBusy(true)
    try {
      const presigned = await hotels.photoUploadUrl(hotel.id,
        { contentType: file.type, sizeBytes: file.size })
      await putToStorage(presigned.uploadUrl, file)
      await hotels.confirmPhoto(hotel.id,
        { storageKey: presigned.storageKey, altText: file.name })
      message.success('Photo added')
      onChanged()
    } catch (failure) {
      message.error(failure instanceof RequestError
        ? failure.message : 'The upload did not complete')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card size="small" title="Hotel photos"
          extra={<Typography.Text type="secondary" style={{ fontSize: 12 }}>
            The building and shared spaces. Room photos live on each room type.
          </Typography.Text>}>
      <Flex gap={12} wrap>
        {hotel.photos.map((photo) => (
          <Flex key={photo.id} vertical gap={4} style={{ width: 180 }}>
            <img src={photo.url} alt={photo.altText ?? ''} width={180} height={124}
                 style={{ objectFit: 'cover', borderRadius: 8 }} />
            <Flex justify="space-between" align="center">
              {photo.cover
                ? <Tag color="blue">Cover</Tag>
                : <Button type="link" size="small" disabled={busy}
                          onClick={async () => {
                            await hotels.reorderPhotos(hotel.id, {
                              photoIdsInOrder: hotel.photos.map((item) => item.id),
                              coverPhotoId: photo.id,
                            })
                            onChanged()
                          }}>
                    Make cover
                  </Button>}
              <Popconfirm title="Remove this photo?" okButtonProps={{ danger: true }}
                          onConfirm={async () => {
                            await hotels.deletePhoto(hotel.id, photo.id)
                            onChanged()
                          }}>
                <Button type="text" size="small" danger disabled={busy}>Remove</Button>
              </Popconfirm>
            </Flex>
          </Flex>
        ))}

        <label style={{ cursor: 'pointer' }}>
          <input type="file" accept="image/jpeg,image/png,image/webp" hidden
                 onChange={(event) => {
                   const file = event.target.files?.[0]
                   if (file) {
                     upload(file)
                   }
                   event.target.value = ''
                 }} />
          <Flex align="center" justify="center"
                style={{ width: 180, height: 124, border: '1px dashed #d9d9d9',
                         borderRadius: 8, background: '#fafafa' }}>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Add a photo
            </Typography.Text>
          </Flex>
        </label>
      </Flex>
      {hotel.photos.length === 0 && (
        <Typography.Text type="warning" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
          At least one photo is required before the hotel can be submitted.
        </Typography.Text>
      )}
    </Card>
  )
}

/** Front-desk accounts, scoped to this hotel. */
function StaffTab({ hotelId }: { hotelId: string }) {
  const { message } = AntApp.useApp()

  const [staff, setStaff] = useState<import('../../types').StaffMember[] | null>(null)
  const [phone, setPhone] = useState('')
  const [busy, setBusy] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const rows = await hotels.listStaff(hotelId)
        if (!cancelled) {
          setStaff(rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setStaff([])
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load staff')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [hotelId, reloadToken, message])

  async function add() {
    setBusy(true)
    try {
      await hotels.addStaff(hotelId, phone)
      message.success('Staff member added')
      setPhone('')
      setReloadToken((token) => token + 1)
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not add them')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="What staff can and cannot do"
        description="Front-desk accounts see this hotel's reservations and handle check-in and
          check-out. They cannot change rates, availability, room types or staff — and they see
          only the hotels they are assigned to, even within the same business."
      />

      <Table<import('../../types').StaffMember>
        size="small"
        rowKey="userId"
        pagination={false}
        loading={staff === null}
        dataSource={staff ?? []}
        locale={{ emptyText: 'No front-desk accounts yet' }}
        columns={[
          { title: 'Name', dataIndex: 'fullName', render: (name?: string) => name ?? '—' },
          { title: 'Phone', dataIndex: 'phone' },
          {
            title: 'Added',
            dataIndex: 'assignedAt',
            render: (value: string) => dayjs(value).format('D MMM YYYY'),
          },
          {
            title: '',
            width: 100,
            render: (_, member) => (
              <Popconfirm title="Remove from this hotel?"
                          okButtonProps={{ danger: true }}
                          onConfirm={async () => {
                            await hotels.removeStaff(hotelId, member.userId)
                            setReloadToken((token) => token + 1)
                          }}>
                <Button type="text" size="small" danger>Remove</Button>
              </Popconfirm>
            ),
          },
        ]}
      />

      <Card size="small" title="Add a front-desk account">
        <Flex gap={8} wrap align="center">
          <Input
            placeholder="Their phone number"
            style={{ maxWidth: 220 }}
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            onPressEnter={add}
          />
          <Button type="primary" loading={busy} disabled={!phone.trim()} onClick={add}>
            Add
          </Button>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            They need to have signed up and verified their number first — we do not create
            accounts on their behalf.
          </Typography.Text>
        </Flex>
      </Card>
    </Space>
  )
}
