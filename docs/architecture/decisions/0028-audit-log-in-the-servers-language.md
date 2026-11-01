# 0028 - The Audit Log in the Server's Language

## Status

Accepted (1.9.0). Builds on [ADR 0009](0009-audit-system.md) and
[ADR 0012](0012-safe-messages.md).

## Context

Every action writes an audit entry with a `summary`: an English sentence
built in code from the action's input ("Kicked Notch: Spam") and stored as
it is. The audit log showed that sentence and the entry's type
(`core:moderation.kick`) as its title. On a server running in German both
read as foreign text, the title as a technical key. Some actions had no
sentence of their own at all and stored their input's `toString()`
(`core:players.heal: PlayerTargetInput[targetId=...]`).

Translating the stored sentence is not possible: it is free text, written
once, and old entries were written in English whatever the language.

## Decision

- **The audit log shows a title and a sentence from the language files**,
  `audit.entry.<namespace>.<action-name-with-dashes>.title` and `.text`
  (dots become dashes, so `moderation.freeze` and
  `moderation.freeze.disconnect-notice` do not collide in the YAML tree).
- **The sentence is a template.** `{0}` is the actor, `{1}` the target the
  entry already stores, `{2}` onwards are the action's own values.
- **Actions store those values** - an action declares them with
  `ActionDefinition.Builder#auditArgs` (API 1.8), the executor writes them
  into the entry's metadata as `arg.0`, `arg.1`, ... A value
  `lang:<key>` is shown as the reader's client translation (Minecraft's
  names: `lang:gameMode.creative`, `lang:effect.minecraft.speed`), a value
  `text:<key>` as a text from the language files (`text:worlds.gui.weather.rain`).
  Everything else is plain text and escaped like every message argument.
- **The English `summary` stays stored** - the console, the Discord
  extension and future exports keep using it, and it is the fallback: an
  entry whose template needs a value it does not have (written before 1.9,
  or by an action without values) shows its stored summary; an action
  without a title shows its type.
- Actions that had no summary of their own got one, so the stored text is
  readable too.

## Consequences

- Entries written from 1.9 on read in the server's language, and change
  with it. Entries from before 1.9 do so wherever their sentence needs no
  more than actor and target; the rest keep their English sentence.
- An extension gets the same by shipping `audit.entry.<its namespace>...`
  texts and declaring `auditArgs`; without them its entries show the type and
  its own summary, as before.
- The values are stored as strings in the metadata, which the audit codec
  already supports - no schema change.
