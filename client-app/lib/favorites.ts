'use client'

import { useCallback, useEffect, useState } from 'react'

/**
 * Saved listings, kept in the browser rather than the account.
 *
 * <p>There is no favorites field on the backend yet, so this is a local-only
 * shortlist: it lets a guest mark places to compare while browsing, but it is
 * per-browser (it won't follow them to a new device or survive clearing site
 * data). If this turns out to be something people actually rely on, making it
 * durable is a small backend addition — a saved-listings table plus an
 * endpoint — not a rewrite of this hook's call sites.
 */
const STORAGE_KEY = 'stay.favorites'
const CHANGE_EVENT = 'stay.favorites.changed'

function readAll(): Set<string> {
  if (typeof window === 'undefined') {
    return new Set()
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return new Set(raw ? (JSON.parse(raw) as string[]) : [])
  } catch {
    // Corrupt or blocked storage: behave as if nothing is saved yet.
    return new Set()
  }
}

function writeAll(ids: Set<string>) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify([...ids]))
  } catch {
    // Storage full or blocked (private browsing): the toggle just won't
    // persist across a reload. Not worth surfacing as an error.
  }
  // Same-tab listeners (other cards for the same listing, if any) don't get
  // the browser's own `storage` event, which only fires in *other* tabs.
  window.dispatchEvent(new Event(CHANGE_EVENT))
}

/**
 * Whether one listing is saved, plus a toggle for it. Stays in sync with any
 * other card for the same listing on the page, and with other tabs.
 */
export function useFavorite(listingId: string): [boolean, () => void] {
  const [saved, setSaved] = useState(() => readAll().has(listingId))

  useEffect(() => {
    const sync = () => setSaved(readAll().has(listingId))
    sync()
    window.addEventListener(CHANGE_EVENT, sync)
    window.addEventListener('storage', sync)
    return () => {
      window.removeEventListener(CHANGE_EVENT, sync)
      window.removeEventListener('storage', sync)
    }
  }, [listingId])

  const toggle = useCallback(() => {
    const all = readAll()
    if (all.has(listingId)) {
      all.delete(listingId)
    } else {
      all.add(listingId)
    }
    writeAll(all)
  }, [listingId])

  return [saved, toggle]
}
