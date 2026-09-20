import type { Metadata } from 'next'
import { TripsView } from '@/components/TripsView'

export const metadata: Metadata = {
  title: 'Trips',
}

export default function TripsPage() {
  return <TripsView />
}
