import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert, App as AntApp, Avatar, Badge, Button, Card, Col, Empty, Flex, Input, Row, Skeleton,
  Typography,
} from 'antd'
import { SendOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { RequestError } from '../api/client'
import { conversations } from '../api/endpoints'
import type { Conversation, Message } from '../types'
import { palette } from '../theme'
import { PageHead } from '../ui/PageHead'

/**
 * The host's side of the same threads the guest app shows.
 *
 * <p>Same endpoints, same one-thread-per-booking rule: who you are decides what
 * you see, not which application you opened. A host with three properties still
 * has one inbox.
 */
export function MessagesPage() {
  const { message: toast } = AntApp.useApp()
  const [threads, setThreads] = useState<Conversation[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [draft, setDraft] = useState('')
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const bottom = useRef<HTMLDivElement | null>(null)

  const loadThreads = useCallback(async () => {
    try {
      const page = await conversations.inbox()
      setThreads(page.rows)
      setActiveId((current) => current ?? page.rows[0]?.id ?? null)
    } catch (failure) {
      toast.error(failure instanceof RequestError ? failure.message : 'Could not load messages')
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => { void loadThreads() }, [loadThreads])

  useEffect(() => {
    if (!activeId) {
      return
    }
    let cancelled = false

    async function open(conversationId: string) {
      try {
        const page = await conversations.messages(conversationId)
        if (cancelled) {
          return
        }
        // The endpoint pages newest-first, which is right for walking back
        // through history and wrong for reading a conversation: a chat runs
        // downwards.
        setMessages([...page.rows].reverse())
        await conversations.markRead(conversationId)
        if (!cancelled) {
          setThreads((current) => current.map((thread) =>
            thread.id === conversationId ? { ...thread, unread: 0 } : thread))
        }
      } catch (failure) {
        if (!cancelled) {
          toast.error(failure instanceof RequestError
            ? failure.message : 'Could not open that thread')
        }
      }
    }

    open(activeId)
    return () => { cancelled = true }
  }, [activeId, toast])

  useEffect(() => {
    bottom.current?.scrollIntoView({ block: 'end' })
  }, [messages])

  async function send() {
    const body = draft.trim()
    if (!body || !activeId || sending) {
      return
    }
    setSending(true)
    try {
      const sent = await conversations.send(activeId, body)
      setMessages((current) => [...current, sent])
      setDraft('')
      if (sent.flaggedReason) {
        toast.warning('That message was flagged for mentioning off-platform payment.')
      }
    } catch (failure) {
      toast.error(failure instanceof RequestError ? failure.message : 'Could not send it')
    } finally {
      setSending(false)
    }
  }

  const active = threads.find((thread) => thread.id === activeId)

  if (loading) {
    return (
      <>
        <PageHead title="Messages" />
        <Skeleton active paragraph={{ rows: 8 }} />
      </>
    )
  }

  return (
    <>
      <PageHead
        title="Messages"
        description={'One thread per booking. Keep payment on the platform — a guest who pays '
          + 'you directly has no protection, and asking for it freezes your payouts.'}
      />

      {threads.length === 0 ? (
        <Card size="small">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="No conversations yet. A thread appears when a guest books."
          />
        </Card>
      ) : (
        <Row gutter={16}>
          <Col xs={24} lg={8}>
            <Card size="small" styles={{ body: { padding: 8, maxHeight: 560, overflowY: 'auto' } }}>
              {threads.map((thread) => (
                <div
                  key={thread.id}
                  className="thread-list__item"
                  data-active={thread.id === activeId}
                  onClick={() => setActiveId(thread.id)}
                >
                  <Flex gap={10} align="flex-start">
                    <Badge count={thread.unread} size="small" offset={[-2, 2]}>
                      <Avatar size={34} style={{ background: palette.brandSoft,
                                                 color: palette.brandStrong, fontWeight: 600 }}>
                        {thread.withName.slice(0, 1).toUpperCase()}
                      </Avatar>
                    </Badge>
                    <Flex vertical gap={1} style={{ minWidth: 0, flex: 1 }}>
                      <Flex justify="space-between" gap={8}>
                        <Typography.Text strong style={{ fontSize: 13 }}>
                          {thread.withName}
                        </Typography.Text>
                        {thread.lastMessageAt && (
                          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                            {dayjs(thread.lastMessageAt).format('D MMM')}
                          </Typography.Text>
                        )}
                      </Flex>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }} ellipsis>
                        {thread.listingTitle}
                      </Typography.Text>
                      <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                        {thread.checkIn} → {thread.checkOut}
                      </Typography.Text>
                    </Flex>
                  </Flex>
                </div>
              ))}
            </Card>
          </Col>

          <Col xs={24} lg={16}>
            <Card
              size="small"
              title={active && (
                <Flex vertical gap={1}>
                  <Typography.Text strong>{active.withName}</Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 400 }}>
                    {active.listingTitle} · {active.bookingReference}
                  </Typography.Text>
                </Flex>
              )}
              styles={{ body: { display: 'flex', flexDirection: 'column', height: 500 } }}
            >
              <div style={{ flex: 1, overflowY: 'auto', paddingRight: 4 }}>
                {messages.length === 0 && (
                  <Typography.Text type="secondary">
                    No messages yet. Say hello — a guest who has heard from their host arrives
                    with fewer questions.
                  </Typography.Text>
                )}
                {messages.map((entry) => (
                  <div
                    key={entry.id}
                    className={entry.mine ? 'bubble-row bubble-row--mine' : 'bubble-row'}
                  >
                    <div className={entry.mine ? 'bubble bubble--mine' : 'bubble'}>
                      <div>{entry.body}</div>
                      <Typography.Text type="secondary"
                                       style={{ fontSize: 11, display: 'block', marginTop: 3 }}>
                        {dayjs(entry.sentAt).format('D MMM HH:mm')}
                      </Typography.Text>
                      {entry.flaggedReason && (
                        <Alert
                          type="warning"
                          showIcon
                          style={{ marginTop: 8, fontSize: 12 }}
                          message="Flagged for review"
                          description={'This mentions paying outside the platform, which holds '
                            + 'your payouts until someone has read it.'}
                        />
                      )}
                    </div>
                  </div>
                ))}
                <div ref={bottom} />
              </div>

              <Flex gap={8} style={{ paddingTop: 12, borderTop: `1px solid ${palette.line}`,
                                     marginTop: 12 }}>
                <Input
                  value={draft}
                  maxLength={4000}
                  placeholder="Write a message…"
                  onChange={(event) => setDraft(event.target.value)}
                  onPressEnter={send}
                />
                <Button type="primary" icon={<SendOutlined />} loading={sending}
                        disabled={!draft.trim()} onClick={send}>
                  Send
                </Button>
              </Flex>
            </Card>
          </Col>
        </Row>
      )}
    </>
  )
}
