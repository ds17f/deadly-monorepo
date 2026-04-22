const EMOJI = [
  "🐻", "🦊", "🐼", "🐨", "🦁", "🐯", "🐸", "🐵",
  "🦉", "🦅", "🐢", "🐙", "🦋", "🐝", "🐬", "🦈",
  "🐺", "🦇", "🐲", "🦎", "🐞", "🦀", "🐡", "🦑",
  "🎸", "🎹", "🎺", "🥁", "🎻", "🎷", "🎵", "🎤",
  "⚡", "🌙", "🌟", "🔥", "💎", "🌈", "🍄", "🌵",
  "🎲", "🧩", "🎯", "🪁", "🛸", "🚀", "⛵", "🏔️",
];

function hashCode(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = ((h << 5) - h + s.charCodeAt(i)) | 0;
  }
  return Math.abs(h);
}

export function emojiForId(id: string): string {
  const h = hashCode(id);
  const a = h % EMOJI.length;
  const b = Math.floor(h / EMOJI.length) % EMOJI.length;
  return `${EMOJI[a]}${EMOJI[b === a ? (b + 1) % EMOJI.length : b]}`;
}
