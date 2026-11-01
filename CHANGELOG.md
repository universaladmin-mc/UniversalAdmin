# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/). Unreleased
changes accumulate under `[Unreleased]`; each release moves them into a
dated, versioned section below it (see
[docs/release/releasing.md](docs/release/releasing.md)).

## [Unreleased]

## [1.9.0] - 2026-11-01

The second half of the menu overhaul, and a look over everything else:
compatibility, migrations and how easy UniversalAdmin is to use.

### Added

- **Server, worlds and performance as hubs**, like the player profile: what
  you see in a light blue row, what you do in rows behind coloured labels -
  green harmless, orange with an effect on players, red dangerous.
- **Live values**: the main menu, server, worlds, a world and performance
  update once a second while open.
- **Traffic lights** on the performance page: TPS, MSPT and memory in green,
  yellow or red, measured against your alert thresholds.
- **A world's settings show their value**: time (as a clock), weather,
  difficulty, spawn and world border show what they are now and change on a
  click.
- **The audit log reads in the server's language**: "Ban - Admin banned
  Notch for 7d: Griefing" instead of `core:moderation.ban` and an English
  sentence. See [ADR 0028](docs/architecture/decisions/0028-audit-log-in-the-servers-language.md).
- **Minecraft's names in your game's language**: game modes, difficulties,
  effects and entities ("Überlebensmodus", "Eile") instead of `SURVIVAL`,
  `SPEED`.
- **New options reach your config.yml**: an update writes options it brings
  into your existing file with their explanation; the console lists them.
- **A hint for staff on their first join**: `/admin` opens the menu - and if
  your game runs in a language UniversalAdmin ships that the server does not
  use, one click switches it.

### Fixed

- Heal, feed, extinguish, clear effects and the flight, glow, gravity and
  collision switches wrote their raw input into the audit log
  (`PlayerTargetInput[targetId=...]`).
- The used deprecated `getBedSpawnLocation` and effect `getName` are
  replaced.

### For extension developers

- API 1.8: `GuiRows`, `GuiButton#display`, `GuiPage#live()`,
  `ActionDefinition.Builder#auditArgs`, `Migration#namespace()` (your own
  numbering in `ua_extension_migrations`), `MessageService#get(locale, ...)`.
  Everything has a default; extensions built against API 1.7 keep working.
- `./gradlew build` checks the jar against the Paper API of 1.21.7, 1.21.11
  and the version compiled against (`binaryCompatibility`).

## [1.8.0] - 2026-10-28

The menus got a new look: a frame in every module's colour, glass panes
that always mean the same, and hubs instead of long lists of buttons. See
[ADR 0027](docs/architecture/decisions/0027-menu-design-system.md).

### Added

- **Main menu as a dashboard:** players online, open reports, TPS,
  maintenance and the network at a glance, the modules in three groups,
  and a "My status" row to switch vanish, staff mode, godmode, staff chat
  and your nick on and off.
- **Status line:** staff see what they have switched on in the action bar,
  e.g. `Vanish · Godmode · Nick Notch`, while something is on. In staff
  mode the target display keeps the action bar. Off with
  `gui.status-line: false`.
- **A player's profile is the place for everything about them:** who,
  where, playtime and condition on the left, then rows to moderate (warn,
  temp mute, kick, temp ban, freeze/unfreeze right there), view (inventory,
  ender chest, effects, history, reports, notes, alts), help (teleport,
  bring, heal, feed, flight, all actions) and more. The previous and next
  online player are one click away, the UUID can be copied.
- **Player lists as tabs:** the Players button opens the online players;
  Online, Offline and Recently seen switch in the header.
- **A freeze is visible:** the frozen player sees a title until they are
  free (`moderation.freeze.show-title`), and staff who may freeze see a
  marker above their head with who froze them and for how long
  (`moderation.freeze.staff-marker`).
- **Settings with readable names and descriptions** in English and German;
  the config path is still shown below.

### Changed

- Every page has the same frame: the header in the module's colour with
  back, main menu, the page's icon, refresh and close; tools and paging at
  the bottom; empty slots are black. Glass colours mean the same on every
  page - green on/confirm, red off/danger, orange warning, white "nothing
  here".
- Switching pages no longer closes and reopens the inventory - no flicker,
  the mouse stays where it is.
- Confirmations have a frame in the colour of what is at stake: red for a
  ban, orange for a kick or mute.

### For extension developers

- API 1.7: `GuiGlass`, `MenuGroup`, `GuiWindow`, `GuiStyle`,
  `StatusToggle`/`StatusToggleRegistry`/`ActionStatusToggle`,
  `PlayerMenuGroup`, `PlayerMenuEntry#group()`, `GuiClickContext#page()`,
  `ModuleDescriptor#menuGroup`/`#color`. Everything has a default -
  extensions built against API 1.6 keep working unchanged.

## [1.7.2] - 2026-10-28

### Fixed

- **Godmode showed as "on" after every join** on Paper 26, and the first
  click only switched it off. 1.7.1 read the player's invulnerability, which
  Paper also reports for the few seconds of protection after joining or a
  hit. Godmode is now kept as a mark in the player's data. A player with
  the godmode permission who still has godmode from an older version is
  recognised a few seconds after joining, once that protection is over.

## [1.7.1] - 2026-10-28

### Fixed

- **Corrected texts now reach every server.** `lang/<locale>.yml` holds only
  your own texts; every other text comes from the plugin and follows its
  updates. Up to 1.7.0 the whole built-in file was copied there once and hid
  every later correction - such a copy is cleaned once (your changes stay,
  the original is kept as `lang/<locale>.yml.before-1.7.1`). Every built-in
  text is in `lang/reference/` to copy from. See
  [ADR 0026](docs/architecture/decisions/0026-language-files-hold-only-overrides.md).
- **Godmode after a restart:** a player who still had godmode on when the
  server restarted stayed invulnerable while the menu said "off", and needed
  two clicks to turn it off. Godmode now follows the player's real
  invulnerability.
- **Empty lists** show a white "Nothing here." instead of a grey pane that
  looked like filling.

## [1.7.0] - 2026-10-28

Staff can play incognito now.

### Added

- **Nick system**, a new built-in module: `/nick Notch` shows you as Notch,
  with Notch's skin, in the chat, the tab list, above your head, in join,
  quit and death messages and to other plugins. Name and skin can be chosen
  separately (`/nick Bob Notch`), or both at random (`/nick random`);
  `/unnick` ends it, also from the new **Nick** menu. Names of players who
  have played here, of online players and of other nicks are refused. Staff
  with `universaladmin.nick.see` see the real name in the player list, on
  the profile and in `/nick list`; punishments, notes, reports and the
  audit log keep the real identity, and actions a nicked staff member takes
  run under their real name. A nick survives rejoining
  (`nick.restore-on-rejoin`) and follows its player through a network. See
  [docs/user/modules/nick.md](docs/user/modules/nick.md) and
  [ADR 0025](docs/architecture/decisions/0025-nick-system.md).
