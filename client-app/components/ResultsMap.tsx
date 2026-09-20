'use client'

import { useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { LngLatBounds, Map as MapLibreMap, Marker, NavigationControl, Popup } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { formatMoney } from '@/lib/format'
import type { ListingSummary } from '@/lib/types'

/**
 * Map of the current results.
 *
 * <p>Tiles come from OpenStreetMap's public servers, which is fine for
 * development but **not permitted for production traffic** — a keyed provider
 * (MapTiler, Stadia, or self-hosted tiles) is needed before launch. The style is
 * defined inline for exactly that reason: swapping the source is a one-object
 * change here, with no other code touched.
 */
export function ResultsMap({ listings }: { listings: ListingSummary[] }) {
  const router = useRouter()
  const container = useRef<HTMLDivElement | null>(null)
  const map = useRef<MapLibreMap | null>(null)

  useEffect(() => {
    if (!container.current || map.current) {
      return
    }

    map.current = new MapLibreMap({
      container: container.current,
      style: {
        version: 8,
        sources: {
          osm: {
            type: 'raster',
            tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
            tileSize: 256,
            attribution: '© OpenStreetMap contributors',
          },
        },
        layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
      },
      // Ulaanbaatar, until the results say otherwise.
      center: [106.9177, 47.8864],
      zoom: 10,
    })
    map.current.addControl(new NavigationControl({ showCompass: false }), 'top-right')

    return () => {
      map.current?.remove()
      map.current = null
    }
  }, [])

  // Markers are rebuilt whenever the result set changes.
  useEffect(() => {
    const instance = map.current
    if (!instance || listings.length === 0) {
      return
    }

    const markers = listings.map((listing) => {
      const label = document.createElement('button')
      label.type = 'button'
      label.textContent = formatMoney(listing.nightlyFrom, listing.currency)
      label.style.cssText = 'background:#fff;border:1px solid #d0d3d9;border-radius:999px;'
        + 'padding:4px 10px;font:600 12px system-ui;cursor:pointer;box-shadow:0 1px 4px rgba(0,0,0,.2)'
      label.onclick = () => router.push(
        `${listing.supplyType === 'HOTEL' ? '/hotels' : '/listings'}/${listing.id}`)

      return new Marker({ element: label })
        .setLngLat([listing.longitude!, listing.latitude!])
        .setPopup(new Popup({ offset: 16, closeButton: false })
          .setText(listing.title))
        .addTo(instance)
    })

    // Frame every result, unless there is only one to look at.
    if (listings.length > 1) {
      const bounds = new LngLatBounds()
      listings.forEach((listing) => bounds.extend([listing.longitude!, listing.latitude!]))
      instance.fitBounds(bounds, { padding: 60, maxZoom: 14, duration: 0 })
    } else {
      instance.setCenter([listings[0].longitude!, listings[0].latitude!])
      instance.setZoom(13)
    }

    return () => markers.forEach((marker) => marker.remove())
  }, [listings, router])

  return (
    <div
      ref={container}
      style={{ height: 320, borderRadius: 12, overflow: 'hidden', marginBottom: 16,
               border: '1px solid var(--line)' }}
    />
  )
}
