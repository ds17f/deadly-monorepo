"use client";

import { useCallback } from "react";
import { useToast } from "@/components/ui/ToastProvider";
import { buildShareUrl, copyToClipboard } from "@/lib/share";

// The "Share" action shared by the show hero and the /me library rows: copy
// the show's public link to the clipboard and flash a toast. (The scannable
// QR lives behind its own button, which opens the QrSheet.)
export function useShareLink(): (
  showId: string,
  recordingId?: string | null,
) => Promise<void> {
  const { showToast } = useToast();
  return useCallback(
    async (showId, recordingId) => {
      const ok = await copyToClipboard(buildShareUrl(showId, recordingId));
      showToast(ok ? "Link copied to clipboard" : "Couldn't copy — try again");
    },
    [showToast],
  );
}
