import type { Metadata, Viewport } from 'next'
import { PT_Sans, PT_Serif } from 'next/font/google'
import { AuthProvider } from '@/components/AuthProvider'
import { SiteHeader } from '@/components/SiteHeader'
import { LanguageProvider } from '@/lib/i18n'
import { SiteFooter } from '@/components/SiteFooter'
import './globals.css'

// PT Sans/PT Serif, not the more common Inter/Playfair pairing: this app is
// Mongolian-first (lang="mn"), and PT's Cyrillic coverage was built in from
// the start rather than added later, unlike several trendier alternatives.
const sans = PT_Sans({
  subsets: ['latin', 'cyrillic'],
  weight: ['400', '700'],
  variable: '--font-pt-sans',
  display: 'swap',
})

// The warm serif used for hero headings only — see globals.css's --font-display.
const display = PT_Serif({
  subsets: ['latin', 'cyrillic'],
  weight: ['400', '700'],
  variable: '--font-pt-serif',
  display: 'swap',
})

export const metadata: Metadata = {
  title: {
    default: 'Rently — houses and hotels in Mongolia',
    template: '%s · Rently',
  },
  description: 'Search and book houses, apartments and hotel rooms in one place.',
  appleWebApp: { capable: true, statusBarStyle: 'default', title: 'Rently' },
  icons: { icon: '/icon-192.png', apple: '/apple-icon.png' },
}

export const viewport: Viewport = {
  themeColor: '#b84b2a',
  width: 'device-width',
  initialScale: 1,
}

export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html lang="mn" className={`${sans.variable} ${display.variable}`}>
      <body>
        <LanguageProvider>
          <AuthProvider>
            <SiteHeader />
            <main className="page">{children}</main>
            <SiteFooter />
          </AuthProvider>
        </LanguageProvider>
      </body>
    </html>
  )
}
