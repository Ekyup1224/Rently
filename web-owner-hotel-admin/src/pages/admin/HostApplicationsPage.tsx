import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntApp, Button, Card, Descriptions, Empty, Flex, Input, Modal, Segmented,
  Space, Spin, Tag, Typography,
} from 'antd'
import dayjs from 'dayjs'
import { RequestError } from '../../api/client'
import { adminHotels } from '../../api/endpoints'
import type { HostApplication } from '../../types'
import { PageHead } from '../../ui/PageHead'

const QUEUES = [
  { label: 'Pending', value: 'PENDING' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Rejected', value: 'REJECTED' },
  { label: 'All', value: 'ALL' },
]

const STATUS_COLORS: Record<HostApplication['status'], string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'default',
}

/**
 * Deciding who becomes a host.
 *
 * <p>Approving a hotel application is what registers the business it will trade
 * under, so this queue is the door every hotel on the platform comes through.
 * That is why the decision spells out what it creates rather than just granting a
 * role quietly.
 */
export function HostApplicationsPage() {
  // The context instance, not the static one: static message renders outside
  // AntApp's holder and gets hidden behind the app shell header.
  const { message } = AntApp.useApp()

  const [queue, setQueue] = useState('PENDING')
  const [list, setList] = useState<HostApplication[] | null>(null)
  const [decision, setDecision] =
    useState<{ application: HostApplication; approve: boolean } | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const page = await adminHotels.hostApplications({
          status: queue === 'ALL' ? undefined : queue, page: 0, size: 50,
        })
        if (!cancelled) {
          setList(page.rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setList([])
          message.error(failure instanceof RequestError
            ? failure.message : 'Could not load applications')
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [queue, reloadToken, message])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <PageHead
        title="Host applications"
        description={'People asking to list. Approving one grants portal access, so check the identity beside the request.'}
      />

      <Alert
        type="info"
        showIcon
        message="Approval is what grants access"
        description="Submitting an application grants nothing. Approving a house application
          gives the account the owner role; approving a hotel application also registers the
          business the hotel will belong to, reusing an existing one when the registration
          number matches."
      />

      <Segmented options={QUEUES} value={queue}
                 onChange={(value) => setQueue(value as string)} />

      {list === null && <Flex justify="center" style={{ padding: 48 }}><Spin /></Flex>}
      {list?.length === 0 && <Card><Empty description="Nothing in this queue" /></Card>}

      {list?.map((application) => (
        <Card key={application.id} size="small">
          <Flex gap={16} wrap justify="space-between">
            <Flex vertical gap={6} flex={1} style={{ minWidth: 300 }}>
              <Flex gap={8} align="center" wrap>
                <Typography.Text strong>
                  {application.requestedRole === 'HOTEL_MANAGER' ? 'Hotel business' : 'House owner'}
                </Typography.Text>
                <Tag color={STATUS_COLORS[application.status]}>
                  {application.status.toLowerCase()}
                </Tag>
              </Flex>

              <Descriptions column={1} size="small" style={{ maxWidth: 520 }}>
                {/* Who is being approved. Without this the queue showed a business
                    name and a date, which is not enough to grant anyone a role. */}
                <Descriptions.Item label="Applicant">
                  {application.applicantName ?? 'No name given'}
                  {' · '}{application.applicantPhone}
                  <Tag
                    style={{ marginInlineStart: 8 }}
                    color={application.applicantKycStatus === 'VERIFIED' ? 'green'
                      : application.applicantKycStatus === 'REJECTED' ? 'red' : 'default'}
                  >
                    {application.applicantKycStatus === 'VERIFIED'
                      ? 'identity verified'
                      : `identity ${application.applicantKycStatus.toLowerCase()}`}
                  </Tag>
                </Descriptions.Item>
                {application.organizationName && (
                  <Descriptions.Item label="Business name">
                    {application.organizationName}
                  </Descriptions.Item>
                )}
                {application.organizationRegistrationNo && (
                  <Descriptions.Item label="Registration">
                    {application.organizationRegistrationNo}
                  </Descriptions.Item>
                )}
                <Descriptions.Item label="Applied">
                  {dayjs(application.createdAt).format('D MMM YYYY HH:mm')}
                </Descriptions.Item>
                {application.decisionNote && (
                  <Descriptions.Item label="Decision note">
                    {application.decisionNote}
                  </Descriptions.Item>
                )}
              </Descriptions>

              {application.note && (
                <Alert type="info" message={`"${application.note}"`} style={{ maxWidth: 520 }} />
              )}
            </Flex>

            {application.status === 'PENDING' && (
              <Flex vertical gap={6} style={{ minWidth: 140 }}>
                <Button type="primary" block
                        onClick={() => setDecision({ application, approve: true })}>
                  Approve
                </Button>
                <Button block danger
                        onClick={() => setDecision({ application, approve: false })}>
                  Reject
                </Button>
              </Flex>
            )}
          </Flex>
        </Card>
      ))}

      <ApplicationDecisionModal decision={decision} onClose={() => setDecision(null)}
                                onDone={() => { setDecision(null); reload() }} />
    </Space>
  )
}

function ApplicationDecisionModal({
  decision, onClose, onDone,
}: {
  decision: { application: HostApplication; approve: boolean } | null
  onClose: () => void
  onDone: () => void
}) {
  const { message } = AntApp.useApp()

  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)

  async function confirm() {
    if (!decision) {
      return
    }
    if (!decision.approve && !note.trim()) {
      message.warning('A reason is required so the applicant knows why')
      return
    }
    setBusy(true)
    try {
      if (decision.approve) {
        await adminHotels.approveApplication(decision.application.id, note || undefined)
        message.success('Approved')
      } else {
        await adminHotels.rejectApplication(decision.application.id, note)
        message.success('Rejected')
      }
      setNote('')
      onDone()
    } catch (failure) {
      message.error(failure instanceof RequestError ? failure.message : 'That did not work')
    } finally {
      setBusy(false)
    }
  }

  const isHotel = decision?.application.requestedRole === 'HOTEL_MANAGER'

  return (
    <Modal
      open={decision !== null}
      title={decision?.approve ? 'Approve this application?' : 'Reject this application?'}
      onCancel={onClose}
      onOk={confirm}
      okText={decision?.approve ? 'Approve' : 'Reject'}
      okButtonProps={{ danger: !decision?.approve, loading: busy }}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        {decision?.approve
          ? isHotel
            ? `This grants the hotel manager role and registers "${
                decision.application.organizationName}" as a business they manage. `
              + 'They can then create hotels under it.'
            : 'This grants the house owner role, so they can create and publish listings.'
          : 'The applicant is told why and can apply again once it is addressed.'}
      </Typography.Paragraph>
      <Input.TextArea
        rows={3}
        maxLength={2000}
        placeholder={decision?.approve ? 'Optional note' : 'Reason (shown to the applicant)'}
        value={note}
        onChange={(event) => setNote(event.target.value)}
      />
    </Modal>
  )
}
