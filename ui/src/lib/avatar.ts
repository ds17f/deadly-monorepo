// Client-side avatar downscaling. Profile pictures are shown small (a few
// dozen px), so we cover-crop to a square and re-encode at AVATAR_SIZE before
// upload. This keeps the upload, the stored bytes, and every later fetch tiny
// (~10–25 KB) regardless of how huge the source file is — the bandwidth win.

export const AVATAR_SIZE = 256; // px, square
const QUALITY = 0.82;

// Loads a user-selected image file and returns a small square WebP blob,
// cover-cropped (centered) so non-square sources fill the frame without
// distortion. Falls back to JPEG if the browser can't encode WebP.
export async function downscaleToAvatar(file: File): Promise<Blob> {
  const bitmap = await loadBitmap(file);
  try {
    const side = Math.min(bitmap.width, bitmap.height);
    const sx = (bitmap.width - side) / 2;
    const sy = (bitmap.height - side) / 2;

    const canvas = document.createElement("canvas");
    canvas.width = AVATAR_SIZE;
    canvas.height = AVATAR_SIZE;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("Canvas 2D context unavailable");
    ctx.imageSmoothingQuality = "high";
    ctx.drawImage(bitmap, sx, sy, side, side, 0, 0, AVATAR_SIZE, AVATAR_SIZE);

    return (
      (await canvasToBlob(canvas, "image/webp")) ??
      (await canvasToBlob(canvas, "image/jpeg")) ??
      (() => {
        throw new Error("Image encoding failed");
      })()
    );
  } finally {
    bitmap.close?.();
  }
}

async function loadBitmap(file: File): Promise<ImageBitmap & { close?: () => void }> {
  if (typeof createImageBitmap === "function") {
    return createImageBitmap(file);
  }
  // Fallback path for browsers without createImageBitmap.
  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const el = new Image();
      el.onload = () => resolve(el);
      el.onerror = () => reject(new Error("Could not load image"));
      el.src = url;
    });
    return img as unknown as ImageBitmap;
  } finally {
    URL.revokeObjectURL(url);
  }
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string): Promise<Blob | null> {
  return new Promise((resolve) => {
    canvas.toBlob((blob) => resolve(blob?.type === type ? blob : null), type, QUALITY);
  });
}
