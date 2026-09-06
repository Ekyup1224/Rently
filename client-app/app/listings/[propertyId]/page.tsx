import type { Metadata } from 'next'
import { Suspense } from 'react'
import { ListingDetailView } from '@/components/ListingDetailView'

export const metadata: Metadata = {
  title: 'Stay details',
}

export default async function ListingPage({ params }: PageProps<'/listings/[propertyId]'>) {
  const { propertyId } = await params
  return (
    <Suspense fallback={<p className="muted">Loading…</p>}>
      <ListingDetailView propertyId={propertyId} />
    </Suspense>
  )
}
