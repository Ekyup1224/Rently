import { useEffect, useState } from 'react'
import {
  Alert, Button, Card, Checkbox, Col, Flex, Form, Input, InputNumber, Row, Select, Space, Spin,
  Switch, Tag, TimePicker, Typography, message,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { RequestError } from '../../api/client'
import { owner } from '../../api/endpoints'
import type { CancellationPolicy, Property, PropertyType } from '../../types'
import { PhotoManager } from './PhotoManager'
import { STATUS_COLORS, STATUS_LABELS } from './listingFormat'

const PROPERTY_TYPES: PropertyType[] = [
  'APARTMENT', 'HOUSE', 'GER', 'CABIN', 'VILLA', 'STUDIO', 'TOWNHOUSE', 'GUESTHOUSE',
]

const CANCELLATION_POLICIES: { value: CancellationPolicy; label: string }[] = [
  { value: 'FLEXIBLE', label: 'Flexible — full refund until 24h before' },
  { value: 'MODERATE', label: 'Moderate — full refund until 5 days before' },
  { value: 'STRICT', label: 'Strict — 50% until 7 days before' },
]

/** Amenities offered in the form, grouped the way an owner thinks about them. */
const AMENITY_GROUPS: { label: string; amenities: string[] }[] = [
  { label: 'Essentials', amenities: ['WIFI', 'KITCHEN', 'HOT_WATER', 'SHOWER', 'HEATING', 'TV'] },
  { label: 'Comfort', amenities: ['AIR_CONDITIONING', 'WASHER', 'DRYER', 'WORKSPACE', 'BATHTUB',
      'STOVE_HEATING', 'FIREPLACE'] },
  { label: 'Outside & parking', amenities: ['PARKING_FREE', 'PARKING_PAID', 'GARDEN', 'TERRACE',
      'BBQ_GRILL', 'POOL', 'HOT_TUB', 'SAUNA'] },
  { label: 'Views', amenities: ['MOUNTAIN_VIEW', 'RIVER_VIEW', 'CITY_VIEW'] },
  { label: 'Access', amenities: ['ELEVATOR', 'PRIVATE_ENTRANCE', 'WHEELCHAIR_ACCESSIBLE',
      'SELF_CHECK_IN', 'LUGGAGE_DROPOFF'] },
  { label: 'Safety', amenities: ['SMOKE_ALARM', 'FIRE_EXTINGUISHER', 'FIRST_AID_KIT'] },
  { label: 'Rules & family', amenities: ['PETS_ALLOWED', 'SMOKING_ALLOWED', 'EVENTS_ALLOWED',
      'LONG_TERM_STAYS', 'CRIB', 'HIGH_CHAIR'] },
]

/**
 * The listing editor.
 *
 * <p>Everything saves against the live listing, including while it is approved.
 * Changing the location or property type is the exception: the server returns the
 * listing to review, since the approval was a judgement about those facts, and the
 * status tag updates to say so.
 */
export function PropertyEditorPage() {
  const { propertyId } = useParams<{ propertyId: string }>()
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [listing, setListing] = useState<Property | null>(null)
  const [saving, setSaving] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    if (!propertyId) {
      return
    }
    let cancelled = false

    async function load() {
      try {
        const loaded = await owner.getProperty(propertyId!)
        if (!cancelled) {
          setListing(loaded)
          form.setFieldsValue({
            ...loaded,
            checkInFrom: loaded.checkInFrom ? dayjs(loaded.checkInFrom, 'HH:mm:ss') : undefined,
            checkOutBy: loaded.checkOutBy ? dayjs(loaded.checkOutBy, 'HH:mm:ss') : undefined,
          })
        }
      } catch (failure) {
        if (!cancelled) {
          message.error(failure instanceof RequestError ? failure.message : 'Listing not found')
          navigate('/properties')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [propertyId, form, navigate, reloadToken])

  async function save(values: Record<string, unknown>) {
    if (!propertyId) {
      return
    }
    setSaving(true)
    try {
      const patch = {
        ...values,
        checkInFrom: values.checkInFrom ? (values.checkInFrom as dayjs.Dayjs).format('HH:mm:ss') : undefined,
        checkOutBy: values.checkOutBy ? (values.checkOutBy as dayjs.Dayjs).format('HH:mm:ss') : undefined,
      }
      const updated = await owner.updateProperty(propertyId, patch)
      setListing(updated)
      message.success(updated.status === 'PENDING_REVIEW' && listing?.status === 'APPROVED'
        ? 'Saved. Because the location or type changed, the listing is back under review.'
        : 'Saved')
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not save')
    } finally {
      setSaving(false)
    }
  }

  async function submitForReview() {
    if (!propertyId) {
      return
    }
    try {
      const updated = await owner.submitProperty(propertyId)
      setListing(updated)
      message.success('Submitted for review')
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'Could not submit')
    }
  }

  if (!listing) {
    return <Flex justify="center" style={{ padding: 64 }}><Spin size="large" /></Flex>
  }

  const ready = listing.readinessProblems.length === 0
  const submittable = listing.status === 'DRAFT' || listing.status === 'REJECTED'

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%', maxWidth: 900 }}>
      <Flex align="center" justify="space-between" wrap gap={12}>
        <Space>
          <Button onClick={() => navigate('/properties')}>Back</Button>
          <Typography.Title level={3} style={{ margin: 0 }}>{listing.title}</Typography.Title>
          <Tag color={STATUS_COLORS[listing.status]}>{STATUS_LABELS[listing.status]}</Tag>
        </Space>
        <Space>
          <Button onClick={() => navigate(`/properties/${listing.id}/calendar`)}>Calendar</Button>
          {submittable && (
            <Button type="primary" disabled={!ready} onClick={submitForReview}>
              Submit for review
            </Button>
          )}
        </Space>
      </Flex>

      {listing.status === 'REJECTED' && listing.rejectionReason && (
        <Alert type="error" showIcon message="Not approved"
               description={listing.rejectionReason} />
      )}
      {submittable && !ready && (
        <Alert
          type="warning"
          showIcon
          message="Not ready to submit yet"
          description={<ul style={{ margin: 0, paddingInlineStart: 20 }}>
            {listing.readinessProblems.map((problem) => <li key={problem}>{problem}</li>)}
          </ul>}
        />
      )}
      {listing.status === 'SUSPENDED' && (
        <Alert type="error" showIcon message="This listing is suspended"
               description="Contact platform support. It cannot be edited while suspended." />
      )}

      <Form form={form} layout="vertical" onFinish={save}
            disabled={listing.status === 'SUSPENDED'}>
        <Card title="The basics" size="small">
          <Form.Item label="Title" name="title" rules={[{ required: true }]}>
            <Input maxLength={150} />
          </Form.Item>
          <Form.Item label="Description" name="description"
                     extra="What guests see on the listing page. Required before submitting.">
            <Input.TextArea rows={5} maxLength={8000} showCount />
          </Form.Item>
          <Row gutter={12}>
            <Col xs={12} md={6}>
              <Form.Item label="Type" name="propertyType">
                <Select options={PROPERTY_TYPES.map((value) => ({ value, label: value.toLowerCase() }))} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item label="Sleeps" name="maxGuests">
                <InputNumber min={1} max={50} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={8} md={4}>
              <Form.Item label="Bedrooms" name="bedrooms">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={8} md={4}>
              <Form.Item label="Beds" name="beds">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={8} md={4}>
              <Form.Item label="Bathrooms" name="bathrooms">
                <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        <Card title="Where it is" size="small" style={{ marginTop: 12 }}>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="Changing the address or coordinates of a live listing sends it back for review."
          />
          <Form.Item label="Street address" name="addressLine"
                     extra="Shown to guests only after they book.">
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
              <Form.Item label="Latitude" name="latitude"
                         extra="Required before submitting, so the listing appears on the map.">
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

        <Card title="Price and stay rules" size="small" style={{ marginTop: 12 }}>
          <Row gutter={12}>
            <Col xs={12} md={6}>
              <Form.Item label="Per night (₮)" name="basePrice" rules={[{ required: true }]}>
                <InputNumber min={0} step={10000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item label="Cleaning fee (₮)" name="cleaningFee">
                <InputNumber min={0} step={5000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item label="Minimum nights" name="minStayNights">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item label="Maximum nights" name="maxStayNights"
                         extra="Leave empty for no limit">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Set higher weekend or seasonal prices on the calendar; this is the default.
          </Typography.Text>
          <Row gutter={12} style={{ marginTop: 12 }}>
            <Col xs={24} md={14}>
              <Form.Item label="Cancellation policy" name="cancellationPolicy">
                <Select options={CANCELLATION_POLICIES} />
              </Form.Item>
            </Col>
            <Col xs={24} md={10}>
              <Form.Item label="Instant book" name="instantBook" valuePropName="checked"
                         extra="Off means you approve each request yourself.">
                <Switch />
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
        </Card>

        <Card title="Amenities" size="small" style={{ marginTop: 12 }}>
          <Form.Item name="amenities" noStyle>
            <Checkbox.Group style={{ width: '100%' }}>
              <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
                {AMENITY_GROUPS.map((group) => (
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

        <Card title="House rules" size="small" style={{ marginTop: 12 }}>
          <Form.Item name="houseRules" noStyle>
            <Input.TextArea rows={4} maxLength={4000} showCount
                            placeholder="Quiet after 22:00, no shoes indoors…" />
          </Form.Item>
        </Card>

        <Flex justify="flex-end" style={{ marginTop: 16 }}>
          <Button type="primary" htmlType="submit" loading={saving} size="large">
            Save changes
          </Button>
        </Flex>
      </Form>

      <PhotoManager
        propertyId={listing.id}
        photos={listing.photos}
        onChanged={() => setReloadToken((token) => token + 1)}
      />
    </Space>
  )
}
