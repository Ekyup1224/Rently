'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { api, describeError } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { useLanguage } from '@/lib/i18n'
import { useAuth } from './AuthProvider'
import type { Conversation, Message } from '@/lib/types'

/**
 * The guest inbox: one thread per booking.
 *
 * <p>Threads are never started from here — a conversation only exists because a
 * stay does, which is what keeps the inbox from becoming a channel for strangers
 * to reach each other.
 */
export function MessagesView() {
  const { user, loading: authLoading } = useAuth()
  const { t, locale } = useLanguage()
  const params = useSearchParams()
  const bookingParam = params.get('booking')

  const [conversations, setConversations] = useState<Conversation[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const bottom = useRef<HTMLDivElement | null>(null)

  const loadConversations = useCallback(async () => {
    const page = await api.conversations()
    setConversations(page.rows)
    return page.rows
  }, [])

  // Opening the inbox: fetch the threads, and open the one the caller asked for.
  useEffect(() => {
    if (authLoading || !user) {
      return
    }
    let cancelled = false

    async function load() {
      try {
        // A "message host" link carries a booking that may have no thread yet.
        if (bookingParam) {
          const opened = await api.openConversation(bookingParam)
          if (!cancelled) {
            setActiveId(opened.id)
          }
        }
        const rows = await loadConversations()
        if (!cancelled) {
          setActiveId((current) => current ?? rows[0]?.id ?? null)
        }
      } catch (failure) {
        if (!cancelled) {
          setError(describeError(failure))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [authLoading, user, bookingParam, loadConversations])

  // Opening a thread: read it, and mark it read so the badge clears.
  useEffect(() => {
    if (!activeId) {
      return
    }
    let cancelled = false

    async function load(conversationId: string) {
      try {
        const page = await api.messages(conversationId)
        if (cancelled) {
          return
        }
        // The endpoint pages newest-first, which is right for walking back
        // through history and wrong for reading a conversation: a chat runs
        // downwards.
        setMessages([...page.rows].reverse())
        await api.markConversationRead(conversationId)
        if (!cancelled) {
          setConversations((current) => current.map((conversation) =>
            conversation.id === conversationId ? { ...conversation, unread: 0 } : conversation))
        }
      } catch (failure) {
        if (!cancelled) {
          setError(describeError(failure))
        }
      }
    }

    load(activeId)
    return () => {
      cancelled = true
    }
  }, [activeId])

  useEffect(() => {
    bottom.current?.scrollIntoView({ block: 'end' })
  }, [messages])

  async function send(event: React.FormEvent) {
    event.preventDefault()
    const body = draft.trim()
    if (!body || !activeId || sending) {
      return
    }
    setSending(true)
    setError(null)
    try {
      const sent = await api.sendMessage(activeId, body)
      setMessages((current) => [...current, sent])
      setDraft('')
    } catch (failure) {
      setError(describeError(failure))
    } finally {
      setSending(false)
    }
  }

  if (authLoading || loading) {
    return <p className="muted">{t('common.loading')}</p>
  }
  if (!user) {
    return (
      <div className="card">
        <h2 className="card__title">{t('messages.title')}</h2>
        <p className="muted small" style={{ margin: 0 }}>{t('auth.signInToContinue')}</p>
      </div>
    )
  }

  const active = conversations.find((conversation) => conversation.id === activeId)

  return (
    <>
      <h1>{t('messages.title')}</h1>
      {error && <div className="alert alert--error">{error}</div>}

      {conversations.length === 0 ? (
        <div className="card">
          <p className="muted" style={{ margin: 0 }}>{t('messages.empty')}</p>
        </div>
      ) : (
        <div className="inbox">
          <nav className="inbox__list" aria-label={t('messages.title')}>
            {conversations.map((conversation) => (
              <button
                key={conversation.id}
                type="button"
                className="thread"
                aria-current={conversation.id === activeId}
                onClick={() => setActiveId(conversation.id)}
              >
                <span className="thread__top">
                  <strong>{conversation.withName}</strong>
                  {conversation.unread > 0 && (
                    <span className="badge">{conversation.unread}</span>
                  )}
                </span>
                <span className="muted small">{conversation.listingTitle}</span>
                <span className="muted small">
                  {conversation.checkIn} → {conversation.checkOut}
                </span>
              </button>
            ))}
          </nav>

          <section className="inbox__thread">
            {active ? (
              <>
                <header className="inbox__head">
                  <strong>{t('messages.with', { name: active.withName })}</strong>
                  <span className="muted small">
                    {active.listingTitle} · {active.bookingReference}
                  </span>
                </header>

                <div className="alert alert--info small">{t('messages.safety')}</div>

                <div className="bubbles">
                  {messages.length === 0 && (
                    <p className="muted small">{t('messages.noMessages')}</p>
                  )}
                  {messages.map((message) => (
                    <div
                      key={message.id}
                      className={message.mine ? 'bubble bubble--mine' : 'bubble'}
                    >
                      <p style={{ margin: 0 }}>{message.body}</p>
                      <span className="bubble__time">
                        {formatDateTime(message.sentAt, locale)}
                      </span>
                      {message.flaggedReason && (
                        <p className="bubble__warning">{t('messages.flagged')}</p>
                      )}
                    </div>
                  ))}
                  <div ref={bottom} />
                </div>

                <form className="composer" onSubmit={send}>
                  <input
                    className="input"
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    placeholder={t('messages.placeholder')}
                    maxLength={4000}
                    aria-label={t('messages.placeholder')}
                  />
                  <button className="button" type="submit" disabled={sending || !draft.trim()}>
                    {sending ? t('common.sending') : t('common.send')}
                  </button>
                </form>
              </>
            ) : (
              <p className="muted">{t('messages.pick')}</p>
            )}
          </section>
        </div>
      )}
    </>
  )
}
