export interface Artist {
  id: string;
  name: string;
  short_name: string | null;
  active_from: number | null;
  active_to: number | null;
  is_active: number;
  description: string | null;
  image_url: string | null;
  show_count: number;
  recording_count: number;
}
