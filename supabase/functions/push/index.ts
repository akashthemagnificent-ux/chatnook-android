// ChatNook push sender.
//
// Called by the `messages_push` database trigger with the inserted row. Resolves who
// should hear about it, mints an FCM HTTP v1 access token from the service account, and
// sends one data-only message per device. Data-only keeps the notification text and the
// tap target under the app's control.
//
// Secrets (supabase secrets set …):
//   FCM_SERVICE_ACCOUNT   the service account JSON, as a single line
//   PUSH_WEBHOOK_SECRET   shared with the database trigger
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by the platform.

import { createClient } from "jsr:@supabase/supabase-js@2";

type MessageRow = {
  id: string;
  channel_id: string;
  author_id: string;
  content: string | null;
  type: string;
  created_at: string;
};

type ServiceAccount = {
  client_email: string;
  private_key: string;
  project_id: string;
};

const encoder = new TextEncoder();

function base64url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToBytes(pem: string): Uint8Array {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const raw = atob(body);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

/** Exchanges the service account for an OAuth2 access token (scope: cloud-platform). */
async function fcmAccessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = base64url(
    encoder.encode(
      JSON.stringify({
        iss: account.client_email,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      }),
    ),
  );
  const unsigned = `${header}.${claims}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(unsigned)),
  );
  const assertion = `${unsigned}.${base64url(signature)}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error(`token exchange failed: ${await response.text()}`);
  const body = await response.json();
  return body.access_token as string;
}

function preview(content: string | null): string {
  const text = (content ?? "").replace(/\s+/g, " ").trim();
  if (text.length <= 140) return text;
  return text.slice(0, 139) + "…";
}

Deno.serve(async (request) => {
  const secret = Deno.env.get("PUSH_WEBHOOK_SECRET");
  if (secret && request.headers.get("x-webhook-secret") !== secret) {
    return new Response("forbidden", { status: 403 });
  }

  const payload = await request.json().catch(() => null);
  const record: MessageRow | undefined = payload?.record;
  if (!record || record.type !== "text" || !record.content) {
    return new Response("ignored", { status: 200 });
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  const { data: channel } = await supabase
    .from("channels")
    .select("id,name,guild_id")
    .eq("id", record.channel_id)
    .maybeSingle();
  if (!channel) return new Response("no channel", { status: 200 });

  // Everyone who should hear about it: guild members, or the other DM participant.
  let recipients: string[] = [];
  if (channel.guild_id) {
    const { data } = await supabase
      .from("guild_members")
      .select("user_id")
      .eq("guild_id", channel.guild_id);
    recipients = (data ?? []).map((row) => row.user_id);
  } else {
    const { data } = await supabase
      .from("dm_participants")
      .select("user_id")
      .eq("channel_id", channel.id);
    recipients = (data ?? []).map((row) => row.user_id);
  }
  recipients = recipients.filter((id) => id !== record.author_id);
  if (recipients.length === 0) return new Response("nobody", { status: 200 });

  // Someone who has already read past this message does not need a push.
  const { data: reads } = await supabase
    .from("read_states")
    .select("user_id,last_read_at")
    .eq("channel_id", channel.id)
    .in("user_id", recipients);
  const caughtUp = new Set(
    (reads ?? [])
      .filter((row) => row.last_read_at && row.last_read_at >= record.created_at)
      .map((row) => row.user_id),
  );
  const targets = recipients.filter((id) => !caughtUp.has(id));
  if (targets.length === 0) return new Response("all caught up", { status: 200 });

  const { data: tokens } = await supabase
    .from("push_tokens")
    .select("token")
    .in("user_id", targets);
  if (!tokens?.length) return new Response("no devices", { status: 200 });

  const { data: author } = await supabase
    .from("profiles")
    .select("display_name,username")
    .eq("id", record.author_id)
    .maybeSingle();
  const authorName = author?.display_name || author?.username || "Someone";

  const account = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT")!) as ServiceAccount;
  const accessToken = await fcmAccessToken(account);
  const isDm = !channel.guild_id;
  const title = isDm ? authorName : `#${channel.name}`;
  const body = isDm ? preview(record.content) : `${authorName}: ${preview(record.content)}`;

  let sent = 0;
  const dead: string[] = [];
  await Promise.all(
    tokens.map(async ({ token }) => {
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token,
              data: {
                channel_id: channel.id,
                channel_name: channel.name ?? "",
                dm: String(isDm),
                title,
                body,
              },
              android: { priority: "high" },
            },
          }),
        },
      );
      if (response.ok) {
        sent++;
      } else if (response.status === 404 || response.status === 400) {
        // UNREGISTERED / INVALID_ARGUMENT: the install is gone, drop the token.
        dead.push(token);
      }
    }),
  );

  if (dead.length) {
    await supabase.from("push_tokens").delete().in("token", dead);
  }

  return new Response(JSON.stringify({ sent, dead: dead.length }), {
    headers: { "Content-Type": "application/json" },
  });
});
