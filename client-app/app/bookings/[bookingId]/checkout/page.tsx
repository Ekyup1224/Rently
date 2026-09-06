import type { Metadata } from 'next'
import { CheckoutView } from '@/components/CheckoutView'

export const metadata: Metadata = {
  title: 'Checkout',
}

export default async function CheckoutPage({
  params,
}: PageProps<'/bookings/[bookingId]/checkout'>) {
  const { bookingId } = await params
  return <CheckoutView bookingId={bookingId} />
}
