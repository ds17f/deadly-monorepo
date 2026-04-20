const LEVEL_COLORS: Record<string, string> = {
  info: "#2eb67d",
  warn: "#ecb22e",
  error: "#e01e5a",
};

export function notify(
  title: string,
  body: string,
  level: "info" | "warn" | "error" = "info",
): void {
  const url = process.env.SLACK_WEBHOOK_URL;
  const message = `[${level}] ${title}: ${body}`;

  if (!url) {
    if (level === "error") console.error(message);
    else if (level === "warn") console.warn(message);
    else console.info(message);
    return;
  }

  const payload = {
    attachments: [
      {
        color: LEVEL_COLORS[level],
        blocks: [
          {
            type: "section",
            text: { type: "mrkdwn", text: `*${title}*\n${body}` },
          },
        ],
      },
    ],
  };

  fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  }).catch((err) => {
    console.error(`Slack notification failed: ${err instanceof Error ? err.message : err}`);
  });
}
