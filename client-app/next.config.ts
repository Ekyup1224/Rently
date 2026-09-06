import type { NextConfig } from 'next'

/** Spring Boot origin. Set BACKEND_ORIGIN in deployed environments. */
const backendOrigin = process.env.BACKEND_ORIGIN ?? 'http://localhost:8080'

const nextConfig: NextConfig = {
  // Same-origin API calls in the browser: no CORS preflight, and the access token
  // never travels to a third origin.
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${backendOrigin}/api/:path*` }]
  },
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
        ],
      },
    ]
  },
}

export default nextConfig
