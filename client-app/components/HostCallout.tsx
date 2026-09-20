'use client'

import { useT } from '@/lib/i18n'

export function HostCallout() {
  const t = useT()
  return (
    <div className="card">
      <h2 className="card__title">{t('home.hostTitle')}</h2>
      <p className="muted small" style={{ marginTop: 0, marginBottom: 12 }}>
        {t('home.hostBody')}
      </p>
      <a className="button button--ghost" href="/account#host">{t('home.hostCta')}</a>
    </div>
  )
}
