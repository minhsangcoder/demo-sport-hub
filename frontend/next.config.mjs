/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  experimental: { proxyClientMaxBodySize: '31mb' },
  // app/api/v1/[...segments] owns forwarding and reads GATEWAY_URL at runtime.
  // A beforeFiles rewrite bypasses that handler and freezes its URL at build time.
  images: {
    unoptimized: true,
  },
}

export default nextConfig
