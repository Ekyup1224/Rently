import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Empty, Flex, Image, Popconfirm, Select, Space, Spin, Tag,
  Typography,
} from 'antd'
import { useNavigate } from 'react-router-dom'
import { RequestError } from '../../api/client'
import { owner } from '../../api/endpoints'
import type { Property, PropertyStatus } from '../../types'
import { CreateListingModal } from './CreateListingModal'
import { STATUS_COLORS, STATUS_LABELS, formatMoney } from './listingFormat'
import { PageHead } from '../../ui/PageHead'

/** An owner's listings, with the actions each one's state actually allows. */
export function PropertiesPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const navigate = useNavigate()
  const [listings, setListings] = useState<Property[] | null>(null)
  const [status, setStatus] = useState<PropertyStatus | undefined>()
  const [creating, setCreating] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const page = await owner.listProperties({ status, page: 0, size: 50 })
        if (!cancelled) {
          setListings(page.rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setListings([])
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load your listings')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [status, reloadToken, message])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  async function act(listing: Property, action: () => Promise<unknown>, success: string) {
    setBusyId(listing.id)
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
        title="Properties"
        description={'Your houses and apartments, and where each one is in the approval process.'}
      />
        <Space>
          <Select<PropertyStatus>
            allowClear
            placeholder="Any status"
            style={{ minWidth: 180 }}
            value={status}
            onChange={setStatus}
            options={(Object.keys(STATUS_LABELS) as PropertyStatus[])
              .map((value) => ({ value, label: STATUS_LABELS[value] }))}
          />
          <Button type="primary" onClick={() => setCreating(true)}>New listing</Button>
        </Space>
      </Flex>

      {listings === null && <Flex justify="center" style={{ padding: 48 }}><Spin /></Flex>}

      {listings?.length === 0 && (
        <Card>
          <Empty description={status
            ? `No listings with status ${STATUS_LABELS[status]}`
            : 'You have no listings yet'}>
            <Button type="primary" onClick={() => setCreating(true)}>Create your first listing</Button>
          </Empty>
        </Card>
      )}

      {listings?.map((listing) => (
        <Card key={listing.id} size="small">
          <Flex gap={16} wrap>
            <div style={{ width: 160, flexShrink: 0 }}>
              {listing.photos[0]
                ? <Image src={listing.photos[0].url} alt={listing.photos[0].altText ?? ''}
                         width={160} height={110} style={{ objectFit: 'cover', borderRadius: 8 }} />
                : <Flex align="center" justify="center"
                        style={{ width: 160, height: 110, background: '#fafafa',
                                 border: '1px dashed #d9d9d9', borderRadius: 8 }}>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>No photos</Typography.Text>
                  </Flex>}
            </div>

            <Flex vertical flex={1} gap={4} style={{ minWidth: 240 }}>
              <Flex gap={8} align="center" wrap>
                <Typography.Text strong>{listing.title}</Typography.Text>
                <Tag color={STATUS_COLORS[listing.status]}>{STATUS_LABELS[listing.status]}</Tag>
                {listing.instantBook && <Tag color="green">Instant book</Tag>}
              </Flex>
              <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                {listing.propertyType.toLowerCase()} · {listing.district ?? listing.city} ·
                sleeps {listing.maxGuests}
              </Typography.Text>
              <Typography.Text style={{ fontSize: 13 }}>
                {formatMoney(listing.basePrice, listing.currency)} / night
                {listing.cleaningFee > 0
                  && ` · ${formatMoney(listing.cleaningFee, listing.currency)} cleaning`}
              </Typography.Text>

              {listing.status === 'REJECTED' && listing.rejectionReason && (
                <Alert type="error" showIcon style={{ marginTop: 4 }}
                       message="Not approved" description={listing.rejectionReason} />
              )}
              {listing.status === 'DRAFT' && listing.readinessProblems.length > 0 && (
                <Typography.Text type="warning" style={{ fontSize: 12 }}>
                  Before submitting: {listing.readinessProblems.join('; ')}
                </Typography.Text>
              )}
            </Flex>

            <Flex vertical gap={6} style={{ minWidth: 150 }}>
              <Button block onClick={() => navigate(`/properties/${listing.id}`)}>Edit</Button>
              <Button block onClick={() => navigate(`/properties/${listing.id}/calendar`)}>
                Calendar
              </Button>

              {(listing.status === 'DRAFT' || listing.status === 'REJECTED') && (
                <Button
                  block type="primary"
                  loading={busyId === listing.id}
                  disabled={listing.readinessProblems.length > 0}
                  onClick={() => act(listing, () => owner.submitProperty(listing.id),
                    'Submitted for review')}
                >
                  Submit for review
                </Button>
              )}
              {listing.status === 'APPROVED' && (
                <Button block loading={busyId === listing.id}
                        onClick={() => act(listing, () => owner.pauseProperty(listing.id),
                          'Listing paused')}>
                  Pause
                </Button>
              )}
              {listing.status === 'PAUSED' && (
                <Button block type="primary" loading={busyId === listing.id}
                        onClick={() => act(listing, () => owner.resumeProperty(listing.id),
                          'Listing is live again')}>
                  Resume
                </Button>
              )}
              {(listing.status === 'DRAFT' || listing.status === 'REJECTED') && (
                <Popconfirm
                  title="Delete this listing?"
                  description="This cannot be undone."
                  okButtonProps={{ danger: true }}
                  onConfirm={() => act(listing, () => owner.deleteProperty(listing.id),
                    'Listing deleted')}
                >
                  <Button block danger type="text">Delete</Button>
                </Popconfirm>
              )}
            </Flex>
          </Flex>
        </Card>
      ))}

      <CreateListingModal
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={(created) => {
          setCreating(false)
          navigate(`/properties/${created.id}`)
        }}
      />
    </Space>
  )
}
