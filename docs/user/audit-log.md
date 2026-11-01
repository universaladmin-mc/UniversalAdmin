# Audit Log

UniversalAdmin records every administrative change centrally - not as
per-feature logging bolted onto individual modules, but as one shared
pipeline every `Action` runs through automatically. See
[docs/architecture/actions.md#audit-hook](../architecture/actions.md#audit-hook)
for how an action ends up here without its author writing any audit code.

## What gets recorded

Every audit entry (`dev.universaladmin.audit.AuditEvent`) has:

| Field | Meaning |
|---|---|
| id / timestamp | assigned on save |
| actor | who did it (player/console/system/web, with display name) |
| type | the action's id (`namespace:name`), or a free-form category for the rare non-action entry |
| module | the owning module's key (e.g. `players`), if any |
| target | what was acted on (type/id/display name), if the action has a target |
| source | which channel was used: GUI, COMMAND, WEB, API, EXTENSION, SYSTEM |
| success | whether the change actually took effect |
| reason | free text - a kick/ban reason, or why it failed |
| old value / new value | before/after, if applicable |
| world / position | if the change was location-bound |
| summary | a short, human-readable one-line description |
| metadata | small structured extras (flat key/value; see "Metadata" below) |
| correlation id | ties multiple entries to one originating request, if set |

Every field beyond `type`/`actor`/`source`/`summary` is optional - most
entries only fill in a handful of them.

## Successes vs. failures

A successful action is audited by default. A failed one (denied by
permission, invalid input, or an unexpected error) is **not** audited
unless the action explicitly opted in via `ActionDefinition.Builder#auditFailures()` -
this is meant for security-relevant actions (kick/ban/permission changes),
where "someone tried and was denied" is itself worth recording. A
read-only action can opt out of auditing successes entirely via
`.notAudited()` (see `GetPlayerProfileAction`'s registration) so routine
lookups don't fill the log with noise.

## Metadata

`metadata` is a flat `Map<String, Object>` of `String`/`Number`/`Boolean`/
`null` values only - no nested objects or arrays, and never Java
serialization. Stored as a small hand-written JSON string
(`dev.universaladmin.audit.jdbc.MetadataJson`), not through an external
JSON library (see docs/development/architecture-rules.md's "Dependencies" section). Avoid putting
anything sensitive in metadata (passwords, tokens, full chat logs) - it is
visible to anyone with `universaladmin.audit.details`.

## Querying

`AuditService#query(AuditQuery)` supports filtering by actor, target,
action, module, source, success, and a time range, plus pagination -
always async, never blocking the main thread (see
[docs/architecture/threading.md](../architecture/threading.md)).
`AuditService#recent(limit)` is a shorthand for "no filters, newest first".

## GUI

Open via `/admin` → **Audit Log** (needs `universaladmin.audit.view`):

- **List**: newest first, paginated. A filter button in the top row cycles
  All → Success only → Failures only. A second "Filters..." button opens a
  dedicated filter page: pick an actor (from the online-player list, or by
  typing a cached name), a module, and/or a time range (Last Hour/24h/7d, or
  All Time) - each combines with the success/failure toggle into one
  `AuditQuery`. Every filter is stored per-session and cleared individually
  from the same page.
- **Detail**: click an entry (needs `universaladmin.audit.details` - a
  viewer without it still sees the list row, just not a clickable detail
  page) to see Actor/Action/Target/Time/Source/Result/Reason/Old-New/
  Metadata laid out in full.

Entries read in the server's language: a short title ("Ban") and a
sentence ("Admin banned Notch for 7d: Griefing") from the language files
(`audit.entry.*`, see [ADR 0028](../architecture/decisions/0028-audit-log-in-the-servers-language.md)).
The English summary each entry was saved with stays in the database for the
console, Discord and exports; an entry from before 1.9 shows it where its
sentence would need a value that was not stored then.

The list loads the most recent 200 entries matching the current filter and
paginates over that batch in memory - the same "load once, slice
client-side" shape every list page in this GUI framework uses (see
[docs/development/gui-framework.md](../development/gui-framework.md)), not
a fully server-paginated view of a potentially huge table.

## Permissions

| Node | Default | Meaning |
|---|---|---|
| `universaladmin.audit.view` | op | Open the audit log list |
| `universaladmin.audit.details` | op | Open an entry's detail view (old/new values, metadata) |

## Retention

`audit.retention-days` in `config.yml` (default `0` = unlimited, never
deleted). When set above `0`, `AuditLogModule` runs a cleanup once an hour
(not on every server tick) that deletes entries older than the configured
window via `AuditService#cleanupExpired()`. Disabling the whole Audit Log
*module* (`modules.audit: false`) stops this cleanup task from running at
all, but does not affect whether `AuditService` itself records events -
that is `audit.enabled`, a separate switch.

## Database

Backed by the `audit_log` table (see
[docs/architecture/storage.md](../architecture/storage.md)), created by
`AuditSchemaMigration` (version 1) and widened by `AuditSchemaMigrationV2`
(version 2) to the full field set above. Indexed on `occurred_at`,
`(actor_type, actor_id)`, `(target_type, target_id)`, `action_id`,
`module`, and `source` - the dimensions `AuditQuery` actually filters on,
deliberately not more (see `AuditSchemaMigrationV2`'s Javadoc).

## Commands

`/admin audit [player] [page]` shows the newest entries in chat or the
console. Targets and summaries use player names (since 1.0).

## For extensions

Every recorded entry is also published to `AuditListener`s
(`ModuleContext#subscribeAudit`) - the official Discord extension uses this
to mirror selected events into a channel.
