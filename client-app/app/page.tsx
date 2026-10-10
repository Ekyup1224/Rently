import { FeaturedStays } from '@/components/FeaturedStays'
import { HomeHero } from '@/components/HomeHero'
import { HostCallout } from '@/components/HostCallout'
import { WelcomeBanner } from '@/components/WelcomeBanner'

// No `title` override here on purpose. A root layout's title.template applies
// only to child segments, not to the page sharing its own segment — so a title
// set here would replace the branded default outright and leave the landing
// page, the most-shared URL of the lot, as the one tab with no product name in
// it. The layout's `default` already reads "Rently — houses and hotels in
// Mongolia", which is exactly what this page wants.

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
