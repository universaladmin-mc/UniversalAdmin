# Roadmap

What exists, and what comes next. Not a dated plan - order within a block is
a recommendation. Every item builds on the structure in
[ARCHITECTURE.md](ARCHITECTURE.md), not around it. Ideas and votes are
welcome as [GitHub issues](https://github.com/universaladmin-mc/UniversalAdmin/issues).

## Done - 1.1.0

- [x] Modular core: modules with lifecycle and dependencies, actions with
      central authorization/validation/audit, typed settings, layered
      translations (`en_US`, `de_DE`), async storage (SQLite, MySQL/MariaDB)
      with versioned migrations, GUI framework
- [x] Players, Moderation (incl. staff tools and staff notes), Reports, Chat,
      Server, Worlds, Whitelist, Performance, Audit Log, Settings
- [x] Commands for every module, `/admin help`, tab completion, optional
      shortcuts ([ADR 0011](docs/architecture/decisions/0011-command-framework.md))
- [x] In-game settings editor
- [x] Team notifications for punishments, report alerts, join hints
- [x] Escaped message arguments and upgrade-safe language files
      ([ADR 0012](docs/architecture/decisions/0012-safe-messages.md))
- [x] Public extension API `universaladmin-api`, extension loading,
      developer guide and example
      ([ADR 0010](docs/architecture/decisions/0010-extension-api.md))
- [x] Official extension: Discord logging
- [x] **Network mode** (Velocity/BungeeCord): network overview, find,
      cross-server teleports, network-wide punishments, staff chat, reports,
      alerts and audit log over one shared MySQL database, plus a Velocity
      plugin that refuses banned players at the proxy
      ([ADR 0013](docs/architecture/decisions/0013-network-mode.md))
- [x] One jar for Paper 1.21.7 - 26.3, new Paper versions tested and
      published automatically
- [x] Releases: GitHub (plugin, Discord extension, Velocity plugin, API
      jars), Modrinth, update checker, anonymous statistics with opt-out

## Done - 1.2.0

- [x] **Import** bans/mutes/warnings/kicks from LiteBans, AdvancedBan, the
      vanilla ban lists and EssentialsX
      ([ADR 0015](docs/architecture/decisions/0015-punishment-import.md))
- [x] **PlaceholderAPI** placeholders as an official extension, backed by
      status services in the extension API

## Done - 1.2.1

- [x] **Maintenance per server or for the whole network**, enforced at the
      proxy by UniversalAdmin Velocity
      ([ADR 0017](docs/architecture/decisions/0017-maintenance-scopes.md))

## Done - 1.3.0

- [x] **Punishment templates and escalation** ("spam": 1st = warn, 2nd =
      1h mute, 3rd = 1d mute) in the wizard, via `/punish <player> <template>`
      and in the Discord bot
      ([ADR 0018](docs/architecture/decisions/0018-punishment-templates.md))
- [x] **Network whitelist** and **vanish across the network**
      ([ADR 0019](docs/architecture/decisions/0019-network-whitelist-and-vanish.md))

## Done - 1.4.0

- [x] **Alt detection** based on keyed address hashes (opt-in, raw addresses
      are not stored): possible alts on the profile and a staff hint when an
      alt of a banned account joins
      ([ADR 0020](docs/architecture/decisions/0020-alt-detection.md))
- [x] A **BungeeCord** proxy plugin next to the Velocity one, both on shared
      proxy code ([ADR 0021](docs/architecture/decisions/0021-bungeecord-proxy-plugin.md))

## Done - 1.5.0

- [x] **Forms instead of one prompt after another**: punishing, adding to
      the whitelist, title broadcasts, enabling maintenance, the world
      border, player values (sliders) and teleporting to coordinates - each
      one Paper dialog
      ([ADR 0023](docs/architecture/decisions/0023-dialog-forms.md))
- [x] **Chat, voice and full mutes** and **UniversalAdmin Voice Chat** for
      Simple Voice Chat: voice mutes, silent vanish, voice status on the
      profile, a staff voice group
      ([ADR 0022](docs/architecture/decisions/0022-mute-kinds-and-voice-chat.md))
- [x] **The whole network in the player menu**: every player with their
      server, a server filter and sorting, send and bring from the profile
- [x] Licensed under **GPL-3.0-or-later**; the extension API stays
      Apache-2.0

## Done - 1.6.0

- [x] **Automatic updates**: new releases of the plugin and the official
      extensions are downloaded, checked and installed at the next restart
      (within the same major version, switchable in `config.yml`); a notice
      for staff who join later; the proxy plugins check too
      ([ADR 0024](docs/architecture/decisions/0024-updates-and-repositories.md))
- [x] **A GitHub organization**, [universaladmin-mc](https://github.com/universaladmin-mc):
      the core and every official extension in their own repositories, each
      with its own releases, changelog and update check
- [x] **New logos** for UniversalAdmin, the official extensions and the
      proxy plugins
- [x] **Documentation website**:
      [universaladmin-mc.github.io/UniversalAdmin](https://universaladmin-mc.github.io/UniversalAdmin/)

## Done - 1.7.0

- [x] **Nick system** (built-in module, for staff): `/nick Notch` - the
      name and skin of an existing account everywhere (chat, tab list,
      name tag); or name and skin chosen separately, or both at random.
      Staff with the permission see the real name in menus, punishments and
      the audit log; a nick survives rejoining and works across a network
      ([ADR 0025](docs/architecture/decisions/0025-nick-system.md))
- [x] **TAB** (by NEZNAMY) as an official extension: tab list and name tags
      follow vanish, staff mode and nicks

## Done - 1.8.0

- [x] **Menu overhaul**: a frame in every module's colour, glass colours
      with one meaning, no flicker between pages; the main menu as a
      dashboard with "My status"; the player profile as the hub for
      everything about a player; player lists as tabs; readable settings
      ([ADR 0027](docs/architecture/decisions/0027-menu-design-system.md))
- [x] **Staff status in the action bar** and a **visible freeze** (a title
      for the frozen player, a marker above their head for staff)

## Done - 1.9.0

- [x] **Menu overhaul, part 2**: server, worlds and performance as hubs with
      live values and traffic lights; the audit log in the server's language
      ([ADR 0028](docs/architecture/decisions/0028-audit-log-in-the-servers-language.md))
- [x] Minecraft's names in the game's language, new options written into an
      existing config.yml, a first-join hint for staff
- [x] For extensions: hub rows, live pages, translated audit entries, their
      own migration numbering; a binary compatibility check against every
      supported Paper version in the build

## Later - 1.x

Additive, no breaking changes.

- [ ] **Language per player**: menus and messages in each staff member's
      game language, broadcasts in the server's
- [ ] **Rework of existing features**: go through the modules and polish
      what grew step by step - behaviour, texts and settings that should
      work more alike
- [ ] **Vault** as an official extension: balance and rank on the player
      profile
- [ ] **CoreProtect** as an official extension: block lookups and rollbacks
      from the player profile, e.g. together with a griefing ban
- [ ] **WorldGuard** as an official extension: the regions at a player's
      position on the profile, regions and their flags in the menu
- [ ] **MiniMOTD** as an official extension: a maintenance MOTD and a
      player count without vanished players
- [ ] Forms for whole settings sections (checkboxes, sliders, option lists)
- [ ] Ticket/support system as an official extension built on the API
- [ ] More languages (community translations - `lang/<locale>.yml` is all it
      takes)
- [ ] Audit log export (CSV) and per-module retention
- [ ] Loading/unloading worlds from the Worlds module
- [ ] A curated extension list in the docs

## Later - 2.0 candidates

- [ ] **Web panel**: REST API over the same actions, live dashboard via
      WebSockets, web logins separate from Minecraft accounts - see
      [docs/architecture/web-future.md](docs/architecture/web-future.md)
- [ ] **Extension builder in the browser**: build extensions by drag and
      drop - menu pages on the chest grid, commands, logic blocks
      (event → condition → action), settings, permissions, translations -
      for people who don't write Java. The result is a real extension
      project against `universaladmin-api` with code blocks and an "eject
      to IDE" path, so an extension can grow as complex as a hand-written
      one - see [docs/architecture/extension-builder.md](docs/architecture/extension-builder.md)
- [ ] **Sharing extensions on the same site** (the extension registry):
      a page per extension with versions and downloads, search, and "open
      in builder" to start from a shared project

## Deliberately not planned

- Folia support - only once there is real demand; the scheduler abstraction
  keeps the door open.
- Packet-level features (ProtocolLib, NMS) in the core - those belong in an
  extension, see [docs/development/architecture-rules.md](docs/development/architecture-rules.md).
- Spigot/CraftBukkit or Minecraft versions below 1.21.7.
