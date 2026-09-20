import type { Metadata } from 'next'
import { Suspense } from 'react'
import { SearchResults } from '@/components/SearchResults'

export const metadata: Metadata = {
  title: 'Search stays',
}

export default function SearchPage() {
  return (
    <Suspense fallback={null}>
      <SearchResults />
    </Suspense>
  )
}
