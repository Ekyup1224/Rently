'use client'

import { useLanguage } from '@/lib/i18n'

/**
 * Мон / Eng, in the header.
 *
 * <p>Two visible buttons rather than a dropdown: there are only two languages,
 * and someone who cannot read the current one cannot be expected to find a menu
 * labelled in it.
 */
export function LanguageSwitch() {
  const { locale, setLocale, t } = useLanguage()

  return (
    <div className="lang-switch" role="group" aria-label={t('nav.language')}>
      <button type="button" aria-pressed={locale === 'mn'} onClick={() => setLocale('mn')}>
        Мон
      </button>
      <button type="button" aria-pressed={locale === 'en'} onClick={() => setLocale('en')}>
        Eng
      </button>
    </div>
  )
}
