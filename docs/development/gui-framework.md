# GUI Framework

This document describes the reusable in-game GUI framework under
[`dev.universaladmin.gui`](../../src/main/java/dev/universaladmin/gui) and,
at the end, how a module builds its own page on top of it. It replaces the
"no framework decision yet" state from
[docs/architecture/gui.md](../architecture/gui.md) - that document remains
the short version of the architecture rule (a frontend only calls a
service/action), this one covers the *how*.

Currently two concrete pages from the framework itself exist: the main
menu (`MainMenuPage`) and a placeholder page per built-in module
(`PlaceholderGuiPage`) - see "Main Menu Skeleton" below. No module has a
real feature GUI today; that's deliberately out of scope for this task
(see ROADMAP.md Phase 1).

## The One Architecture Rule Still Holds

```
GUI → application service / action → domain
```

A click handler (`GuiButton.ClickHandler`) calls a service or an `Action`,
never SQL, never multi-line computation. The framework doesn't change
that - it just gives this rule a shared, tested foundation instead of
every page bringing its own `InventoryClickListener`. See
[docs/architecture/decisions/0004-gui-service-separation.md](../architecture/decisions/0004-gui-service-separation.md).

## Building Blocks at a Glance

| Building block | Class | Purpose |
|---|---|---|
| Page | [`GuiPage`](../../src/main/java/dev/universaladmin/gui/GuiPage.java) | The minimal interface from ADR-0004 (`id()`, `open(Player)`). Every framework page implements it via `AbstractGuiPage`. |
| Base class | [`AbstractGuiPage`](../../src/main/java/dev/universaladmin/gui/AbstractGuiPage.java) | Renders the navigation bar (back/refresh/close), the rest is `renderContent(...)`. |
| List base class | [`AbstractListGuiPage<T>`](../../src/main/java/dev/universaladmin/gui/AbstractListGuiPage.java) | Async loading + pagination + loading/empty/error state, implemented once. |
| Session | [`GuiSession`](../../src/main/java/dev/universaladmin/gui/GuiSession.java) / [`GuiSessionManager`](../../src/main/java/dev/universaladmin/gui/GuiSessionManager.java) | Per-player state (navigation history, attributes) - see "Player Session". |
| Visible element | [`GuiItem`](../../src/main/java/dev/universaladmin/gui/GuiItem.java) / [`GuiButton`](../../src/main/java/dev/universaladmin/gui/GuiButton.java) | `GuiItem` is purely visual, `GuiButton` additionally has a click handler + optional permission. |
| Rendering | [`GuiView`](../../src/main/java/dev/universaladmin/gui/GuiView.java) | A rendered inventory for one player; the only place that calls `Bukkit.createInventory`. |
| Layout | [`GuiLayout`](../../src/main/java/dev/universaladmin/gui/GuiLayout.java) | The one slot table - see "Slots". |
| Pagination | [`Pagination<T>`](../../src/main/java/dev/universaladmin/gui/Pagination.java) | Pure logic (no Bukkit), used by `AbstractListGuiPage`. |
| Click routing | [`GuiListener`](../../src/main/java/dev/universaladmin/gui/GuiListener.java) | The one Bukkit listener for every GUI. |
| Icons | [`IconProvider`](../../src/main/java/dev/universaladmin/gui/IconProvider.java) / [`MaterialIconProvider`](../../src/main/java/dev/universaladmin/gui/MaterialIconProvider.java) | See "Icons". |
| Confirmation | [`ConfirmationDialog`](../../src/main/java/dev/universaladmin/gui/ConfirmationDialog.java) | Yes/no with a danger level. |
| Selection | [`SelectionDialog`](../../src/main/java/dev/universaladmin/gui/SelectionDialog.java) | Pick one from a list, built on `AbstractListGuiPage`. |
| Text input | [`GuiTextInput`](../../src/main/java/dev/universaladmin/gui/GuiTextInput.java) | Free text via the Paper dialog API - see "Search". |
| Form | [`GuiForm`](../../api/src/main/java/dev/universaladmin/gui/GuiForm.java) | Several inputs in one Paper dialog - text, sliders, checkboxes, option lists - see "Forms". |
| Bundle | [`GuiFramework`](../../src/main/java/dev/universaladmin/gui/GuiFramework.java) | `GuiSessionManager` + `IconProvider`, passed to every page via the constructor (`UniversalAdmin#guiFramework()`). |

