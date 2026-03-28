export interface Song {
  name: string;
  url: string | null;
  segue_into_next: boolean;
}

export interface SetlistSet {
  set_name: string;
  songs: Song[];
}

export interface LineupMember {
  name: string;
  instruments: string;
  image_url: string;
}

export interface TicketImage {
  url: string;
  filename: string;
  side: string;
}

export interface ShowPhoto {
  url: string;
  filename: string;
  thumbnail_url?: string;
}

export interface Show {
  show_id: string;
  band: string;
  date: string;
  venue: string;
  location_raw: string;
  city: string;
  state: string;
  country: string;
  setlist: SetlistSet[] | null;
  lineup: LineupMember[] | null;
  recordings: string[];
  best_recording: string | null;
  recording_count: number;
  avg_rating: number;
  raw_rating: number;
  source_types: Record<string, number>;
  ai_show_review: AiShowReview | null;
  cover_image_url: string | null;
  ticket_images: TicketImage[];
  photos: ShowPhoto[];
}

export interface BandPerformance {
  [member: string]: string;
}

export interface AiShowReview {
  summary?: string;
  blurb?: string;
  review?: string;
  key_highlights?: string[];
  song_highlights?: string[];
  must_listen_sequences?: string[][];
  band_performance?: BandPerformance;
  best_recording?: { identifier: string; reason: string };
  ratings?: {
    ai_rating: number;
    confidence: string;
  };
}
