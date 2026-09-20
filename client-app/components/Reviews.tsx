'use client'

import { useEffect, useState } from 'react'
import { api, describeError } from '@/lib/api'
import { useLanguage } from '@/lib/i18n'
import { formatDate } from '@/lib/format'
import type { Review, SupplyType } from '@/lib/types'

/** Rounded to the nearest half, which is as fine as five stars can show. */
function stars(rating: number): string {
  const whole = Math.floor(rating)
  const half = rating - whole >= 0.5
  return '★'.repeat(whole) + (half ? '½' : '')
}

/**
 * The rating beside a title.
 *
 * <p>Renders nothing when there are no reviews rather than showing a zero — a new
 * listing has not been rated badly, it has not been rated.
 */
export function RatingBadge({ average, count, size = 'normal' }: {
  average?: number | null
  count: number
  size?: 'normal' | 'small'
}) {
  const { t } = useLanguage()
  if (!average || count === 0) {
    return <span className="muted small">{t('reviews.none')}</span>
  }
  return (
    <span className={size === 'small' ? 'rating rating--small' : 'rating'}>
      <span className="rating__stars" aria-hidden="true">{stars(average)}</span>
      <strong>{average.toFixed(1)}</strong>
      <span className="muted">({count})</span>
    </span>
  )
}

const SUB_RATING_KEYS = ['cleanliness', 'accuracy', 'location', 'value'] as const

/** The four sub-scores, averaged across everything shown. */
function subRatingAverages(reviews: Review[]): Array<[string, number]> {
  return SUB_RATING_KEYS.flatMap((key) => {
    const scores = reviews
      .map((review) => review.subRatings?.[key])
      .filter((score): score is number => typeof score === 'number')
    if (scores.length === 0) {
      return []
    }
    const mean = scores.reduce((sum, score) => sum + score, 0) / scores.length
    return [[key, mean] as [string, number]]
  })
}

/**
 * What guests said about a place.
 *
 * <p>Only shows reviews a guest wrote about the stay; the host's review of the
 * guest lives on the guest's own account, not on the listing.
 */
export function ReviewList({ supplyType, supplyId, average, count }: {
  supplyType: SupplyType
  supplyId: string
  average?: number | null
  count: number
}) {
  const { t, locale } = useLanguage()
  const [reviews, setReviews] = useState<Review[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const page = supplyType === 'HOTEL'
          ? await api.hotelReviews(supplyId)
          : await api.listingReviews(supplyId)
        if (!cancelled) {
          setReviews(page.rows)
        }
      } catch (failure) {
        if (!cancelled) {
          setError(describeError(failure))
        }
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [supplyType, supplyId])

  if (error) {
    return null
  }
  if (!reviews) {
    return (
      <div className="card">
        <h2 className="card__title">{t('reviews.title')}</h2>
        <p className="muted small" style={{ margin: 0 }}>{t('common.loading')}</p>
      </div>
    )
  }

  const breakdown = subRatingAverages(reviews)

  return (
    <div className="card">
      <div className="section-head" style={{ marginBottom: 12 }}>
        <h2 className="card__title" style={{ marginBottom: 0 }}>{t('reviews.title')}</h2>
        <RatingBadge average={average} count={count} />
      </div>

      {reviews.length === 0 ? (
        <p className="muted small" style={{ margin: 0 }}>{t('reviews.empty')}</p>
      ) : (
        <>
          {breakdown.length > 0 && (
            <div className="rating-breakdown">
              {breakdown.map(([key, score]) => (
                <div key={key} className="rating-breakdown__row">
                  <span className="muted small">{t(`reviews.sub.${key}`)}</span>
                  <span className="rating-breakdown__bar" aria-hidden="true">
                    <span style={{ width: `${(score / 5) * 100}%` }} />
                  </span>
                  <strong className="small">{score.toFixed(1)}</strong>
                </div>
              ))}
            </div>
          )}

          <ul className="review-list">
            {reviews.map((review) => (
              <li key={review.id} className="review">
                <div className="review__head">
                  <strong>{review.authorName}</strong>
                  <span className="rating rating--small">
                    <span className="rating__stars" aria-hidden="true">{stars(review.rating)}</span>
                  </span>
                  <span className="muted small">
                    {formatDate(review.createdAt, locale)}
                  </span>
                </div>
                {review.comment && <p className="review__body">{review.comment}</p>}
                {review.response && (
                  <div className="review__response">
                    <span className="muted small">{t('reviews.hostReplied')}</span>
                    <p style={{ margin: '4px 0 0' }}>{review.response}</p>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
