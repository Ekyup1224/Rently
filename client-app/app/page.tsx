import type { Metadata } from 'next'
import { FeaturedStays } from '@/components/FeaturedStays'
import { HomeHero } from '@/components/HomeHero'
import { HostCallout } from '@/components/HostCallout'
import { WelcomeBanner } from '@/components/WelcomeBanner'

export const metadata: Metadata = {
  title: 'Houses and hotels in Mongolia',
}

export default function HomePage() {
  return (
    <>
      <WelcomeBanner />
      <HomeHero />
      <FeaturedStays />
      <HostCallout />
    </>
  )
}
