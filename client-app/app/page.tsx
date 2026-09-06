import type { Metadata } from 'next'
import { SearchPanel } from '@/components/SearchPanel'
import { WelcomeBanner } from '@/components/WelcomeBanner'

export const metadata: Metadata = {
  title: 'Houses and hotels in Mongolia',
}

export default function HomePage() {
  return (
    <>
      <h1>Stay anywhere in Mongolia</h1>
      <p className="muted" style={{ marginTop: 0 }}>
        Independent houses and apartments alongside hotel rooms — searched, compared
        and booked in one place.
      </p>

      <WelcomeBanner />
      <SearchPanel />

      <div className="card">
        <h2 className="card__title">Renting out a place?</h2>
        <p className="muted small" style={{ marginTop: 0, marginBottom: 12 }}>
          List a house or apartment as an individual, or register a hotel with room
          types and rate management. Applications are reviewed by our team.
        </p>
        <a className="button button--ghost" href="/account#host">Become a host</a>
      </div>
    </>
  )
}
