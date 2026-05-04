import type { NextConfig } from "next";

const isDev = process.env.NODE_ENV === "development";

const nextConfig: NextConfig = {
  // Static export for production (served by Caddy); dev server handles rewrites instead
  ...(isDev ? {} : { output: "export" }),
  images: { unoptimized: true },
  trailingSlash: true,
  async rewrites() {
    return [
      { source: "/api/:path*", destination: "http://localhost:3001/api/:path*" },
      { source: "/ws/:path*",  destination: "http://localhost:3001/ws/:path*"  },
    ];
  },
};

export default nextConfig;
