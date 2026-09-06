'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { api, describeError } from '@/lib/api'
import type { HostApplication } from '@/lib/types'
import { useAuth } from './AuthProvider'

/** Profile editing plus the host application flow, both wired to the live API. */
export function AccountView() {
  const { user, loading, reload } = useAuth()

  if (loading) {
    return <p className="muted">Loading…</p>
  }

  if (!user) {
    return (
      <div className="card">
        <p style={{ marginTop: 0 }}>Sign in to manage your account.</p>
        <Link href="/login" className="button">Sign in</Link>
      </div>
    )
  }

  return (
    <>
      <ProfileCard onSaved={reload} />
      <HostApplicationCard />
      <div className="card">
        <h2 className="card__title">Roles</h2>
        <div className="row">
          {user.roles.map((grant) => (
            <span className="tag" key={`${grant.role}:${grant.organizationId ?? ''}`}>
              {grant.role}
              {grant.organizationName ? ` · ${grant.organizationName}` : ''}
            </span>
          ))}
        </div>
        {user.roles.some((grant) => grant.role !== 'CLIENT') && (
          <p className="muted small" style={{ marginBottom: 0 }}>
            Manage your listings in the partner portal.
          </p>
        )}
      </div>
    </>
  )
}

function ProfileCard({ onSaved }: { onSaved: () => Promise<void> }) {
  const { user } = useAuth()
  const [fullName, setFullName] = useState(user?.fullName ?? '')
  const [email, setEmail] = useState(user?.email ?? '')
  const [locale, setLocale] = useState(user?.locale ?? 'mn')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<{ kind: 'error' | 'info'; text: string } | null>(null)

  async function save() {
    setBusy(true)
    setNotice(null)
    try {
      await api.updateProfile({ fullName, email, locale })
      await onSaved()
      setNotice({ kind: 'info', text: 'Profile saved.' })
    } catch (failure) {
      setNotice({ kind: 'error', text: describeError(failure) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card">
      <h2 className="card__title">Your details</h2>
      {notice && <div className={`alert alert--${notice.kind}`}>{notice.text}</div>}
      <form
        onSubmit={(event) => {
          event.preventDefault()
          void save()
        }}
      >
        <label className="field">
          <span className="field__label">Phone</span>
          {/* Changing the primary identifier needs its own verified flow. */}
          <input value={user?.phone ?? ''} disabled />
        </label>
        <label className="field">
          <span className="field__label">Full name</span>
          <input value={fullName} onChange={(event) => setFullName(event.target.value)} />
        </label>
        <label className="field">
          <span className="field__label">Email</span>
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
          />
        </label>
        <label className="field">
          <span className="field__label">Language</span>
          <select value={locale} onChange={(event) => setLocale(event.target.value)}>
            <option value="mn">Монгол</option>
            <option value="en">English</option>
          </select>
        </label>
        <button type="submit" className="button" disabled={busy}>
          {busy ? 'Saving…' : 'Save changes'}
        </button>
      </form>
    </div>
  )
}

const STATUS_COPY: Record<HostApplication['status'], string> = {
  PENDING: 'Under review',
  APPROVED: 'Approved',
  REJECTED: 'Not approved',
  WITHDRAWN: 'Withdrawn',
}

function HostApplicationCard() {
  const [applications, setApplications] = useState<HostApplication[] | null>(null)
  /** Bumped after a mutation to re-run the fetch effect. */
  const [reloadToken, setReloadToken] = useState(0)
  const [requestedRole, setRequestedRole] = useState<'HOUSE_OWNER' | 'HOTEL_MANAGER'>('HOUSE_OWNER')
  const [organizationName, setOrganizationName] = useState('')
  const [organizationRegistrationNo, setOrganizationRegistrationNo] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // The fetch lives inside the effect and the state updates happen after the
  // await, so no render is triggered synchronously from the effect body.
  useEffect(() => {
    let cancelled = false

    async function fetchApplications() {
      try {
        const rows = await api.listHostApplications()
        if (!cancelled) {
          setApplications(rows)
        }
      } catch {
        if (!cancelled) {
          setApplications([])
        }
      }
    }

    fetchApplications()
    return () => {
      cancelled = true
    }
  }, [reloadToken])

  const reload = () => setReloadToken((token) => token + 1)

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      await api.applyAsHost({
        requestedRole,
        organizationName: requestedRole === 'HOTEL_MANAGER' ? organizationName : undefined,
        organizationRegistrationNo:
          requestedRole === 'HOTEL_MANAGER' ? organizationRegistrationNo : undefined,
        note: note || undefined,
      })
      setNote('')
      reload()
    } catch (failure) {
      setError(describeError(failure))
    } finally {
      setBusy(false)
    }
  }

  const pending = applications?.find((application) => application.status === 'PENDING')

  return (
    <div className="card" id="host">
      <h2 className="card__title">Become a host</h2>
      <p className="muted small" style={{ marginTop: 0 }}>
        Applications are reviewed by our team. Approval grants access to the partner
        portal, where you manage listings, rates and bookings.
      </p>

      {error && <div className="alert alert--error">{error}</div>}

      {applications && applications.length > 0 && (
        <ul style={{ paddingInlineStart: 18, marginTop: 0 }}>
          {applications.map((application) => (
            <li key={application.id} className="small">
              <strong>{application.requestedRole === 'HOUSE_OWNER' ? 'House owner' : 'Hotel'}</strong>
              {' — '}
              {STATUS_COPY[application.status]}
              {application.decisionNote ? ` · ${application.decisionNote}` : ''}
              {application.status === 'PENDING' && (
                <button
                  type="button"
                  className="button button--link"
                  disabled={busy}
                  onClick={async () => {
                    setBusy(true)
                    try {
                      await api.withdrawHostApplication(application.id)
                      reload()
                    } catch (failure) {
                      setError(describeError(failure))
                    } finally {
                      setBusy(false)
                    }
                  }}
                >
                  Withdraw
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {!pending && (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            void submit()
          }}
        >
          <label className="field">
            <span className="field__label">I want to list</span>
            <select
              value={requestedRole}
              onChange={(event) =>
                setRequestedRole(event.target.value as 'HOUSE_OWNER' | 'HOTEL_MANAGER')}
            >
              <option value="HOUSE_OWNER">A house or apartment I own</option>
              <option value="HOTEL_MANAGER">A hotel with room types</option>
            </select>
          </label>

          {requestedRole === 'HOTEL_MANAGER' && (
            <>
              <label className="field">
                <span className="field__label">Registered business name</span>
                <input
                  value={organizationName}
                  onChange={(event) => setOrganizationName(event.target.value)}
                  required
                />
              </label>
              <label className="field">
                <span className="field__label">Business registration number</span>
                <input
                  value={organizationRegistrationNo}
                  onChange={(event) => setOrganizationRegistrationNo(event.target.value)}
                />
              </label>
            </>
          )}

          <label className="field">
            <span className="field__label">Anything we should know (optional)</span>
            <textarea rows={3} value={note} onChange={(event) => setNote(event.target.value)} />
          </label>

          <button type="submit" className="button" disabled={busy}>
            {busy ? 'Submitting…' : 'Submit application'}
          </button>
        </form>
      )}
    </div>
  )
}
