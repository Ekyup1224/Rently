'use client'

import { useT } from '@/lib/i18n'

export function SiteFooter() {
  const t = useT()
  return (
    <footer className="site-footer">
      <span>{t('footer.rights')}</span>
    </footer>
  )
}