## Slots

No magic numbers per feature - every page renders into the same six-row
layout, defined once in `GuiLayout`:

```
Row 0: Back (0) · Main menu (1) · page icon (4) · Refresh (7) · Close (8)
       - the rest in the module's colour (ModuleDescriptor#color)
Rows 1-4: content area, 36 slots - GuiLayout.contentSlot(0..35)
Row 5: tools (45-47 TOOL_LEFT_*, 51-53 TOOL_RIGHT_*) · Previous (48) ·
       page indicator (49) · Next (50)
```

Every slot a page leaves empty becomes a black pane without a tooltip.
Glass colours keep one meaning on every page (`GuiGlass`, see
[ADR 0027](../architecture/decisions/0027-menu-design-system.md)): black
frame and empty space, light grey divider, the module colour for "where
am I", green on/confirm, red off/danger, orange warning, white "nothing
here". `GuiItem.pane(glass)` is a plain pane, `GuiItem.glass(glass, name,
lore)` one with a text, e.g. a section label.

A page rendering its own content exclusively uses
`GuiLayout.contentSlot(index)` instead of a raw number - so "where is the
back button" stays a single, platform-wide answer.

## Navigation

The entire navigation mechanism is exactly one concept: **opening a page
is always `GuiPage#open(Player)`.**

- **Forward** (`GuiClickContext#open(GuiPage)`): remembers how the current
  page redraws itself (`() -> currentPage.open(viewer)`), pushes that onto
  `GuiSession`'s history, then opens the next page.
- **Back** (`GuiClickContext#back()`): pops the top history entry and runs
  it - which simply calls `open(...)` on the previous page again. With no
  history, `back()` closes the inventory.
- **Refresh**: the refresh button in `AbstractGuiPage` calls
  `this.open(viewer)` again on the same page - no special case, no second
  method.

No separate stack of `GuiPageId`s that would need to be kept in sync by
hand - "how do I get back here" is always executable code, never a piece
of data.

## Pagination

`Pagination<T>` is pure, Bukkit-free logic: slice calculation, clamping for
an empty/short list, `hasPrevious()`/`hasNext()`. Changing pages only
changes the page number (stored in `GuiSession` under `<pageId>.page`) and
calls `open(viewer)` again - the same refresh mechanism as above.

## Async Data

`AbstractListGuiPage<T>` is the complete reference for the required flow:

```
open() → render a loading placeholder (immediately)
       → loadItems(viewer) on the TaskScheduler
       → whenComplete(...) → scheduler.runOnMainThread(...)
       → repopulate the GuiView
```

**No inventory mutation off the main thread** - see
[docs/architecture/threading.md](../architecture/threading.md). A result
that arrives after the player has already left the page is detected
(`viewer.getOpenInventory().getTopInventory().getHolder() == view`) and
discarded, instead of mutating a closed/foreign inventory.

An error in the loaded `CompletableFuture` renders the error state
(`gui.error`, refresh retries); an empty list renders the empty state
(`gui.empty`); while loading, the loading state (`gui.loading`) - all
three via `IconProvider`/`MessageService`, never hardcoded.

## Permissions

A `GuiButton` optionally carries a `PermissionNode`. The rule is
mechanically enforced, not just documented: `GuiView#place(int, GuiButton, Player)`
only places the button if `viewer.hasPermission(...)` is true - otherwise
the slot stays empty ("hide by default", no grayed-out disabled state).
`GuiButton#handle` checks again on click as a safety net (defense against
a click racing a permission change).

**GUI permission is display only.** The service/action a button ultimately
calls must re-authorize itself - see
[docs/architecture/decisions/0004-gui-service-separation.md](../architecture/decisions/0004-gui-service-separation.md)
and [SECURITY.md](../../SECURITY.md). An optional "disabled but visible"
state is deliberately not a framework feature: a page that wants that
renders its own `GuiItem` (with no click handler) instead of a
`GuiButton` when `viewer.hasPermission(...)` fails.

