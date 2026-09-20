'use client'

import { useT } from '@/lib/i18n'

export function LoginHeading() {
  const t = useT()
  return (
    <>
      <h1>{t('auth.title')}</h1>
      <p className="muted" style={{ marginTop: 0 }}>{t('auth.lead')}</p>
    </>
  )
}
