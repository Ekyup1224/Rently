'use client'

import { useEffect, useState } from 'react'
import { api, describeError } from '@/lib/api'
import { useT } from '@/lib/i18n'
import type { Review } from '@/lib/types'

const ASPECTS = ['cleanliness', 'accuracy', 'location', 'value'] as const

/** Five buttons. Cheaper to tap on a phone than a slider, and unambiguous. */
function StarPicker({ label, value, onChange }: {
  label: string
  value: number
  onChange: (score: number) => void
}) {
  return (
    <div className="star-picker">
      <span className="star-picker__label">{label}</span>
      <span className="star-picker__stars" role="radiogroup" aria-label={label}>
        {[1, 2, 3, 4, 5].map((score) => (
          <button
            key={score}
            type="button"
            role="radio"
            aria-checked={value === score}
            aria-label={String(score)}
            className={score <= value ? 'star star--on' : 'star'}
            onClick={() => onChange(score)}
          >
            ★
          </button>
        ))}
      </span>
    </div>
  )
}

/**
 * Writing the review for one finished stay.
 *
 * <p>Only the overall rating is required. Asking for four sub-scores before
 * accepting anything is how a review form goes unfilled, and one honest number
 * is worth more than four coerced ones.
 */
export function WriteReview({ bookingId, onWritten }: {
  bookingId: string
  onWritten?: (review: Review) => void
}) {
  const t = useT()
  const [existing, setExisting] = useState<Review | null | undefined>(undefined)
  const [open, setOpen] = useState(false)
  const [rating, setRating] = useState(0)
  const [subRatings, setSubRatings] = useState<Record<string, number>>({})
  const [comment, setComment] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const rows = await api.bookingReviews(bookingId)
        // Whichever of the two is ours: the endpoint only returns an unpublished
        // review to its own author, so anything hidden here is mine.
        const mine = rows.find((review) => review.subject === 'SUPPLY')
        if (!cancelled) {
          setExisting(mine ?? null)
        }
      } catch {
        if (!cancelled) {
          setExisting(null)
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [bookingId])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (rating < 1 || saving) {
      return
    }
    setSaving(true)
    setError(null)
    try {
      const written = await api.writeReview(bookingId, {
        rating,
        subRatings: Object.keys(subRatings).length > 0 ? subRatings : undefined,
        comment: comment.trim() || undefined,
      })
      setExisting(written)
      setOpen(false)
      onWritten?.(written)
    } catch (failure) {
      setError(describeError(failure))
    } finally {
      setSaving(false)
    }
  }

  if (existing === undefined) {
    return null
  }
  if (existing) {
    return (
      <p className="muted small" style={{ margin: '8px 0 0' }}>
        {t('reviews.done')}
        {!existing.visible && ` · ${t('reviews.pending')}`}
      </p>
    )
  }
  if (!open) {
    return (
      <button type="button" className="button button--ghost" onClick={() => setOpen(true)}>
        {t('reviews.write')}
      </button>
    )
  }

  return (
    <form className="review-form" onSubmit={submit}>
      <h3 style={{ margin: '0 0 4px' }}>{t('reviews.writeTitle')}</h3>
      <p className="muted small" style={{ marginTop: 0 }}>{t('reviews.writeHelp')}</p>

      <StarPicker label={t('reviews.overall')} value={rating} onChange={setRating} />
      {ASPECTS.map((aspect) => (
        <StarPicker
          key={aspect}
          label={t(`reviews.sub.${aspect}`)}
          value={subRatings[aspect] ?? 0}
          onChange={(score) => setSubRatings((current) => ({ ...current, [aspect]: score }))}
        />
      ))}

      <label className="field">
        <span className="field__label">{t('reviews.comment')}</span>
        <textarea
          className="input"
          rows={4}
          maxLength={4000}
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          placeholder={t('reviews.commentPlaceholder')}
        />
      </label>

      {error && <div className="alert alert--error">{error}</div>}

      <div className="row">
        <button className="button" type="submit" disabled={saving || rating < 1}>
          {saving ? t('common.sending') : t('reviews.submit')}
        </button>
        <button type="button" className="button button--ghost" onClick={() => setOpen(false)}>
          {t('common.cancel')}
        </button>
      </div>
    </form>
  )
}
