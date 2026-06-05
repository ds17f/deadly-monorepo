import type { MetadataRoute } from "next";

// Required for the metadata route to be emitted as a static file under
// next.config `output: "export"`.
export const dynamic = "force-static";

// Web app manifest for installability ("Add to Home Screen" / Android "Install
// app"). Android Chrome honors this fully; iOS leans more on the legacy
// apple-mobile-web-app-* meta tags (see appleWebApp in layout.tsx). Brand
// colors mirror --color-deadly-bg (#121212) so the splash/status chrome
// matches the app background.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "The Deadly — Every Grateful Dead Concert",
    short_name: "The Deadly",
    description:
      "Every Grateful Dead concert — setlists, recordings, and reviews for 2,300+ shows.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#121212",
    theme_color: "#121212",
    orientation: "portrait",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
    ],
  };
}
