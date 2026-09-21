import type { MetadataRoute } from 'next'

/**
 * Web app manifest. A valid manifest served over HTTPS is what makes the app
 * installable to a home screen; no service worker is needed for that, and one is
 * deliberately left out until push notifications land in Step 5.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Rently — houses and hotels in Mongolia',
    short_name: 'Rently',
    description: 'Search and book houses, apartments and hotel rooms in one place.',
    start_url: '/',
    display: 'standalone',
    orientation: 'portrait',
    background_color: '#ffffff',
    theme_color: '#b84b2a',
    lang: 'mn',
    icons: [
      { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
      { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
      { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  }
}
