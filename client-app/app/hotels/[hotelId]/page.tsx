import type { Metadata } from 'next'
import { Suspense } from 'react'
import { HotelDetailView } from '@/components/HotelDetailView'

export const metadata: Metadata = {
  title: 'Hotel details',
}

export default async function HotelPage({ params }: PageProps<'/hotels/[hotelId]'>) {
  const { hotelId } = await params
  return (
    <Suspense fallback={<p className="muted">Loading…</p>}>
      <HotelDetailView hotelId={hotelId} />
    </Suspense>
  )
}
