import type { Metadata, Viewport } from 'next'
import { AuthProvider } from '@/components/AuthProvider'
import { SiteHeader } from '@/components/SiteHeader'
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
  themeColor: '#1668dc',
  width: 'device-width',
  initialScale: 1,
}

export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html lang="mn">
      <body>
        <AuthProvider>
          <SiteHeader />
          <main className="page">{children}</main>
          <footer className="site-footer">
            <span>Prices in MNT · Монгол / English</span>
          </footer>
        </AuthProvider>
      </body>
    </html>
  )
}
