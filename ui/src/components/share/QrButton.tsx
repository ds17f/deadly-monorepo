"use client";

import { useState, type ReactNode } from "react";
import QrSheet from "./QrSheet";

/**
 * Owns the open state for the QrSheet and lets the caller render any trigger
 * via a render-prop (a hero pill, a row-menu item, …). Example:
 *
 *   <QrButton showId={id} recordingId={rid} subtitle="May 8, 1977">
 *     {(open) => <PillButton onClick={open}>QR</PillButton>}
 *   </QrButton>
 */
export default function QrButton({
  showId,
  recordingId,
  subtitle,
  children,
}: {
  showId: string;
  recordingId?: string | null;
  subtitle?: string;
  children: (open: () => void) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      {children(() => setOpen(true))}
      <QrSheet
        open={open}
        onClose={() => setOpen(false)}
        showId={showId}
        recordingId={recordingId}
        subtitle={subtitle}
      />
    </>
  );
}
