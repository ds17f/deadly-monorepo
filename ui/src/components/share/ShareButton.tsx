"use client";

import { useState, type ReactNode } from "react";
import ShareSheet from "./ShareSheet";

/**
 * Owns the open state for a ShareSheet and lets the caller render any trigger
 * via a render-prop (a hero pill, a row-menu item, …). Example:
 *
 *   <ShareButton showId={id} recordingId={rid} subtitle="May 8, 1977">
 *     {(open) => <PillButton onClick={open}>Share</PillButton>}
 *   </ShareButton>
 */
export default function ShareButton({
  showId,
  recordingId,
  subtitle,
  mode,
  children,
}: {
  showId: string;
  recordingId?: string | null;
  subtitle?: string;
  mode?: "share" | "qr";
  children: (open: () => void) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      {children(() => setOpen(true))}
      <ShareSheet
        open={open}
        onClose={() => setOpen(false)}
        showId={showId}
        recordingId={recordingId}
        mode={mode}
        subtitle={subtitle}
      />
    </>
  );
}
