import type { NextConfig } from 'next'

/** Spring Boot origin. Set BACKEND_ORIGIN in deployed environments. */
const backendOrigin = process.env.BACKEND_ORIGIN ?? 'http://localhost:8080'

/**
 * Where listing photos are served from: MinIO locally, S3 or a CDN in production.
 * next/image will only optimize hosts named here, so this has to track the
 * backend's app.storage.s3.public-base-url.
 */
const mediaOrigin = new URL(process.env.MEDIA_ORIGIN ?? 'http://localhost:9000')

/**
 * Next refuses to optimize images from a private IP, which is the right default:
 * the optimizer fetches server-side, so an attacker-supplied URL would otherwise
 * be an SSRF primitive. The MinIO dev container lives on localhost, so the
 * exception is granted only when the media origin is itself local — a deployed
 * environment pointing at S3 or a CDN keeps the protection.
 */
const mediaIsLocal = ['localhost', '127.0.0.1', '::1', '[::1]'].includes(mediaOrigin.hostname)

const nextConfig: NextConfig = {
  images: {
    dangerouslyAllowLocalIP: mediaIsLocal,
    remotePatterns: [{
      protocol: mediaOrigin.protocol.replace(':', '') as 'http' | 'https',
      hostname: mediaOrigin.hostname,
      port: mediaOrigin.port,
      pathname: '/**',
    }],
  },
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
