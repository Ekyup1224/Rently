import type { Metadata } from 'next'
import { Suspense } from 'react'
import { SearchResults } from '@/components/SearchResults'

export const metadata: Metadata = {
  title: 'Search stays',
}

export default function SearchPage() {
  return (
    <Suspense fallback={<p className="muted">Loading search…</p>}>
      <SearchResults />
    </Suspense>
  )
}