## Icons

No `Material` scattered through feature code. `IconProvider#resolve(GuiIcon)`
is the only place that turns a [`GuiIcon`](../../src/main/java/dev/universaladmin/module/GuiIcon.java)
(materialKey + label, see `ModuleDescriptor.icon()`) into a `Material`;
`MaterialIconProvider` is the default implementation (unknown key →
`PAPER` + a one-time warning, the same fallback principle as
`YamlSettingsService` for an invalid config value). The small fixed set of
framework icons (back/close/refresh/pagination/loading/empty/error/
confirm/cancel) are default methods on `IconProvider` - here too, no page
picks its own `Material`.

## Player Session

`GuiSession` (navigation history + attribute bag) and `GuiSessionManager`
(`Map<UUID, GuiSession>`) **never** hold a `Player` reference, only the
`UUID` - holding a `Player` object long-term is a classic way to
accumulate stale/dead references once the player leaves the server and a
new `Player` object is created for the same session. Every framework
class always fetches the live `Player` fresh from the triggering Bukkit
event.

A registered `AbstractGuiPage` is a singleton shared by every player (like
any other service in this codebase) - so it may hold **no** per-player
instance state itself (no `private int currentPage`!). That's exactly why
`GuiSession` exists: state that only applies to one player lives there,
keyed by its own `GuiPageId` (e.g. `core:players.list.page`), never as an
instance field of the page.

**No memory leak:** `GuiListener` removes a session as soon as the
associated inventory closes for a "real" reason (`PLAYER`, `DISCONNECT`,
...) - not on `OPEN_NEW` (that's us navigating to the next page ourselves).
`PlayerQuitEvent` additionally removes it defensively, independent of the
close-event ordering. See
[`GuiSessionManager`](../../src/main/java/dev/universaladmin/gui/GuiSessionManager.java)'s
class comment for the full argument.

## Click Handling

A single [`GuiListener`](../../src/main/java/dev/universaladmin/gui/GuiListener.java),
registered once in `UniversalAdminPlugin#bootstrapCore` - no feature
registers its own `InventoryClickEvent` handler. Detection is via
`inventory.getHolder() instanceof GuiView` (not the title string, which
could differ per locale).

Default behavior for every page: the entire click event is cancelled
(`setCancelled(true)`) - nothing can be taken out of, dragged into, or
shift-clicked out of the GUI. A click on a slot with a `GuiButton` fires
its `ClickHandler` with the matching `GuiClickType` (LEFT/SHIFT_LEFT/
RIGHT/SHIFT_RIGHT/MIDDLE/OTHER); a click on an empty/decorative slot is
only cancelled, nothing more.

`GuiView#editable(boolean)` turns off the automatic cancel, allowing free
drag/drop within the (synthetic, `GuiListener`-protected) `GuiView` -
used e.g. by
`dev.universaladmin.modules.players.gui.PlayerInventoryPage`/
`PlayerEnderChestPage` for the inventory/ender chest editor. A slot with a
registered `GuiButton` stays protected regardless (see `GuiListener`) - so
individual slots (e.g. decorative filler between two areas) can still be
locked on an editable page by placing them as a `GuiButton` with a no-op
handler instead of a plain `GuiItem`.

`GuiView#onClose(Consumer<GuiView>)` is the attachment point for an
editable page that wants to skip a separate save button: the callback runs
once the view actually closes (not when navigating to another
UniversalAdmin page - the same `OPEN_NEW` distinction as session cleanup),
by which point every drag/click by the player has already landed in the
`Inventory`.

`GuiView#onChange(Consumer<GuiView>)` is the same idea, but per click/drag
instead of only at close - for a page where "live" needs to mean instantly
mirrored onto the real target, not just "no save button." `GuiListener`
schedules it one tick after a click/drag it let through (`view.editable()`
and not a `GuiButton` slot), since Bukkit only applies the vanilla move to
the `Inventory` *after* the triggering event's handlers return, in the same
tick - by the next tick it's guaranteed visible. `PlayerInventoryPage`/
`PlayerEnderChestPage` (and the Staff Mode Inventory/Ender Chest Inspector
tools, which reuse the identical mechanism) register both: `onChange` reads
the mirror's current contents and calls
`SetPlayerInventoryContentsAction`/`SetPlayerEnderChestContentsAction`
through `ActionExecutor` silently (no chat message per item moved), and
`onClose` does the same once more as a final flush, this time with the
usual chat confirmation. "Live" here always means "no save click needed,
and the target sees it immediately", never "bypass `ActionExecutor` and
write straight into the real inventory" (see docs/user/modules/players.md
for the full reasoning on why a directly opened `PlayerInventory` of the
target is deliberately *not* the chosen path).

