'use client'

import { useState } from 'react'
import Link from 'next/link'
import { api, describeError } from '@/lib/api'
import type { SupplyType } from '@/lib/types'
import { useAuth } from './AuthProvider'
import { useT } from '@/lib/i18n'

/** What guests actually run into, in their words rather than ours. */
const REASONS = ['not_there', 'not_as_described', 'stolen_photos',
                 'asked_to_pay_off_platform', 'other'] as const

/**
 * Reporting a listing.
 *
 * <p>Kept quiet — a link, not a banner — because most listings are fine and an
 * accusatory button on every page would say otherwise. But it is on every page,
 * because the guest standing outside a house that was never there is the only
 * person who can tell us, and their report is what stops the host being paid.
 *
 * <p>Worth naming the off-platform-payment case explicitly: a host asking to be
 * paid directly is trying to get around the very hold that protects the guest,
 * and guests do not otherwise know that is worth reporting.
 */
export function ReportListing({ supplyType, supplyId }: {
  supplyType: SupplyType
  supplyId: string
}) {
  const { user } = useAuth()
  const t = useT()
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState<string>(REASONS[0])
  const [details, setDetails] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)

  if (sent) {
    return (
      <div className="card">
        <h2 className="card__title">{t('report.thanks')}</h2>
        <p className="muted small" style={{ marginTop: 0, marginBottom: 0 }}>
          {t('report.thanksBody')}
        </p>
      </div>
    )
  }

  if (!open) {
    return (
      <p className="muted small" style={{ textAlign: 'center', marginTop: 20 }}>
        <button type="button" className="button button--link" onClick={() => setOpen(true)}>
          {t('report.open')}
        </button>
      </p>
    )
  }

  return (
    <div className="card">
      <h2 className="card__title">{t('report.open')}</h2>
      <p className="muted small" style={{ marginTop: 0 }}>
        {t('report.lead')}
      </p>

      {!user ? (
        <>
          <div className="alert alert--info">
            {t('report.signInFirst')}
          </div>
          <Link href="/login" className="button button--block">{t('nav.signIn')}</Link>
        </>
      ) : (
        <form
          onSubmit={async (event) => {
            event.preventDefault()
            setBusy(true)
            setError(null)
            try {
              await api.reportListing(supplyType, supplyId, {
                reason,
                details: details || undefined,
              })
              setSent(true)
            } catch (failure) {
              setError(describeError(failure))
            } finally {
              setBusy(false)
            }
          }}
        >
          <label className="field">
            <span className="field__label">{t('report.reason')}</span>
            <select value={reason} onChange={(event) => setReason(event.target.value)}>
              {REASONS.map((option) => (
                <option key={option} value={option}>{t(`report.reason.${option}`)}</option>
              ))}
            </select>
          </label>

          <label className="field">
            <span className="field__label">{t('report.detailsOptional')}</span>
            <textarea
              rows={3}
              maxLength={2000}
              placeholder={t('report.detailsPlaceholder')}
              value={details}
              onChange={(event) => setDetails(event.target.value)}
            />
          </label>

          {error && <div className="alert alert--error">{error}</div>}

          <div className="row">
            <button type="submit" className="button" disabled={busy}>
              {busy ? t('common.sending') : t('report.submit')}
            </button>
            <button type="button" className="button button--ghost" onClick={() => setOpen(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
