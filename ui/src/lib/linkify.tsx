import React from "react";

// Render a notification body with tappable links (decision D). Supports
// markdown links `[label](url)` AND bare http(s) URLs, restricted to the
// http/https schemes. Anchors are built from parsed tokens — never via
// dangerouslySetInnerHTML — so an admin typo can't inject markup.

// `[label](url)` OR a bare http(s):// URL. The markdown alt comes first so a
// URL already inside a markdown link isn't double-matched.
const TOKEN = /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|(https?:\/\/[^\s]+)/g;

function isSafe(url: string): boolean {
  return /^https?:\/\//i.test(url);
}

/** Trailing punctuation that usually isn't part of a bare URL. */
function trimTrailing(url: string): { url: string; trailing: string } {
  const m = url.match(/[).,!?;:]+$/);
  if (!m) return { url, trailing: "" };
  return { url: url.slice(0, -m[0].length), trailing: m[0] };
}

function anchor(href: string, label: string, key: number): React.ReactNode {
  return (
    <a
      key={key}
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="text-deadly-accent underline underline-offset-2 hover:text-white"
    >
      {label}
    </a>
  );
}

/** Parse a plain-text body into React nodes with inline links. */
export function linkify(body: string): React.ReactNode[] {
  const nodes: React.ReactNode[] = [];
  let last = 0;
  let key = 0;
  let match: RegExpExecArray | null;

  TOKEN.lastIndex = 0;
  while ((match = TOKEN.exec(body)) !== null) {
    const [full, mdLabel, mdUrl, bareUrl] = match;
    if (match.index > last) nodes.push(body.slice(last, match.index));

    if (mdUrl && isSafe(mdUrl)) {
      nodes.push(anchor(mdUrl, mdLabel, key++));
    } else if (bareUrl && isSafe(bareUrl)) {
      const { url, trailing } = trimTrailing(bareUrl);
      nodes.push(anchor(url, url, key++));
      if (trailing) nodes.push(trailing);
    } else {
      nodes.push(full);
    }
    last = match.index + full.length;
  }
  if (last < body.length) nodes.push(body.slice(last));
  return nodes;
}