## Confirmations

`ConfirmationDialog.open(...)` renders a small inventory (not the Paper
dialog API - see "Search" below for the trade-off between the two) with a
title, description, confirm/cancel button, and a `DangerLevel`
(`NORMAL`/`WARNING`/`DANGEROUS`) that determines the confirm button's color
via `IconProvider#confirm(DangerLevel)` (lime/yellow/red wool). Ephemeral:
not registered in `GuiRegistry`, a click handler opens a fresh instance
directly with the concrete parameters (e.g. "ban player X?"). Intended for
ban/clear-inventory/restart/shutdown/entity-clear, once the respective
modules need it - not wired up yet.

## Selection

`SelectionDialog.open(...)` is pick-one-from-a-list, implemented as a thin
adapter over `AbstractListGuiPage` - the same pagination/async machinery
as any other list page, just with a one-off callback instead of a
registered `GuiPageId`.

## Search

Minecraft inventories have no text field, and this project rules out
packet hacks/ProtocolLib/NMS (see docs/development/architecture-rules.md).
Options considered:

- **Anvil GUI hack** (a renamable anvil as text input): only works via an
  "empty" recipe plus click interception, in practice a packet-/NMS-
  adjacent behavior depending on the implementation - rejected.
- **Chat input session** (intercept the player's next chat message):
  works, but collides with `AsyncPlayerChatEvent`/other plugins listening
  to chat, and the player types "somewhere in chat", not in a recognizable
  UI context - no clean state, no timeout without extra work.
- **Sign API**: forces a sign texture/block placement as the input
  surface - functional, but a visual and UX mismatch for a menu that's
  otherwise entirely inventory-based.
- **Paper Dialog API** (`io.papermc.paper.dialog.Dialog`,
  `Player#showDialog`, `TextDialogInput`): a server-defined,
  client-rendered input form - not a packet hack, an official part of the
  target Paper version (confirmed in this project's `paper-api` jar.
  **Chosen.**

[`GuiTextInput.request(...)`](../../src/main/java/dev/universaladmin/gui/GuiTextInput.java)
builds a `Dialog` with a text field plus submit/cancel button; each button
is a `DialogAction.customClick(...)` callback with `uses(1)` and a
two-minute lifetime, so a dialog that's never answered can't be replayed.
The callback runs (like a command or a click event) on the main thread -
a caller that needs blocking IO afterward has to hop off via
`TaskScheduler` itself, same as everywhere else.

A "search" button on a future list page would call, for example:

```java
GuiTextInput.request(
        viewer, title, label, previousQuery, submitLabel, cancelLabel,
        query -> { /* filter results, reopen the page */ },
        () -> { /* do nothing, or go back to the list */ });
```

## Forms

Where a step needs more than one value, one form asks for all of them
instead of a chain of prompts ([ADR 0023](../architecture/decisions/0023-dialog-forms.md)).
[`GuiForm`](../../api/src/main/java/dev/universaladmin/gui/GuiForm.java)
builds one Paper dialog:

```java
GuiForm.builder(title)
        .message(intro)                                  // a line of text above the inputs
        .text("reason", reasonLabel, "", 255)            // single line, explicit maximum length
        .multilineText("notes", notesLabel, "", 255, 4)  // up to 4 visible lines
        .number("speed", speedLabel, -1, 1, 0.05f, 0.2f) // a slider: min, max, step, initial
        .toggle("kick", kickLabel, false)                // a checkbox
        .choice("kind", kindLabel, options, "FULL")      // cycles through GuiForm.Option(id, label)
        .open(viewer, submitLabel, cancelLabel,
                response -> apply(response.textOrNull("reason"), response.toggle("kick")),
                () -> page.open(viewer));
```

- Keys are lower-case letters, digits and `_`, unique per form.
- A form only collects: the submit callback hands the values to the same
  action as before, which validates, checks permissions and audits. A value
  that cannot be parsed reports the error and reopens the page.
- Sliders only for small, bounded ranges (health, food, XP percent,
  speeds); coordinates, border sizes and levels stay text fields.
- Text fields need an explicit maximum length - the dialog default is 32
  characters. `GuiTextInput` uses 255.
- Like `GuiTextInput`: single-use callbacks with a lifetime of a few
  minutes, run by Paper on the main thread.
- Tests hand code that handles a form a `GuiForm.Response.of(Map)`.

## Main Menu Skeleton

`MainMenuPage` (`/admin` with no arguments, see below) shows one button per
built-in module, filtered by:

1. **The module is actually `ENABLED`** (`ModuleRegistry#state(...)`) - a
   module disabled via `config.yml` or failed doesn't show up at all,
   never as "disabled".
2. **Permission** - the module's own existing permission node
   (`universaladmin.players.view`, `universaladmin.moderation.use`, ...),
   via `GuiButton`'s hide-by-default rule.

`MainMenuPage` only references each module through its `ModuleId` literal
(`ModuleId.core("players")`, identical to what each module class defines
for itself) - **not** through an import of the concrete
`dev.universaladmin.modules.*` class. That keeps the GUI framework package
generic and not dependent "upward" on individual built-in modules, see
the "Package Rules" section of the
[development rules](architecture-rules.md).

Every button today opens a `PlaceholderGuiPage` ("not built yet") under a
stable `GuiPageId` (`core:<module>.home`, e.g. `core:players.home`). A
module that later gets a real GUI registers its own page under the same
`GuiPageId` - `GuiRegistry` allows exactly one owner per id, so the
placeholder registration in `UniversalAdminPlugin#registerMainMenu` then
has to be removed for that module.

## The `/admin` Command

- `/admin` (no argument): opens `MainMenuPage` for a player (permission
  `universaladmin.menu.open`, otherwise `error.no-permission`). For
  console/command blocks (can't see an inventory), the previous text
  status report remains.
- `/admin reload`: unchanged, entirely separate from the menu - see
  [docs/user/configuration.md#reload](../user/configuration.md#reload).

## Extension Readiness

Like the rest of the platform (see
[docs/architecture/decisions/0005-extension-ready-design.md](../architecture/decisions/0005-extension-ready-design.md)),
this framework is built so a future external extension could use the same
building blocks as a built-in module - `AbstractGuiPage`, `GuiButton`,
`GuiFramework` aren't coupled to `dev.universaladmin.modules.*`. But there
is **no public, versioned API boundary yet** (no `universaladmin-api`
module, see ROADMAP.md Phase 4) - so this package can still change without
a compatibility promise.

## A Module Builds a Page: Example

Suppose `PlayersModule` got a real "player list" page (not yet part of
this task, but the pattern is already usable). The service already exists
(`PlayerService`, see
[docs/development/adding-module.md](adding-module.md)):

```java
package dev.universaladmin.modules.players.gui;

public final class PlayerListPage extends AbstractListGuiPage<PlayerProfile> {

    public static final GuiPageId ID = GuiPageId.core("players.home");

    private final PlayerService playerService;

    public PlayerListPage(
            GuiFramework framework, MessageService messages, TaskScheduler scheduler, PlayerService playerService) {
        super(ID, framework, messages, scheduler);
        this.playerService = playerService;
    }

    @Override
    protected Component title(Player viewer) {
        return text("players.gui.title"); // a new lang key, no hardcoded string
    }

    @Override
    protected CompletableFuture<List<PlayerProfile>> loadItems(Player viewer) {
        return playerService.allProfiles(); // service, no repository/SQL knowledge here
    }

    @Override
    protected GuiItem render(PlayerProfile profile) {
        return GuiItem.of(Material.PLAYER_HEAD, Component.text(profile.lastKnownName()));
    }

    @Override
    protected void onSelect(GuiClickContext ctx, PlayerProfile profile) {
        ctx.open(new PlayerDetailPage(framework, messages, profile)); // forward navigation
    }
}
```

Registration in `PlayersModule#onEnable` simply replaces the previous
`PlaceholderGuiPage` registration for `core:players.home`:

```java
context.platform().guiPages().register(
        new PlayerListPage(context.platform().guiFramework(), context.platform().messages(),
                context.platform().scheduler(), playerService));
```

No `InventoryClickListener`, no pagination code, no manual main-thread
hopping - `AbstractListGuiPage` provides all of that. The click handler
(`onSelect`) only calls navigation and (indirectly, through the service)
domain logic, never SQL or computation directly - see "The One
Architecture Rule Still Holds" above.

## Added in 1.9

- **`GuiRows`** (API 1.8): `GuiRows.place(view, viewer, row, column, glass, label, buttons)`
  draws a label pane and the buttons after it and returns those that did
  not fit; `GuiButton.display(item)` puts a value among buttons. See the
  colour meanings in [ADR 0027](../architecture/decisions/0027-menu-design-system.md).
- **Live pages** (API 1.8): a page whose `live()` returns true is drawn
  again once a second while it is open. It must draw synchronously - a
  page that loads asynchronously would flash its loading placeholder.
- **`GameNames`** (core): game modes, difficulties, effects and entity
  types as translatable components, `GameNames.arg(...)` for a message
  argument (`<lang:...>`), `GameNames.clock(ticks)` for the time of day.

## Added in 1.8

- **The frame** (see "Slots"): `AbstractGuiPage` draws the header in the
  module's colour and the main menu button; override `accent()` or
  `header(viewer)` for a page of its own colour or icon.
- **The window stays open** between pages of the same size: `open` redraws
  into the `GuiWindow` the viewer has open and changes its title. A late
  async render checks `GuiView#isOpenFor(viewer)`, which is only true for
  the view that is current in that window.
- **`ModuleDescriptor#menuGroup`/`#color`** (API 1.7): the main menu
  groups modules (`MenuGroup`) and colours each page's header.
- **`StatusToggle`** (`ModuleContext#registerStatusToggle`, API 1.7): a
  switch in the main menu's "My status" row and, while on, in the status
  line in the action bar. `ActionStatusToggle` runs an action per click.
  Before sending your own action bar, call
  `StatusToggleRegistry#pauseStatusLine` so it stays readable.
- **`PlayerMenuEntry#group()`** (API 1.7): the row of the player's profile
  a button goes in - `MODERATE`, `VIEW`, `HELP` or `MORE` (the default).
- **`GuiClickContext#page()`** (API 1.7): the page a click happened on, to
  come back to it after a dialog.
- **`ConfirmationDialog`** has a frame in its danger level's colour and
  clickable panes either side of "confirm" and "cancel".

## Added in 1.5

- **`GuiForm`** (API 1.4) - see "Forms". The punish wizard, adding to the
  whitelist, title broadcasts, enabling maintenance, the world border and
  the player values use it.

## Added in 1.0

- **Main menu** entries come from `ModuleDescriptor#menu(homePage,
  permission)` and `#menuOrder` - the menu lists every enabled module with a
  registered home page; names come from `menu.<namespace>.<module>.*` keys
  when present.
- **Profile buttons** from any module or extension: `PlayerMenuEntry` +
  `PlayerMenuRegistry` (`ModuleContext#registerPlayerMenuEntry`).
- **`AbstractListGuiPage#renderControls`** places filter/add buttons in the
  free top-row or bottom-row slots.
- **`GuiActions.run(...)`** executes an action from a click and reports a
  failure to the viewer.
- **`ConfirmationDialog`** returns to the page it was opened from and is
  single-use; every button ignores a second click within 150 ms.

