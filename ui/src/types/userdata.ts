export interface FavoriteShow {
  showId: string;
  addedAt: number;
  isPinned: boolean;
  lastAccessedAt?: number | null;
  tags?: string[] | null;
  notes?: string | null;
  preferredRecordingId?: string | null;
  recordingQuality?: number | null;
  playingQuality?: number | null;
  customRating?: number | null;
  // Display metadata merged in by the API show catalog (GET
  // /api/user/favorites/shows). Absent for shows not in the index.
  date?: string | null;
  venue?: string | null;
  city?: string | null;
  state?: string | null;
  country?: string | null;
  image?: string | null;
  bestRecordingId?: string | null;
}

export interface FavoriteTrack {
  showId: string;
  trackTitle: string;
  trackNumber?: number | null;
  recordingId?: string | null;
}

export interface ShowReview {
  showId: string;
  notes?: string | null;
  overallRating?: number | null;
  recordingQuality?: number | null;
  playingQuality?: number | null;
  reviewedRecordingId?: string | null;
  playerTags?: PlayerTag[] | null;
}

export interface PlayerTag {
  playerName: string;
  instruments?: string | null;
  isStandout: boolean;
  notes?: string | null;
}

export interface RecordingPreference {
  showId: string;
  recordingId: string;
}

export interface UserSettings {
  includeShowsWithoutRecordings?: boolean | null;
  favoritesDisplayMode?: string | null;
  forceOnline?: boolean | null;
  sourceBadgeStyle?: string | null;
  shareAttachImage?: boolean | null;
  eqEnabled?: boolean | null;
  eqPreset?: string | null;
  eqBandLevels?: string | null;
}

export interface PlaybackPosition {
  showId: string;
  recordingId: string;
  trackIndex: number;
  positionMs: number;
}

export interface RecentShow {
  showId: string;
  lastPlayedAt: number;
  firstPlayedAt: number;
  totalPlayCount: number;
  // Display metadata merged in by the API show catalog. Absent for shows
  // not in the index; clients fall back to the date-prefixed showId.
  date?: string | null;
  venue?: string | null;
  city?: string | null;
  state?: string | null;
  country?: string | null;
  rating?: number;
  recordingCount?: number;
  image?: string | null;
  bestRecordingId?: string | null;
}

export interface UserDataBackupV3 {
  version: 3;
  exportedAt: number;
  app: string;
  favorites: {
    shows: FavoriteShow[];
    tracks: FavoriteTrack[];
  };
  reviews: ShowReview[];
  recordingPreferences: RecordingPreference[];
  settings: UserSettings | null;
  recentShows?: RecentShow[];
  playbackPosition?: PlaybackPosition | null;
}
