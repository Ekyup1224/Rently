'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { messages, type Locale } from './messages'

/**
 * Language for the guest app.
 *
 * <p>Mongolian is the default, not English. This is a Mongolian marketplace, and
 * a guest in Ulaanbaatar should not have to find a switch before the site reads
 * naturally; the switch is there for the visitors who do need it.
 *
 * <p>The choice is kept per browser rather than on the account, so it works
 * before anyone signs in — which is when most people first read the site.
 */
const STORAGE_KEY = 'stay.locale'
const DEFAULT_LOCALE: Locale = 'mn'

interface LanguageValue {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: (key: string, values?: Record<string, string | number>) => string
}

const LanguageContext = createContext<LanguageValue | null>(null)

/** Fills {name} placeholders. */
function interpolate(template: string, values?: Record<string, string | number>): string {
  if (!values) {
    return template
  }
  return template.replace(/\{(\w+)\}/g, (whole, name: string) =>
    name in values ? String(values[name]) : whole)
}

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  // Always start on the default so the server and the first client render agree;
  // the stored choice is applied in an effect, after hydration.
  const [locale, setLocaleState] = useState<Locale>(DEFAULT_LOCALE)

  useEffect(() => {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (stored === 'mn' || stored === 'en') {
      setLocaleState(stored)
    }
  }, [])

  useEffect(() => {
    document.documentElement.lang = locale
  }, [locale])

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next)
    window.localStorage.setItem(STORAGE_KEY, next)
  }, [])

  const t = useCallback((key: string, values?: Record<string, string | number>) => {
    // English is the fallback because it is the language the keys were written
    // in: a missing Mongolian string shows real words rather than a raw key.
    const text = messages[locale][key] ?? messages.en[key] ?? key
    return interpolate(text, values)
  }, [locale])

  const value = useMemo(() => ({ locale, setLocale, t }), [locale, setLocale, t])

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>
}

export function useLanguage(): LanguageValue {
  const value = useContext(LanguageContext)
  if (!value) {
    throw new Error('useLanguage must be used inside a LanguageProvider')
  }
  return value
}

/** The common case: just the translate function. */
export function useT() {
  return useLanguage().t
}
