// Pure decisions behind a push, kept apart from the HTTP handler so they can be tested
// without Deno or a database. `index.ts` is the only caller.

export type Level = "all" | "mentions" | "none";

/** One line for the notification body; 140 characters with an ellipsis. */
export function preview(content: string | null): string {
  const text = (content ?? "").replace(/\s+/g, " ").trim();
  if (text.length <= 140) return text;
  return text.slice(0, 139) + "…";
}

/** Same rule as `channel_unread_counts`: @name, not glued to a word character. */
export function mentions(content: string, username: string): boolean {
  const escaped = username.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`(^|[^a-z0-9._-])@${escaped}(?![a-z0-9._-])`, "i").test(content);
}

/**
 * Whether one member wants this message. A muted server wins over everything; otherwise
 * the channel level decides, and "mentions" needs the member's name in the text.
 */
export function wantsPush(
  level: Level | undefined,
  muted: boolean,
  mentioned: boolean,
): boolean {
  if (muted) return false;
  switch (level ?? "all") {
    case "none":
      return false;
    case "mentions":
      return mentioned;
    default:
      return true;
  }
}
