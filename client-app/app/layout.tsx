import type { Metadata, Viewport } from 'next'
import { AuthProvider } from '@/components/AuthProvider'
import { SiteHeader } from '@/components/SiteHeader'
import { LanguageProvider } from '@/lib/i18n'
import { SiteFooter } from '@/components/SiteFooter'
import './globals.css'

export const metadata: Metadata = {
  title: {
    default: 'Stay — houses and hotels in Mongolia',
    template: '%s · Stay',
  },
  description: 'Search and book houses, apartments and hotel rooms in one place.',
  appleWebApp: { capable: true, statusBarStyle: 'default', title: 'Stay' },
  icons: { icon: '/icon-192.png', apple: '/apple-icon.png' },
}

export const viewport: Viewport = {
  themeColor: '#2563eb',
  width: 'device-width',
  initialScale: 1,
}

export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html lang="mn">
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
