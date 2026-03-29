export function artistUrl(artistId: string): string {
  return `/${encodeURIComponent(artistId)}`;
}

export function showUrl(artistId: string, showId: string): string {
  return `/${encodeURIComponent(artistId)}/${encodeURIComponent(showId)}`;
}

export function recordingUrl(artistId: string, showId: string, recordingId: string): string {
  return `/${encodeURIComponent(artistId)}/${encodeURIComponent(showId)}/${encodeURIComponent(recordingId)}`;
}
