'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { api, describeError } from '@/lib/api'
import type { HostApplication } from '@/lib/types'
import { useAuth } from './AuthProvider'
import { useT } from '@/lib/i18n'

/** Profile editing plus the host application flow, both wired to the live API. */
export function AccountView() {
  const { user, loading, reload } = useAuth()
  const t = useT()

  if (loading) {
    return <p className="muted">{t('common.loading')}</p>
  }

  if (!user) {
    return (
      <div className="card">
        <p style={{ marginTop: 0 }}>{t('account.signInBody')}</p>
        <Link href="/login" className="button">{t('nav.signIn')}</Link>
      </div>
    )
  }

  return (
    <>
      <h1>{t('account.title')}</h1>
      <ProfileCard onSaved={reload} />
      <HostApplicationCard />
      <div className="card">
        <h2 className="card__title">{t('account.roles')}</h2>
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
            {t('account.managePortal')}
          </p>
        )}
      </div>
    </>
  )
}

function ProfileCard({ onSaved }: { onSaved: () => Promise<void> }) {
  const { user } = useAuth()
  const t = useT()
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
      setNotice({ kind: 'info', text: t('account.profileSaved') })
    } catch (failure) {
      setNotice({ kind: 'error', text: describeError(failure) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card">
      <h2 className="card__title">{t('account.yourDetails')}</h2>
      {notice && <div className={`alert alert--${notice.kind}`}>{notice.text}</div>}
      <form
        onSubmit={(event) => {
          event.preventDefault()
          void save()
        }}
      >
        <label className="field">
          <span className="field__label">{t('account.phone')}</span>
          {/* Changing the primary identifier needs its own verified flow. */}
          <input value={user?.phone ?? ''} disabled />
        </label>
        <label className="field">
          <span className="field__label">{t('account.fullName')}</span>
          <input value={fullName} onChange={(event) => setFullName(event.target.value)} />
        </label>
        <label className="field">
          <span className="field__label">{t('account.email')}</span>
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
          />
        </label>
        <label className="field">
          <span className="field__label">{t('account.language')}</span>
          <select value={locale} onChange={(event) => setLocale(event.target.value)}>
            <option value="mn">Монгол</option>
            <option value="en">English</option>
          </select>
        </label>
        <button type="submit" className="button" disabled={busy}>
          {busy ? t('account.saving') : t('account.saveChanges')}
        </button>
      </form>
    </div>
  )
}

function HostApplicationCard() {
  const t = useT()
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
      <h2 className="card__title">{t('account.becomeHost')}</h2>
      <p className="muted small" style={{ marginTop: 0 }}>
        {t('account.hostLead')}
      </p>

      {error && <div className="alert alert--error">{error}</div>}

      {applications && applications.length > 0 && (
        <ul style={{ paddingInlineStart: 18, marginTop: 0 }}>
          {applications.map((application) => (
            <li key={application.id} className="small">
              <strong>{application.requestedRole === 'HOUSE_OWNER'
                ? t('account.houseOwner') : t('account.hotel')}</strong>
              {' — '}
              {t(`application.${application.status}`)}
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
                  {t('account.withdraw')}
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
            <span className="field__label">{t('account.iWantToList')}</span>
            <select
              value={requestedRole}
              onChange={(event) =>
                setRequestedRole(event.target.value as 'HOUSE_OWNER' | 'HOTEL_MANAGER')}
            >
              <option value="HOUSE_OWNER">{t('account.aHouse')}</option>
              <option value="HOTEL_MANAGER">{t('account.aHotel')}</option>
            </select>
          </label>

          {requestedRole === 'HOTEL_MANAGER' && (
            <>
              <label className="field">
                <span className="field__label">{t('account.orgName')}</span>
                <input
                  value={organizationName}
                  onChange={(event) => setOrganizationName(event.target.value)}
                  required
                />
              </label>
              <label className="field">
                <span className="field__label">{t('account.businessNumber')}</span>
                <input
                  value={organizationRegistrationNo}
                  onChange={(event) => setOrganizationRegistrationNo(event.target.value)}
                />
              </label>
            </>
          )}

          <label className="field">
            <span className="field__label">{t('account.noteOptional')}</span>
            <textarea rows={3} value={note} onChange={(event) => setNote(event.target.value)} />
          </label>

          <button type="submit" className="button" disabled={busy}>
            {busy ? t('account.submitting') : t('account.submitApplication')}
          </button>
        </form>
      )}
    </div>
  )
}
