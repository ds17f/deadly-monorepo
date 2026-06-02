import type { NextConfig } from "next";
import fs from "node:fs";
import path from "node:path";

const isDev = process.env.NODE_ENV === "development";

// The pinned data version — names the prebuilt search-index artifact and keys
// its IndexedDB cache, so the client fetches it once per data release.
function dataVersion(): string {
  for (const p of ["../data/version", "data/version"]) {
    try {
      return fs.readFileSync(path.join(process.cwd(), p), "utf-8").trim();
    } catch {
      /* keep looking */
    }
  }
  return "dev";
}

const nextConfig: NextConfig = {
  // Static export for production (served by Caddy); dev server handles rewrites instead
  ...(isDev ? {} : { output: "export" }),
  images: { unoptimized: true },
  trailingSlash: true,
  env: { NEXT_PUBLIC_DATA_VERSION: dataVersion() },
  async rewrites() {
    return [
      { source: "/api/:path*", destination: "http://localhost:3001/api/:path*" },
      { source: "/ws/:path*",  destination: "http://localhost:3001/ws/:path*"  },
    ];
  },
};

export default nextConfig;
