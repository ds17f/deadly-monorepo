export interface ArchiveTrack {
  filename: string;
  title: string;
  track: number;
  duration: number; // seconds
  url: string; // https://archive.org/download/{identifier}/{filename}
}

export type PlaybackStatus =
  | "idle"
  | "loading"
  | "buffering"
  | "playing"
  | "paused"
  | "error";
