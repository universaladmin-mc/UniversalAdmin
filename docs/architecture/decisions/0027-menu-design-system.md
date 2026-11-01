# 0027 - One Look for Every Menu: Frame, Glass Colours, Hubs

## Status

Accepted (1.8.0). Builds on [ADR 0004](0004-gui-service-separation.md) and
[ADR 0010](0010-extension-api.md).

## Context

Up to 1.7 every page drew its own layout into a shared six-row grid: the
back, refresh and close buttons sat in fixed slots, everything else was
placed in order, and the rest stayed grey. That kept the code simple but
the menus hard to read:

- Nothing told you which module you were in, or what belonged together.
  A player's profile was a block of 17 identical paper items, followed by
  buttons in a row.
- Every page switch closed the inventory and opened a new one - the screen
  flickered and the mouse jumped back to the middle.
- Staff had no place to see what they had switched on (vanish, staff mode,
  godmode, a nick) short of opening each module's page.
- A frozen player only got a chat line, which scrolls away; staff saw
  nothing of a freeze at all.

## Decision

**A frame around every page.** `AbstractGuiPage` draws the top row in the
module's colour (`ModuleDescriptor#color`, a `GuiGlass`): back (0), main
menu (1), the page's icon and title (4), refresh (7), close (8). The bottom
row holds the page's tools (45-47, 51-53) and paging (48-50). Every slot
nothing was placed in becomes a black pane without a tooltip.

**Glass colours keep one meaning everywhere** (`GuiGlass`): black is frame
and empty space, light grey divides sections, the module colour shows where
you are, green means on/confirm, red off/danger/cancel, orange a warning,
white "nothing here" (with text). A dialog's frame takes its danger level:
the confirmation for a ban is red, for a kick orange.

**The window stays open.** A page switch into a window of the same size
clears and redraws it and changes its title (`InventoryView#setTitle`) - no
close, no flicker, the mouse stays. `GuiWindow` is the inventory holder and
knows which `GuiView` is current, so a late asynchronous render of a page
that was left again writes nothing. Pages with an editable inventory (the
live inventory editors) still get a window of their own, since their close
handling needs a real close.

**Hubs instead of lists of buttons.**

- The main menu is a dashboard: status tiles (players, reports, TPS,
  maintenance, network, "me"), the modules in three groups (`MenuGroup`:
  players, server, system) each behind a label pane in its colour, and a
  "My status" row of switches (`StatusToggle`).
- A player's profile shows what is known about them in a column of four
  items (who, where, playtime, condition), then one row per
  `PlayerMenuGroup` - moderate (red), view (blue), help and teleport
  (green), more (purple) - each behind its label pane, and the previous and
  next online player at the bottom. Modules and extensions put their
  buttons into a row with `PlayerMenuEntry#group()`; what does not fit into
  a row's six slots moves on to "more".
- The three player lists are tabs in the header row of each other.

**Staff status in the action bar, not on a sidebar or a bossbar.** The
status line (`gui.status-line`) shows the switches of "My status" that are
on, e.g. `Vanish · Godmode · Nick Notch`, once a second while something is
on. Anything else that uses the action bar holds it back
(`StatusToggleRegistry#pauseStatusLine`); in staff mode the target display
has the action bar.

**A freeze is visible.** The frozen player sees a title until they are
free; staff who may freeze see a marker above their head with who froze
them and for how long. The marker is a `TextDisplay` that is hidden by
default and shown per viewer (`Entity#setVisibleByDefault`,
`Player#showEntity`) - Paper API, no packets (see the
[development rules](../../development/architecture-rules.md)) - and never
saved with the world.

## Addendum (1.9.0): the remaining pages as hubs

- **Rows behind label panes** are a building block now, `GuiRows` (API 1.8):
  the label pane's colour says what the row is for - light blue shows
  information, blue leads to more to look at, green holds harmless actions,
  orange actions with an effect on players, red dangerous ones, purple
  anything else. The server, worlds, world, performance and audit entry
  pages use them like the player profile.
- **A value that can be changed is shown on its button**: the world's time,
  weather, difficulty, spawn and border show what they are now and change
  on a click - no separate field and button for the same thing.
- **Live pages** (`GuiPage#live()`, API 1.8) are drawn again once a second
  while open: the main menu, server, worlds, a world and performance. Only
  the slots whose item changed reach the client.
- **Traffic lights** on the performance page: green, yellow or red under
  TPS, MSPT and memory, measured against the staff alert thresholds.
- **Minecraft's own names** (game modes, difficulties, effects, entities)
  are translatable components - the client shows them in its language.

## Consequences

- One answer to "where is ...?" on every page, built-in or extension: an
  extension page extends `AbstractGuiPage` and gets the frame, its module's
  colour and the window reuse for free.
- API 1.7 adds `GuiGlass`, `MenuGroup`, `GuiWindow`, `GuiStyle`,
  `StatusToggle`/`StatusToggleRegistry`/`ActionStatusToggle`,
  `PlayerMenuGroup`, `PlayerMenuEntry#group()`, `GuiClickContext#page()`
  and `ModuleDescriptor#menuGroup`/`#color`. Every addition has a default,
  so extensions built against 1.6 keep working - their profile buttons land
  in "more", their module in the system group.
- The colours are a convention in the code, not a setting: a server owner
  who wants other materials can still replace icons through the
  `IconProvider`, but the meaning of a colour does not change per server.
- Rejected: a scoreboard sidebar or a bossbar for the staff status - both
  cover a part of the screen all the time and clash with other plugins that
  use them.
