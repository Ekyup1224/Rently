'use client'

import { formatMoney } from '@/lib/format'
import type { Quote } from '@/lib/types'
import { useLanguage } from '@/lib/i18n'

/**
 * The itemized total, shared by the house and hotel booking widgets.
 *
 * <p>Every figure comes from the server's quote; nothing here multiplies or sums.
 * When the nights are not all the same price the multiplication is dropped rather
 * than averaged, because printing `average × nights` produces a line that does
 * not equal the subtotal below it.
 */
export function QuoteBreakdown({ quote, roomsLabel }: { quote: Quote; roomsLabel?: string }) {
  const { t, locale } = useLanguage()
  const mixedNightlyRates = new Set(quote.nightlyRates.map((rate) => rate.amount)).size > 1
  const nightsLabel = quote.nights === 1
    ? t('common.night_one') : t('common.nights', { count: quote.nights })

  return (
    <div style={{ borderTop: '1px solid var(--line)', paddingTop: 12, marginBottom: 12 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span>
          {mixedNightlyRates
            ? nightsLabel
            : `${formatMoney(quote.nightlyRates[0].amount, quote.currency)} × ${nightsLabel}`}
          {roomsLabel ? ` · ${roomsLabel}` : ''}
        </span>
        <span>{formatMoney(quote.nightlySubtotal, quote.currency)}</span>
      </div>

      {mixedNightlyRates && (
        <details style={{ marginTop: 6 }}>
          <summary className="muted small" style={{ cursor: 'pointer' }}>
            {t('book.mixedRates')}
          </summary>
          <ul className="small muted" style={{ paddingInlineStart: 18, margin: '4px 0 0' }}>
            {quote.nightlyRates.map((rate) => (
              <li key={rate.date}>
                {new Date(rate.date + 'T00:00:00').toLocaleDateString(
                  locale === 'mn' ? 'mn-MN' : 'en-US',
                  { weekday: 'short', day: 'numeric', month: 'short' })}
                {' — '}{formatMoney(rate.amount, quote.currency)}
                {rate.overridden ? ` (${t('book.specialRate')})` : ''}
              </li>
            ))}
          </ul>
        </details>
      )}
      {quote.cleaningFee > 0 && (
        <div className="row" style={{ justifyContent: 'space-between', marginTop: 6 }}>
          <span>{t('book.cleaning')}</span>
          <span>{formatMoney(quote.cleaningFee, quote.currency)}</span>
        </div>
      )}
      {quote.guestServiceFee > 0 && (
        <div className="row" style={{ justifyContent: 'space-between', marginTop: 6 }}>
          <span>{t('book.serviceFee')}</span>
          <span>{formatMoney(quote.guestServiceFee, quote.currency)}</span>
        </div>
      )}
      {quote.tax > 0 && (
        <div className="row" style={{ justifyContent: 'space-between', marginTop: 6 }}>
          <span>{t('book.tax')}</span>
          <span>{formatMoney(quote.tax, quote.currency)}</span>
        </div>
      )}
      <div
        className="row"
        style={{ justifyContent: 'space-between', marginTop: 10, paddingTop: 10,
                 borderTop: '1px solid var(--line)', fontWeight: 650 }}
      >
        <span>{t('book.total')}</span>
        <span>{formatMoney(quote.total, quote.currency)}</span>
      </div>

      <details style={{ marginTop: 10 }}>
        <summary className="muted small" style={{ cursor: 'pointer' }}>
          {t('book.cancelTerms', { policy: t(`policy.${quote.cancellationPolicy}`) })}
        </summary>
        <ul className="small muted" style={{ paddingInlineStart: 18, marginBottom: 0 }}>
          {quote.refundSchedule.map((window) => (
            <li key={window.cancelBefore}>
              {window.description} — {t('book.refundBack', {
                amount: formatMoney(window.refundAmount, quote.currency),
              })}
            </li>
          ))}
        </ul>
      </details>
    </div>
  )
}
