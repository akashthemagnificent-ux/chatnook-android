// Run with: node --test supabase/functions/push/rules.test.ts
//
// Node 22.18+ strips TypeScript types on its own, so this needs no build step and no Deno.
// The cases mirror the SQL in `channel_unread_counts` where the two overlap.

import assert from "node:assert/strict";
import { test } from "node:test";

import { mentions, preview, wantsPush } from "./rules.ts";

test("preview collapses whitespace and keeps short text intact", () => {
  assert.equal(preview("  hello \n  there "), "hello there");
  assert.equal(preview(null), "");
});

test("preview truncates past 140 characters", () => {
  const out = preview("x".repeat(200));
  assert.equal(out.length, 140);
  assert.ok(out.endsWith("…"));
});

test("mentions matches a bare @name", () => {
  assert.equal(mentions("@ada look", "ada"), true);
});

test("mentions is case-insensitive", () => {
  assert.equal(mentions("@Ada look", "ada"), true);
});

test("mentions ignores a name glued to a word character", () => {
  assert.equal(mentions("hey@ada", "ada"), false);
  assert.equal(mentions("@adams", "ada"), false);
});

test("mentions allows punctuation around the name", () => {
  assert.equal(mentions("(@ada)", "ada"), true);
  assert.equal(mentions("@ada, hi", "ada"), true);
});

test("mentions does not treat a dot in the name as a wildcard", () => {
  assert.equal(mentions("@a.b", "a.b"), true);
  assert.equal(mentions("@axb", "a.b"), false);
});

test("wantsPush honours a muted server over any level", () => {
  assert.equal(wantsPush("all", true, true), false);
  assert.equal(wantsPush("mentions", true, true), false);
});

test("wantsPush defaults to every message when no override exists", () => {
  assert.equal(wantsPush(undefined, false, false), true);
});

test("wantsPush with level none never sends", () => {
  assert.equal(wantsPush("none", false, true), false);
});

test("wantsPush with mentions needs the name in the text", () => {
  assert.equal(wantsPush("mentions", false, false), false);
  assert.equal(wantsPush("mentions", false, true), true);
});
