'use client'

import { useT } from '@/lib/i18n'
import { SearchPanel } from './SearchPanel'

/**
 * The one place colour is allowed to shout, so the stays below can be the
 * brightest thing on the page.
 *
 * <p>A search box on its own tells a first-time visitor nothing about what is
 * here, so it stays one row deep and the stays themselves follow immediately.
 */
export function HomeHero() {
  const t = useT()
  return (
    <section className="hero">
      <h1>{t('home.title')}</h1>
      <p>{t('home.subtitle')}</p>
      <SearchPanel />
    </section>
  )
}