- **UniversalAdmin TAB**, an official extension for TAB: nicked players are
  shown in an ordinary TAB group, vanished staff and players in staff mode
  get a marker. Its own repository:
  [universaladmin-mc/universaladmin-tab](https://github.com/universaladmin-mc/universaladmin-tab).
- **Extension API 1.6**: the `Nicks` service (`dev.universaladmin.status`),
  `PlayerNames.real(...)` and `PlayerNames.revealedTo(...)`.
- Vanish sets the `vanished` player metadata vanish plugins share, so TAB,
  map plugins and others recognise UniversalAdmin's vanish.

### Fixed

- On Java 21 the update check and the anonymous statistics did not start
  ("No implementation of the random number generator algorithm").
- A player who left while vanished came back invisible when
  `moderation.vanish.restore-on-reconnect` was off.

## [1.6.0] - 2026-10-28

Updates install themselves, the project has a home of its own - and a new
look.

### Added

- **Automatic updates**: a new release (of UniversalAdmin or of an
  installed official extension) is downloaded, checked against its SHA-256
  checksum and installed on the next restart. Only within the same major
  version - a major update waits for `/admin update`. Switch it off with
  `update.auto-download: false`. Staff with `universaladmin.update.notify`
  get a notice on join; the console logs it on every check. See
  [docs/user/configuration.md](docs/user/configuration.md) and
  [ADR 0024](docs/architecture/decisions/0024-updates-and-repositories.md).
- `/admin update` installs every available update at once - the plugin and
  the extensions, a major update too.
- **The proxy plugins** (Velocity, BungeeCord) check for updates as well and
  log a notice (`update-check.enabled` in their `config.yml`). They never
  download anything.
- **Documentation website**:
  [universaladmin-mc.github.io/UniversalAdmin](https://universaladmin-mc.github.io/UniversalAdmin/),
  built from `docs/` whenever it changes on `main`.
- **New logos** for UniversalAdmin, its extensions and the proxy plugins.
- API 1.5: `ExtensionManager#updatesFrom(Plugin, String, String)` - an
  extension names the GitHub repository it is released from, and
  UniversalAdmin keeps it up to date too.

### Changed

- **The project moved** to the GitHub organization
  [universaladmin-mc](https://github.com/universaladmin-mc). Old links to
  `nicki41/UniversalAdmin` redirect. JitPack coordinates are now
  `com.github.universaladmin-mc.UniversalAdmin`.
- **The official extensions have repositories of their own**
  ([Discord](https://github.com/universaladmin-mc/universaladmin-discord),
  [Placeholders](https://github.com/universaladmin-mc/universaladmin-placeholders),
  [Voice Chat](https://github.com/universaladmin-mc/universaladmin-voicechat)),
  with their own releases. Their jars are no longer attached to the
  UniversalAdmin release; Modrinth is unchanged.

### Fixed

- `/admin update` picked the first jar of a release - the API javadoc - and
  refused it over the checksum. It now takes the plugin jar by name.
- `/admin update` reports every plugin on its own line: one whose release
  cannot be fetched (for example an extension without a published release)
  no longer stops the others. A missing release and a used-up GitHub request
  limit are named as such.
- "The details are in the server log" now holds for every internal error an
  action reports - its reason is logged.

## [1.5.0] - 2026-10-24

Mute in the chat, in the voice chat or both, forms instead of one prompt
after another, the whole network in the player menu - and a new license.

### Added

- **Chat, voice and full mutes**: `/chatmute` and `/voicemute` (each with an
  optional duration) next to `/mute`, which stays a full mute. With a voice
  chat connected the punish form asks where to mute, and templates take
  `chatmute [duration]` and `voicemute [duration]` steps. The player's
  notice, the staff notification, the history and `/checkban` name the
  kind; `/unmute` lifts every kind. In a network a mute takes effect on
  every server at once. See
  [docs/user/modules/moderation.md](docs/user/modules/moderation.md#chat-voice-and-full-mutes)
  and [ADR 0022](docs/architecture/decisions/0022-mute-kinds-and-voice-chat.md).
- **UniversalAdmin Voice Chat**, an official extension for Simple Voice
  Chat: voice and full mutes silence the microphone, vanished staff cannot
  be heard (`voicechat.silence-vanished`), a hidden, isolated **staff voice
  group** (`/admin voicegroup`), the voice status on the profile with a
  click to take a player out of a group. New permissions
  `universaladmin.voicechat.staffgroup` and `.manage`. See
  [docs/extensions/voicechat.md](docs/extensions/voicechat.md).
- **The whole network in the player menu**: in network mode **Online
  Players** lists every player of the network with the server they are on,
  with a **Server** filter and sorting by name or by server. A profile says
  where the player is and offers **Teleport to player**, **Bring to me** and
  **Send to server...** - also for players on this server.
- API 1.4: `MuteKind`, `PlayerStatus#chatMute`/`#voiceMute`, the
  `VoiceChat` service and `GuiForm`.

### Changed

- **Forms instead of one prompt after another**
  ([ADR 0023](docs/architecture/decisions/0023-dialog-forms.md)): punishing
  (reason, duration and mute kind in one form), adding to the whitelist
  (reason, notes, expiry), title broadcasts (title and subtitle), chat
  broadcasts (multi-line), enabling maintenance (reason, the message for
  players, whether to kick), the world border (one field per value, a
  transition slider, "use my position"), health, food, XP and speeds as
  sliders, and teleporting to coordinates (X, Y, Z and a world list).
- Text prompts accept up to 255 characters - the dialog's own limit of 32
  cut longer reasons short.
- The punishment list and detail pages show the localized type.
- **License: GPL-3.0-or-later** from this version on, for the plugin, the
  proxy plugins and the official extensions. The extension API
  (`universaladmin-api`) and the example extension stay under Apache-2.0, so
  extensions can still use any license. Versions up to 1.4.0 remain
  available under Apache-2.0. Every jar now carries its license text under
  `META-INF/` - see [docs/release/licensing.md](docs/release/licensing.md).

### Upgrading

- Replace the jars and restart; a database column is added automatically.
  Every existing mute is a full mute. For voice mutes, add
  `universaladmin-voicechat-<version>.jar` next to Simple Voice Chat on every
  server where players talk.

## [1.4.0] - 2026-10-22

Find second accounts of banned players - without storing addresses - and
the proxy checks for BungeeCord networks too.

### Added

- **Alt detection** (opt-in, `moderation.alts.enabled`): every login stores
  a keyed hash of the address - never the address itself; IPv6 counts by its
  /64 network - and accounts that share one show up as possible alts:
  `/admin alts <player>`, the **Possible Alts** button on a profile, and a
  staff hint when a player joins who shares an address with a banned
  account (on every server of a network). Optionally refuse such players
  (`moderation.alts.refuse-banned-alts`, off by default;
  `universaladmin.bypass.alt-check` exempts players). Addresses are forgotten
  after `moderation.alts.retention` (90 days); `/admin alts <player> forget`
  deletes a player's on request; viewing and forgetting are audited. New
  permissions `universaladmin.moderation.alts`, `.alts.forget` and
  `universaladmin.bypass.alt-check`. See
  [docs/user/modules/moderation.md](docs/user/modules/moderation.md#alt-detection)
  and [ADR 0020](docs/architecture/decisions/0020-alt-detection.md).
- **UniversalAdmin BungeeCord**: the proxy plugin for BungeeCord networks -
  bans, network maintenance, the network whitelist and the server-list text,
  the same `config.yml` as the Velocity plugin, `/uabungee`. On GitHub and as
  the `-bungee` version on Modrinth
  ([ADR 0021](docs/architecture/decisions/0021-bungeecord-proxy-plugin.md)).

### Changed

- The proxy checks live in one place (`proxy/common`) for both proxy
  plugins; UniversalAdmin Velocity behaves as before.

### Upgrading

- Replace the jars and restart; a new table is created automatically. Alt
  detection stays off until you switch it on. On a BungeeCord proxy, add
  `universaladmin-bungee-<version>.jar` and fill in its `config.yml` like the
  Velocity plugin's.

## [1.3.0] - 2026-10-22

Consistent punishments that escalate on their own, and a network that can
be whitelisted and keeps vanished staff hidden.

### Added

- **Punishment templates with escalation**: `/punish Steve spam` warns Steve
  the first time, mutes him for an hour the second time, and so on - steps
  per template in `punishment-templates.yml` (five examples in the server's
  language), counting earlier uses of the same template within 90 days (per
  template; `0` = forever). Also as **Punish with Template** in the moderation
  menu and `/punish` in the Discord bot. `/punish <player>` still opens the
  moderation menu (now with the templates first); from the console it lists
  what each template would do. Each step still needs its own permission. New
  permission `universaladmin.moderation.punish`. See
  [docs/user/modules/moderation.md](docs/user/modules/moderation.md#punishment-templates)
  and [ADR 0018](docs/architecture/decisions/0018-punishment-templates.md).
- **Network whitelist** (`/admin whitelist network on|off|status|add|remove|list`):
  one list for every server of the network, on top of each server's own,
  also enforced at the proxy by UniversalAdmin Velocity
  (`whitelist-check`). New permissions `universaladmin.whitelist.network`
  and `universaladmin.bypass.network-whitelist`. See
  [docs/user/modules/whitelist.md](docs/user/modules/whitelist.md#network-whitelist).
- **Vanish across the network**: vanished players are left out of the
  network list, `/admin find`, player counts, tab completion and the
  network placeholders for everyone without `universaladmin.bypass.vanish`,
  and stay vanished when switching servers even with
  `vanish.restore-on-reconnect: false`
  ([ADR 0019](docs/architecture/decisions/0019-network-whitelist-and-vanish.md)).
- Extension API 1.3: `NetworkService#vanished(UUID)`.

### Fixed

- **Whitelist metadata in network mode** was one table for every server, so
  one server's expiry sweep could remove a player from another server's
  whitelist. Each server now keeps its own; temporary entries keep their
  expiry after the upgrade.
- `%universaladmin_network_online%` and `%universaladmin_network_online_<server>%`
  no longer count vanished players.

### Upgrading

- Replace the jars - the plugin on every server, UniversalAdmin Velocity on
  the proxy, and the extensions (UniversalAdmin Placeholders 1.3 needs
  UniversalAdmin 1.3) - and restart. New tables and columns are created
  automatically, `punishment-templates.yml` is written on the first start.
  In a network, give the servers a minute to restart together: a server
  that is still on 1.2 keeps working, but does not know the network
  whitelist.

## [1.2.1] - 2026-10-22

Maintenance done right in a network: one server or all of them.

### Added

- **Network maintenance**: `/admin maintenance network on [kick] [reason]`
  closes every server of the network at once (and the proxy with
  UniversalAdmin Velocity), with its own reason, message and allow-list;
  `off`, `status`, `list`, `allow` and `deny` likewise. The GUI page has a
  second row for it. New permission
  `universaladmin.server.maintenance.network`. See
  [docs/user/network.md](docs/user/network.md#maintenance) and
  [ADR 0017](docs/architecture/decisions/0017-maintenance-scopes.md).
- **UniversalAdmin Velocity** refuses logins during network maintenance
  (bypass permission on the proxy or the network allow-list still get in)
  and shows "Maintenance" in the server list (`maintenance-check` in its
  config.yml). `/uavelocity` shows both checks.
- A server in network mode without LuckPerms says at startup that OP and
  permissions count per server, and how to share them
  ([network.md](docs/user/network.md#permissions)).

### Fixed

- **Maintenance in network mode was shared by accident**: every server used
  the same stored state, so putting one server into maintenance closed the
  others after their next restart. Each server now keeps its own.
- `/admin maintenance status` and the dashboard tile also show the
  network's maintenance.
- Import summary and allow-list audit entries no longer say "1 punishments"
  or "1 players".
- UniversalAdmin Velocity names its author in the proxy log, and no longer
  uses the deprecated event order API.
- Javadoc of the extension API: no more links to classes that only exist in
  the plugin.

### Upgrading

- Replace the jars (plugin on every server, and UniversalAdmin Velocity on
  the proxy if you use it) and restart. A new table (`ua_maintenance`) is
  created and the old maintenance state copied into it - every server keeps
  the maintenance it had. In a network, give every server a
  `network.server-name` if you have not yet.

## [1.2.0] - 2026-10-21

Switching made easy: bring your bans over from LiteBans, AdvancedBan, the
vanilla ban lists or EssentialsX. Plus PlaceholderAPI placeholders and a
Discord bot, both as official extensions with their own Modrinth pages.

### Added

- **Import from other plugins**: `/admin import` brings bans (also IP
  bans), mutes, warnings and kicks over from **LiteBans** (MySQL/MariaDB or
  its H2 file), **AdvancedBan** (MySQL or its HSQLDB files), the **vanilla
  ban lists** and **EssentialsX** (mutes; jails become staff notes) - with
  reasons, dates, who issued and who lifted them, and what is still in
  force. A preview shows everything before anything is written; running it
  again only imports what is new; players who never joined become known by
  name. Vanilla entries are moved so unbans work. See
  [docs/user/import.md](docs/user/import.md).
- **UniversalAdmin Placeholders** - a new official extension for
  PlaceholderAPI: `%universaladmin_vanished%`, `_muted`,
  `_mute_remaining`, `_warnings`, `_online` (without vanished players),
  `_staff_online`, `_open_reports`, `_maintenance`, network counts and more.
  See [docs/extensions/placeholderapi.md](docs/extensions/placeholderapi.md).
- **Discord bot mode** for UniversalAdmin Discord: two-way staff chat,
  slash commands (`/ban`, `/mute`, `/kick`, `/warn`, `/unban`, `/unmute`,
  `/history`, `/check`, `/find`, `/ua <any admin command>`) with player
  autocomplete, and reports with Claim / Resolve / Reject buttons. Discord
  roles map to permission nodes; everything is checked and audited like in
  game. Webhook mode stays the default. See
  [docs/extensions/discord.md](docs/extensions/discord.md).
- Extension API 1.2: `Actor.remote` / `ActorType.REMOTE`, `RemoteCommands`
  and `StaffChat` ([ADR 0016](docs/architecture/decisions/0016-remote-actors.md)).
- Extension API 1.2: status services for displays
  (`dev.universaladmin.status`: `PlayerStatus`, `ReportOverview`,
  `MaintenanceStatus`) and `PlayerDirectory#addIfAbsent`.
- New permission `universaladmin.moderation.import`.

### Upgrading

- Replace the jar and restart. A new table (`ua_import_records`) is created
  automatically; nothing else changes.

## [1.1.0] - 2026-10-20

The first stable release: commands for everything, a report system, chat
tools, staff notes, an in-game settings editor, network mode for Velocity
and BungeeCord networks, a public extension API and the first official
extensions. One jar runs on Paper 1.21.7 - 1.21.11 and 26.1.1 - 26.3.
Upgrading from an alpha is automatic - see "Upgrading" below. (1.0.0 was
never published on its own; everything planned for it is part of 1.1.0.)

### Added

- **Commands for every module** under `/admin` (aliases `/ua`, `/uadmin`),
  with tab completion and a clickable `/admin help`. Optional top-level
  shortcuts such as `/kick`, `/ban`, `/mute`, `/warn`, `/history`,
  `/vanish`, `/heal`, `/gm`, `/invsee`, `/broadcast`, `/maintenance`, `/lag`
  (`commands.shortcuts.enabled`/`disabled`; `/universaladmin:<label>` always
  works). Full list: [docs/user/commands.md](docs/user/commands.md).
  - Moderation: kick, ban/tempban/ipban (`/ban Steve 7d griefing`),
    mute/tempmute, warn/unwarn, freeze/unfreeze, unban/unmute, history,
    check, moderate, vanish, godmode, nocollision, staff [recover], notes.
  - Players: player, info (`/seen`), heal, feed, extinguish, fly, gamemode,
    speed, tp, tphere, invsee, endersee, clearinv.
  - Server: broadcast, server status/title/actionbar/shutdown/restart/cancel,
    maintenance on/off/allow/deny/list.
  - Worlds: worlds, world info/tp/time/weather/difficulty/setspawn/border/
    gamerule. Whitelist: on/off/add (also players who never joined, with an
    optional duration)/remove/list. Performance: perf, entities [clear].
    Audit: audit [player] [page]. Core: status, modules, reload, update.
- **Reports module**: `/report <player> <reason>` for everyone (cooldown and
  open-report limit), instant clickable staff notifications with a sound, a
  reminder on join, a reports GUI with claim/resolve/reject and teleports to
  the player or the report location, a "Reports" button on every profile,
  and a thank-you to the reporter. See
  [docs/user/modules/reports.md](docs/user/modules/reports.md).
- **Chat module**: staff chat (`/sc`, toggle mode, or a `#` prefix), chat
  lock, slow mode and clear chat - commands, GUI page, audited. See
  [docs/user/modules/chat.md](docs/user/modules/chat.md).
- **Staff notes** about players (profile button, `/admin notes`, a hint for
  staff when a player with notes joins).
- **Team notifications**: every punishment and pardon is announced to online
  staff with `universaladmin.moderation.notify`.
- **Settings editor**: every setting of the core, every module and every
  extension is editable in-game (`/admin` → Settings, `/admin settings
  get|set|reset|list`); changes are validated, written to `config.yml` with
  its comments intact, applied live where possible and audited. Sensitive
  values (database password) stay file-only.
- **Extension API** (`universaladmin-api`): extensions are ordinary Paper
  plugins with `depend: [UniversalAdmin]` that register modules through
  `UniversalAdminProvider.get().extensions()` - with commands, GUI pages,
  main-menu entries, profile buttons, audited actions, permissions,
  settings, translations, database migrations and audit/action listeners,
  all cleaned up automatically when the extension is disabled. Published
  with every release (binary, sources, javadoc). Developer guide:
  [docs/extensions/getting-started.md](docs/extensions/getting-started.md).
- **UniversalAdmin Discord** - the first official extension: posts
  punishments, reports, restarts, maintenance and whitelist changes to a
  Discord channel via a webhook ([docs/extensions/discord.md](docs/extensions/discord.md)).
- **Live inventory and ender chest editor**: changes show up in both
  directions within a tick - what the player does appears in the editor,
  what staff change lands in the player's inventory. Laid out like the
  player's own screen (storage, hotbar, armor, offhand); one audit entry
  per editing session with the number of changed slots.
- **Network mode** for servers behind a Velocity or BungeeCord proxy that
  share one MySQL database (`network.enabled`): a Network menu with every
  server - also those without UniversalAdmin - and its players, `/admin
  network`, `/admin find <player>`, cross-server `/admin tp` and `tphere`
  (you land right next to the player), `/admin send`, `/admin connect`,
  network-wide `/admin alert`; bans, mutes, warnings, kicks and freezes
  take effect on every server at once; staff chat, staff notifications and
  report alerts across all servers, tagged with the server; one audit log
  for the network, every entry tagged with its server; commands find every
  player of the network. See [docs/user/network.md](docs/user/network.md).
- **UniversalAdmin Velocity** - an optional proxy plugin that turns banned
  players away at the proxy, before they reach any server (also servers
  without UniversalAdmin), with a configurable ban screen and
  `/uavelocity`. Published next to the plugin on GitHub and Modrinth.
- **One jar for many Minecraft versions**: Paper (and Purpur) 1.21.7 -
  1.21.11 and 26.1.1 - 26.3, Java 21 or newer. Every listed version is
  compiled against and boot-tested; a weekly check tests new Paper
  versions and adds them to the newest release on Modrinth as soon as
  they pass ([docs/user/installation.md](docs/user/installation.md)).
- An example extension (`examples/example-extension`) as a template.
- Ready-made LuckPerms role templates (helper, moderator, admin) in
  [docs/user/permissions.md](docs/user/permissions.md).
- The main menu and module names are translated (`menu.*` keys).

### Changed

- The shipped `config.yml` lists every setting of every module, with
  comments, grouped by module (`moderation.vanish.*`, `server.countdown.*`,
  `players.gui.*`, ...).
- The audit log shows player names instead of UUIDs in targets and
  summaries.
- **Precise feedback everywhere**: every command and every GUI button says
  exactly what it did - "Spawn of world set to 120 64 -30", "Banned Steve
  for 7d. Reason: griefing #42", "Removed 37 entities in world" - instead
  of "Done.", and command and GUI show the same text. Extensions get the
  same through `ActionResult.success(value, key, args...)`.
- Every reply carries a short `[UA]` prefix (`command.prefix`, may be set
  to `""`) - in chat after a GUI click too.
- **Staff mode tools work at range**: aim at a player and right-click - the
  tool acts on the player the actionbar names, up to
  `moderation.staffmode.target-range-blocks` (now 128 blocks, through
  walls), not only on someone close enough to click directly.
- Menu item texts are consistently styled (white names, gray lore - no
  more purple italic lore lines).
- The update check and `/admin update` compare versions properly and never
  offer an older release.
- Releases on GitHub additionally carry the Discord extension, the
  Velocity plugin and the API jars; Modrinth versions are marked as
  release instead of alpha and list every supported Minecraft version.
- Built for Java 21 (was 25), so servers on 1.21.x run it too.
- Extensions: `UniversalAdmin.network()` (servers, players, messages
  between servers, moving players), the `PlayerDirectory` service (find a
  player by name or UUID network-wide) and `CommandInvocation.withPlayer`
  for commands that take any known player.

### Fixed

- Settings of modules (vanish, freeze, staff mode, reports, ...) were only
  read from `config.yml` after an `/admin reload`; they now apply from the
  start.
- New texts added in an update showed up as `[missing: ...]` because the
  copied language files were never updated; the bundled texts are now the
  fallback for every key.
- A typo in a language file stopped the whole plugin from starting; the
  file is now skipped with an error in the log.
- Staff notifications (performance alerts, update notices, frozen-player
  disconnects) showed raw `<yellow>` tags instead of colours.
- Confirmation dialogs went back to the wrong page on "cancel", and a
  quick double click could confirm a dangerous action twice; GUI buttons
  now ignore double clicks.
- `/admin update` could "update" to an older version on a server running a
  newer build.
- The inventory editor did not show the player's own changes while it was
  open and could overwrite them with its outdated copy; it also wrote an
  audit entry for every single click.
- Punishing a player who was offline stored their UUID instead of their
  name in the history.
- The Freeze Tool could freeze and immediately unfreeze with one click.
- Several servers starting at the same time on a new MySQL database could
  run the same schema migration twice; migrations now run under a
  database lock, and a migration added later is applied even if newer
  ones already ran.
- Console commands sent over RCON got no reply.
- "Could not save lang/en_US.yml ... already exists" warnings on every
  start.

### Security

- Text typed by players and staff (reasons, report texts, chat, notes,
  filters) can no longer inject MiniMessage formatting, hover or click
  events into messages other players see - every message argument is
  escaped ([ADR 0012](docs/architecture/decisions/0012-safe-messages.md)).
- Discord messages never ping anyone (`allowed_mentions` disabled), and the
  webhook URL is never logged.

### Upgrading from 0.1.0-alpha

- Replace the jar and restart. `config.yml` is migrated automatically
  (config version 3): module settings move under their module's section
  and the new sections (`commands`, `network`, ...) are added. Nothing you
  set is lost or overwritten.
- New database tables (`ua_reports`, `ua_player_notes`) are created
  automatically.
- Your `lang/*.yml` files are kept; every text you did not change now comes
  from the plugin itself, so new texts appear automatically.
- New permissions to hand out: `universaladmin.reports.*`,
  `universaladmin.chat.*`, `universaladmin.moderation.notes`,
  `universaladmin.moderation.notify`, `universaladmin.status`,
  `universaladmin.network.*` - and `universaladmin.reports.create` is
  granted to everyone by default.
- Running a network? See [docs/user/network.md](docs/user/network.md).

## [0.1.0-alpha.5] - 2026-08-26

### Added

- Every GitHub release is now automatically mirrored to the `universaladmin`
  Modrinth project as a new version (`Kira-NT/mc-publish`, the new
  `MODRINTH_TOKEN` repository secret). See
  [docs/release/releasing.md](docs/release/releasing.md#modrinth).
- The `universaladmin` Modrinth project page is now filled in: icon
  (`assets/icon.png`), title, summary, description, categories, license, and
  links - content lives in [docs/release/modrinth.md](docs/release/modrinth.md).

## [0.1.0-alpha.4] - 2026-08-26

## [0.1.0-alpha.3] - 2026-08-26

### Changed

- Releases are no longer flagged "Pre-release" on GitHub, `-alpha`/`-beta`/
  `-rc` versions included - the project's status is already communicated by
  the version string and the README, not GitHub's separate prerelease flag.
  See [docs/release/releasing.md](docs/release/releasing.md#version-scheme).
- The telemetry endpoint is no longer a config value: `telemetry.endpoint`
  has been removed from `config.yml` and `CoreSettings`, and every heartbeat
  now goes to the official `nicki41-telemetry` instance, fixed in
  `TelemetryBootstrap`. `telemetry.enabled` remains the only switch. This
  also means an old `config.yml` with a leftover `telemetry.endpoint` value
  (e.g. an explicit empty string) no longer has any effect.

### Fixed

- `auto-release.yml`'s version-bump commit/tag push had no recovery if
  `main` moved between checkout and push (hit for real when a backlog of
  delayed Actions runs raced each other) - now retries up to 5 times, each
  attempt recomputing from a fresh `git fetch`/`reset --hard origin/main`.

## [0.1.0-alpha.2] - 2026-08-26

### Added

- **Automatic update checks**: `dev.universaladmin.update` polls the GitHub
  releases API on a jittered interval (default every 6 hours) and, when a
  newer version exists, notifies staff (`universaladmin.update.notify`) and
  logs a console warning. `/admin update` (`universaladmin.update.apply`)
  downloads the latest release's jar into Bukkit's own `plugins/update/`
  folder (verified against its published SHA-256 checksum) - applied at the
  next restart, never a live hot-swap. `update.check-for-updates` (default
  `true`) is the on/off switch; `update.check-interval` configures the
  background timer.
- Empty-slot placeholders in the Players module's and Staff-Mode's
  inventory/ender-chest editors: a dyed-gray leather armor piece per slot
  (helmet/chestplate/leggings/boots), a grayed-out shield for offhand, and a
  visibly distinct hotbar row - `EmptySlotPlaceholder`, stripped back to
  `null` before ever writing to a real inventory.

### Changed

- `TelemetryPayload`'s wire format gained a `pluginId` field (fixed:
  `"universaladmin"`) and renamed `universalAdminVersion` to
  `pluginVersion` - the shape a shared, multi-plugin telemetry backend
  expects (see [docs/user/telemetry.md](docs/user/telemetry.md)). What's
  collected and the opt-out are unchanged.
- `telemetry.endpoint` now defaults to `https://telemetry.0nicki.de/v1/telemetry`
  (the official `nicki41-telemetry` instance) instead of empty - a fresh
  install now reports anonymous usage statistics out of the box;
  `telemetry.enabled: false` (or clearing `telemetry.endpoint`) still turns
  it off entirely. See [docs/user/telemetry.md](docs/user/telemetry.md).

### Fixed

- Closing a live inventory/ender-chest editor (Players module or the
  Staff-Mode Inspector tools) showed the "Done." confirmation twice - both
  halves of the split main-storage/equipment write notified independently;
  now only the first does.

## [0.1.0-alpha.1] - 2026-08-26

### Added

- **Anonymous usage statistics** (`dev.universaladmin.telemetry`):
  `InstallationIdentity`/`InstallationIdentityStore` (128 bits from
  `SecureRandom`, derived from nothing, persisted in
  `installation-id.yml`), `TelemetryPayload` (exactly six fields),
  `TelemetryClient` with `HttpTelemetryClient` (JDK HTTP client, short
  timeouts, no redirects, response discarded) and `NoOpTelemetryClient`,
  `TelemetryService` (enabled check on every heartbeat, player counts read
  on the main thread, the request runs in the background, failures are
  swallowed and only warned once per run), `TelemetryScheduler` (a
  delayed start, an interval with jitter, its own daemon thread), and
  `TelemetryBootstrap` (wiring with three outcomes: off / no endpoint /
  active). New settings `telemetry.enabled` (switchable live),
  `telemetry.endpoint` (empty by default - **nothing** is sent by
  default), and `telemetry.interval`. Full documentation including
  everything that's never collected:
  [docs/user/telemetry.md](docs/user/telemetry.md).
- Tests for telemetry with zero real network requests: id
  generation/persistence, "disabled means zero requests", a payload
  without player identities, serialization, an endpoint failure with no
  side effect, jitter bounds, scheduler cleanup.
- `LICENSE` (Apache-2.0, unmodified official license text) and a
  licensing decision with rationale in
  [docs/release/licensing.md](docs/release/licensing.md).
- `docs/development/architecture-rules.md` - the binding architecture,
  package, threading, settings, module, security, dependency, and test
  rules as regular developer documentation.
- `docs/release/releasing.md` - a reproducible release process
  (version → CHANGELOG → build → commit → tag → push).
- `.github/workflows/release.yml` - a `v*` tag automatically produces a
  GitHub release: a tag-vs-project-version check, a build including
  tests, the shaded jar plus a SHA-256 file as assets, prerelease
  detection for `-alpha`/`-beta`/`-rc`. `contents: write`, no additional
  secrets. Also callable directly (`workflow_call`, with a `tag` input) by
  `auto-release.yml` below, not only by its own tag-push trigger.
- `.github/workflows/auto-release.yml` - releases automatically after any
  push to `main` that changes more than docs/CI metadata: bumps the
  `-alpha.N` counter in `build.gradle.kts`, moves the `CHANGELOG.md`
  `[Unreleased]` section into a dated one, commits, tags, and calls
  `release.yml`. Skips a beta/rc/stable version bump (a bigger decision
  than this workflow makes on its own) and its own release commits (no
  loop). See [docs/release/releasing.md](docs/release/releasing.md).
- A `printVersion` Gradle task as the single source of the project
  version for CI (the tag check, artifact naming).
- Issue forms (`bug_report.yml`, `feature_request.yml`) and
  `ISSUE_TEMPLATE/config.yml` pointing at private vulnerability reporting
  instead of public security issues.

- Project architecture defined and documented (see ARCHITECTURE.md,
  docs/architecture/, ADRs under docs/architecture/decisions/).
- Gradle setup (Java 25 toolchain, Shadow plugin, SQLite/MySQL drivers,
  JUnit 5).
- Plugin bootstrap (`UniversalAdminPlugin`) with manual dependency wiring.
- Core abstractions: `Module`, `Action`/`ActionResult`, `GuiPage`,
  `Repository`/`Migration`, `PermissionRegistry`, `ServiceRegistry`,
  `AuditService`, `MessageService`, `NotificationService`, `TaskScheduler`.
- Eight module skeletons: Players, Moderation, Server, Worlds, Whitelist,
  Performance, Audit Log, Settings. `Players` as the complete reference
  implementation (repository → service → action, including a migration).
- Test infrastructure with example tests (registry, permission
  validation, a service with an in-memory fake, migrations against a
  real SQLite database).

- Core bootstrap lifecycle (`onLoad`/`onEnable`/`onDisable`) with a clear
  separation between critical core components (config, scheduler,
  storage/migrations, shared registries - failures abort the whole
  plugin start) and individual modules (failures are isolated, see
  `ModuleManager`).
- Internal module lifecycle system: `ModuleDescriptor` (metadata
  including dependencies, settings/permission namespace, an optional GUI
  icon placeholder), `ModuleState`
  (`DISCOVERED`/`LOADED`/`ENABLED`/`DISABLED`/`FAILED`), `ModuleRegistry`
  (state tracking), `ModuleManager` (dependency ordering, failure
  isolation), `ModuleResources` (automatic release of
  listeners/tasks/registry entries on disable). See
  docs/architecture/modules.md.
- `PluginStatus`/`ComponentStatus` - a live snapshot of version, uptime,
  active/failed modules, coarse database/web status.
- `/admin` command (aliases `/ua`, `/uadmin`) as a status placeholder,
  rendering `PluginStatus`.
- `unregister(...)` on `ActionRegistry`/`GuiRegistry`/`PermissionRegistry`
  for module-disable cleanup.
- Tests for the module lifecycle system (`ModuleManagerTest`):
  registration, a duplicate module id, lifecycle ordering, failure
  isolation, disable cleanup, dependency ordering, cycle detection, a
  missing dependency.

- Typed settings system (`dev.universaladmin.settings`):
  `SettingKey<T>`/`SettingType<T>`/`SettingValidator<T>`/`SettingDefinition<T>`/
  `SettingValue<T>`/`SettingRegistry`/`SettingsService`, with built-in
  types for string/boolean/int/long/double/Duration/enum/string list,
  validators for min/max/regex/multiple-of, and namespacing between core
  settings and future module/extension settings. See
  docs/development/settings.md and ADR 0007.
- `CoreSettings` - registers the complete `config.yml` tree (`general`,
  `database`, `gui`, `audit`, `modules`, `performance`, `maintenance`,
  `web`); `modules.*` now actually controls whether a built-in module
  gets registered at all.
- `config.yml` versioning: `config-version`, `ConfigMigration`/
  `ConfigMigrationRunner` (`dev.universaladmin.config`) - an existing
  user config gets migrated on future schema changes instead of
  overwritten.
- A safe `/admin reload` (`ReloadConfigAction`, permission
  `universaladmin.reload`) - only ever reloads UniversalAdmin's own
  `config.yml`, never Bukkit's global `/reload`. Restart-required
  settings are reported as "pending restart" on change instead of
  applied live.
- Multi-language message system: `lang/en_US.yml` (default/fallback) and
  `lang/de_DE.yml`, `YamlLocaleMessageService` with a fallback chain
  (active locale → `en_US` → a visible `[missing: ...]` marker with a
  one-time debug log), `ComponentMessages` as a MiniMessage renderer for
  in-game output, kept separate from plain string resolution (for web
  reuse).
- Tests for the settings/localization system: defaults, invalid values
  (parse/validation errors → fallback instead of a crash), enum parsing,
  duration parsing, duplicate setting keys, locale fallback, parameter
  interpolation.
- `DatabaseHealth` (`DISCONNECTED`/`CONNECTING`/`READY`/`FAILED`),
  tracked by `StorageService` and mapped onto the existing
  `ComponentStatus` in `UniversalAdmin#status()` - see
  docs/architecture/storage.md#health.
- SQLite pragmas in `DataSourceFactory` (`journal_mode=WAL`,
  `synchronous=NORMAL`, `foreign_keys=on`, `busy_timeout=5000`).
- `Transactions` (`dev.universaladmin.storage`) - a helper for repository
  methods that need to run multiple statements atomically (commit/
  rollback) over one connection.
- `StorageException` - a generic unchecked wrapper for `SQLException` in
  the storage foundation itself (`StorageService`, `Transactions`).
- `docs/user/database.md` - user documentation for SQLite vs.
  MySQL/MariaDB (when which, creating a MySQL user, behavior on startup
  failure, backups).
- Tests: `StorageServiceTest` (SQLite init, health states, failure on an
  invalid path), `TransactionsTest` (commit, rollback on error),
  `JdbcPlayerProfileRepositoryTest` (the repository foundation end to
  end against a real SQLite database), database-type parsing in
  `SettingTypesTest`, an explicit "no duplicate migration run" assertion
  in `MigrationRunnerTest`.
- A reusable in-game GUI framework (`dev.universaladmin.gui`):
  `AbstractGuiPage`/`AbstractListGuiPage` (navigation, permission-driven
  visibility, pagination, async loading with loading/empty/error state),
  `GuiSession`/`GuiSessionManager` (per-player state with no held
  `Player` references, cleanup on disconnect/a real inventory close),
  `GuiListener` as the single, centrally registered click/drag handler,
  `GuiLayout`/`Pagination` as pure, tested slot/pagination logic,
  `IconProvider`/`MaterialIconProvider` as the central material
  resolution, `ConfirmationDialog` (danger level NORMAL/WARNING/
  DANGEROUS), `SelectionDialog`, `GuiTextInput` (a free-text/search flow
  via the Paper dialog API instead of packet hacks/anvil/sign) - see
  docs/development/gui-framework.md.
- `MainMenuPage`: the `/admin` main menu with one button per built-in
  module, filtered by actual `ModuleState.ENABLED` and permission;
  `PlaceholderGuiPage` as the "not built yet" target page per module. A
  new permission `universaladmin.menu.open`.
- `ModuleDescriptor.icon()` set for all eight built-in modules
  (previously `null` everywhere, see `GuiIcon`).
- Tests for the GUI framework: `PaginationTest`, `GuiLayoutTest`,
  `GuiSessionTest`, `GuiSessionManagerTest`, `GuiButtonVisibilityTest`,
  `GuiClickContextTest` (the navigation stack), `GuiListenerTest`
  (click cancel/dispatch, close/quit cleanup).
- `GuiView#onChange` - a live-sync hook `GuiListener` runs one tick after a
  click/drag it let through, on top of the existing `onClose`. Used by the
  Players module's inventory/ender chest editors and the new Staff-Mode
  Ender Chest Inspector tool so an edit mirrors onto the real target
  immediately instead of only when the viewer closes the GUI.
- Staff-Mode **Ender Chest Inspector** tool (`EnderChestInspectorPage`),
  alongside a now-live (not read-only) Inventory Inspector - both gated on
  the Players module's own `...inventory.edit`/`...enderchest.edit`
  permissions. See [docs/user/modules/staff-tools.md](docs/user/modules/staff-tools.md).
- Staff-Mode **Teleport Picker** tool: opens a player list, then a
  "teleport to them"/"bring them" choice, routed through the Players
  module's own audited `TeleportPlayerAction` - replaces Random Teleport.
- Staff-Mode target tracking now searches by look direction within a
  configurable range (`moderation.staffmode.target-range-blocks`, default
  40) instead of a 6-block, wall-blocked ray trace, and sends a persistent
  actionbar naming the current target. The Player Inspector tool's head
  always shows the current target now, regardless of which tool is held.
- Per-action success messages in the Moderation GUI (`ModerationGuiActions`)
  - naming the target, reason, and duration where relevant - instead of a
  single generic "Done." for every action.
- Audit log GUI filtering by actor, module, and time range
  (`AuditLogFilterPage`), on top of the existing success/failure toggle -
  the query layer already supported all of this, only the GUI was missing.
  See [docs/user/audit-log.md](docs/user/audit-log.md).

### Changed

- The binding development rules now live under
  `docs/development/architecture-rules.md`; every reference in source
  code and documentation points there.
- `README.md` brought to a public-facing state (status, features,
  requirements, quick start, database, permissions, anonymous
  statistics, extensions/web app explicitly marked as planned, license).
- `CONTRIBUTING.md` and `SECURITY.md` reworked: setup, architecture,
  tests, style, PR flow and private vulnerability reporting respectively,
  a telemetry/privacy section, and supported versions.
- `.github/workflows/build.yml` runs on `push`/`pull_request` against
  `main`, sets up JDK 25 (build toolchain) and 21 (runs Gradle), uploads
  the jar under a versioned artifact name.
- The PR template gained checklist items for secrets, localization,
  threading, and telemetry documentation.
- `.gitignore` gained build/IDE/runtime/database/secret patterns as well
  as purely local notes files.
- Target Paper version set to 26.2 (`paper-api:26.2.build.115-stable`).
  Paper 26.2 is only published for JVM 25+, so the Java toolchain was
  raised from the originally planned version 21 to 25 - see
  `build.gradle.kts` and `plugin.yml` (`api-version: '26.2'`).
- `Module` is now two-phase (`onLoad` then `onEnable`) instead of just
  `onEnable`; `id()`/`displayName()` are replaced by `descriptor()`
  (`ModuleDescriptor`). All eight built-in modules updated.
- `UniversalAdminCommand` now shows a `PluginStatus` report instead of
  just the list of loaded modules, and is registered under `/admin`
  instead of `/ua`.
- `ConfigService`/`YamlConfigService` removed, replaced by
  `SettingsService`/`YamlSettingsService` (see above).
  `UniversalAdmin.config()` is now `UniversalAdmin.settings()`
  (`SettingsService`) plus `UniversalAdmin.settingRegistry()`
  (`SettingRegistry`).
- `messages_en_US.yml` (a file at the plugin root) replaced by
  `lang/en_US.yml`/`lang/de_DE.yml`; `YamlMessageService` (one fixed
  locale) replaced by `YamlLocaleMessageService` (multiple locales, a
  fallback chain, reads the active locale live from `general.language`).
- `/admin` (no argument) now opens `MainMenuPage` for a player instead
  of the text status report; for console/command blocks, the status
  report is unchanged. `/admin reload` unchanged. `UniversalAdmin`'s
  constructor gained a new required field, `GuiFramework`
  (`UniversalAdmin#guiFramework()`).

- **Players** complete: a player browser GUI (online/offline/last-seen/
  search), a profile page, ~20 actions (teleport, heal, effects,
  gamemode, inventory/ender chest editor), fine-grained permissions,
  audit. See [docs/user/modules/players.md](docs/user/modules/players.md).
- **Moderation** complete: a punishment repository/service (kick/ban/
  tempban/ipban/mute/tempmute/warn/freeze/unban/unmute/removewarn/
  unfreeze), join/chat enforcement, a GUI wizard, fine-grained
  permissions, audit, plus vanish/godmode/no-collision/staff mode
  (crash-safe snapshot/recovery, `/admin staff recover`). See
  [docs/user/modules/moderation.md](docs/user/modules/moderation.md) and
  [docs/user/modules/staff-tools.md](docs/user/modules/staff-tools.md).
- **Server** complete: a live dashboard (version/uptime/players/memory/
  CPU/DB status/modules), broadcast (message/title/actionbar), its own
  maintenance-mode system (repository/service, join enforcement,
  allow-list), shutdown/restart with dangerous-action confirmation and a
  configurable countdown + cancel, `/admin server ...`, fine-grained
  permissions, audit. See [docs/user/modules/server.md](docs/user/modules/server.md).
- **Worlds** complete: a world browser/profile (environment/seed/spawn/
  border/players/chunks/entities/time/weather/difficulty),
  teleport/spawn/time/weather/difficulty actions, world border
  management, a dynamic gamerule GUI (reads `World#getGameRules()` at
  runtime), fine-grained permissions (seed kept separate), audit. See
  [docs/user/modules/worlds.md](docs/user/modules/worlds.md).
- **Whitelist** complete: native whitelist wrapping (enable/disable/
  list/add/remove) plus its own metadata (added-by/at, reason, notes,
  expiration), time-limited entries with a join check and an hourly
  sweep, a strict ownership model, fine-grained permissions, audit. See
  [docs/user/modules/whitelist.md](docs/user/modules/whitelist.md).
- **Audit Log** complete (GUI side): a full `AuditEvent` (actor/action/
  module/target/source/success/reason/old-new value/world position/
  metadata/correlation id), automatically populated by `ActionExecutor`,
  a filtered, paginated `AuditService#query`, a working GUI (a list with
  a success/failure filter, a detail page), hourly configurable
  retention. `/admin audit` commands still open. See
  [docs/user/audit-log.md](docs/user/audit-log.md).
- **Performance** complete: cached TPS/MSPT/memory/world/entity sampling
  on a configurable interval (never recomputed per GUI render), a
  dashboard, a per-world performance view, an entity overview (grouped
  by type/world), a short in-memory history, staff alerts on TPS/MSPT/
  memory thresholds, a narrowly scoped Entity Clear (never players,
  configurable protected types, preview, confirmation, audit),
  fine-grained permissions. See
  [docs/user/modules/performance.md](docs/user/modules/performance.md).

### Fixed

- **Critical: every module-owned table was never created**
  (`player_profiles`, `punishments`, `server_maintenance_state`,
  `whitelist_entries`, `vanish_state`, ...) - `UniversalAdminPlugin`
  only called `storage.migrations().runPending()` once, before any
  module was even enabled. But every module only registers its own
  migration(s) *during* its own `onEnable` - without a second
  `runPending()` call afterward, those migrations never ran. Affected
  every module except Audit Log (whose two migrations were registered
  before that one, single call) - among other things, the cause of
  "storage error" in the Moderation/punishment module, "unexpected
  error" in maintenance mode, and broken whitelist/vanish state on join.
  See [docs/architecture/threading.md](docs/architecture/threading.md).
- `worlds.gui.action` existed twice as a sibling key in
  `lang/en_US.yml`/`de_DE.yml` (once for success/error messages, once
  for button labels) - YAML doesn't really allow that, Bukkit's loader
  silently takes the last entry (a "duplicate keys found" warning at
  startup). Button labels renamed to `worlds.gui.buttons`.
- Player lists (Players, Whitelist, audit log detail) showed the generic
  Steve head everywhere instead of the real player skin - `GuiItem`
  gained a new `playerHead(OfflinePlayer, ...)` factory method
  (`SkullMeta#setOwningPlayer`), now used by every player-representing
  tile.
- **Critical: the plugin didn't start at all on a real Paper server**
  (`HikariConfig: Failed to load driver class dev.universaladmin.libs.sqlite.JDBC`).
  Two interacting Shadow-plugin issues, neither detectable by the test
  suite (which runs against unshaded, unminimized dependencies) - only
  surfaced by an actual server start:
  - `shadowJar { minimize() }` stripped both JDBC drivers from the jar
    entirely, because they're only loaded reflectively via
    `HikariConfig#setDriverClassName` - invisible to `minimize()`'s
    static-reachability analysis. Both drivers are now explicitly
    excluded from minimization.
  - `org.sqlite` was being relocated like any other dependency - which
    breaks the JNI linking of the bundled native SQLite library
    (`ClassNotFoundException: org/sqlite/core/NativeDB`), since its
    compiled binary hardcodes the original class name. `org.sqlite` is
    now deliberately no longer relocated (but still ships bundled) - see
    [docs/development/architecture-rules.md](docs/development/architecture-rules.md)
    and `build.gradle.kts`.
  - A new Gradle task, `verifyShadedJarDrivers` (runs as part of
    `check`/`build`): opens a real SQLite connection exclusively through
    the finished, built jar (no other classpath entry), so this exact
    class of failure can never come back unnoticed again. See
    `ShadedJarDriverSmokeTestMain`.
- `PlayerProfileIndexMigration`/`ModerationPunishmentIndexMigration`
  used `CREATE INDEX IF NOT EXISTS`, which real MySQL (unlike
  SQLite/MariaDB) doesn't support - removed, since `MigrationRunner`
  already guarantees every migration runs at most once. See
  [docs/architecture/storage.md#dialect-differences](docs/architecture/storage.md#dialect-differences).
- Freezing a player sent the target two notices for one click of the
  Freeze Tool - `PlayerInteractEntityEvent` fires once per hand for a
  single physical right-click, and `StaffModeGuardListener` dispatched on
  both. Now only the main-hand firing is handled.
- `core:performance.clear-entities` failed to record its audit event on
  every run (`IllegalArgumentException: Unsupported audit metadata value
  type ... ListN`) - its metadata put the selected entity types in
  directly as a `List`, which the audit metadata codec only ever accepted
  String/Number/Boolean/null for. Now joined into a single string, same as
  the value already shown in the audit summary.
