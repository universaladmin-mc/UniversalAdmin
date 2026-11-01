# Writing an extension

An extension adds features to UniversalAdmin - its own `/admin` commands,
GUI pages with a main-menu button, buttons on player profiles, audited
actions, settings that show up in the settings GUI, database tables. It is a
normal Paper plugin; UniversalAdmin does the wiring.

The fastest start is to copy
[`examples/example-extension`](../../examples/example-extension) - every
feature below is used there, in about 250 lines.

## 1. Build setup

Java 21 bytecode, Paper 1.21.7 or newer - like UniversalAdmin itself, so
your extension runs on every version UniversalAdmin supports (compile with
Java 21 or `options.release.set(21)`). Add the API as a `compileOnly`
dependency (it is provided at runtime by the UniversalAdmin plugin jar):

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    compileOnly("com.github.universaladmin-mc.UniversalAdmin:universaladmin-api:v1.5.0")
}
```

Alternatives if you prefer:

- the plugin jar from Modrinth's Maven:
  `maven("https://api.modrinth.com/maven")` +
  `compileOnly("maven.modrinth:universaladmin:1.5.0")`,
- or `universaladmin-api-1.5.0.jar` (with sources and javadoc) from the
  [GitHub release](https://github.com/universaladmin-mc/UniversalAdmin/releases),
  added via `compileOnly(files("libs/universaladmin-api-1.5.0.jar"))`.

Never shade the API into your jar.

## 2. plugin.yml

```yaml
name: MyExtension
version: '1.0.0'
main: com.example.myextension.MyExtensionPlugin
api-version: '1.21.7'
depend: [UniversalAdmin]
```

`depend` makes Paper enable UniversalAdmin first.

## 3. Register your module

```java
public final class MyExtensionPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        UniversalAdmin ua = UniversalAdminProvider.find().orElse(null);
        if (ua == null) {               // UniversalAdmin failed to start
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        MessageBundles.register(this, ua.messages(), "en_US", "de_DE");
        ua.extensions().register(this, new TicketsModule());
    }
}
```

When your plugin is disabled, UniversalAdmin disables your module and
removes everything it registered.

## 4. The module

```java
public final class TicketsModule implements Module {

    static final ModuleId ID = ModuleId.of("tickets", "tickets");

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder(ID, "Tickets")
                .description("Support tickets")
                .icon(new GuiIcon("writable_book", "Tickets"))
                .menu(TicketsPage.ID, TicketPermissions.VIEW)   // main-menu button
                .build();
    }

    @Override
    public void onLoad(ModuleContext context) {
        context.registerMigration(new TicketsMigration());     // tables: onLoad only
    }

    @Override
    public void onEnable(ModuleContext context) {
        context.registerPermission(...);
        context.registerSetting(...);
        context.registerAction(ActionDefinition.builder(new CloseTicketAction(...))
                .permission(TicketPermissions.CLOSE)
                .module("tickets")
                .auditSummary(in -> "Closed ticket #" + in.id())
                .build());
        context.registerCommand(new TicketsCommand());
        context.registerPage(new TicketsPage(context.platform()));
        context.registerPlayerMenuEntry(new TicketsProfileButton());
    }
}
```

## 5. The rules that matter

- **Logic lives in actions.** A command or GUI click parses input and calls
  an action (`CommandInvocation#run`, `GuiActions.run`); the action does the
  work. That gives you permission checks, validation and audit for free,
  and makes the same logic usable from every frontend.
- **Say what happened.** Return `ActionResult.success(value, key, args...)`
  with a precise message ("Ticket #12 closed", not "Done.") - then
  `CommandInvocation#run(id, input)` and `GuiActions.runAndReport(...)`
  show exactly that text. See the `GreetAction` in the example extension
  and [actions.md](../architecture/actions.md#success-messages---the-action-says-what-it-did).
- **Never block the main thread.** Database work goes through
  `TaskScheduler#supplyAsync`; Bukkit API calls back through
  `runOnMainThread`/`supplyOnMainThread`. `JdbcPlayerNoteRepository` in the
  core is a compact example of an async repository.
- **No hard-coded texts.** Use `MessageKey`s with your own prefix
  (`tickets.closed`) and ship `lang/en_US.yml` in your jar. String arguments
  are escaped automatically, so player input can never inject formatting.
- **Own namespace everywhere:** module/action/page ids (`tickets:...`),
  permission nodes (`tickets.close`), lang keys, table names
  (`tickets_...`).

## 6. Useful hooks

- `context.subscribeAudit(event -> ...)` - every recorded admin action, from
  every module, in one uniform shape (`AuditEvent`). The Discord extension is
  built on this.
- `context.subscribe(event -> ...)` - raw `ActionEvent`s (executing,
  executed, failed) including the input object.
- `context.platform().services().get(SomeService.class)` - services other
  modules published, e.g. the Reports module's `ReportService`.
- `ua.network()` - the other servers of a Velocity/BungeeCord network
  (`servers()`, `players()`, `player(name)`), messages between servers
  (`publish(topic, payload)` / `subscribe(topic, listener)`) and moving
  players (`connect`, `sendTo`). Without network mode it is a harmless
  no-op (`enabled()` is false), so you never need to branch on it.
- `PlayerDirectory` (a service, `services().get(PlayerDirectory.class)`) -
  find any player who ever joined, on this server or another server of the
  network, by name or UUID. In commands, `CommandInvocation#withPlayer(index,
  player -> ...)` does that lookup for an argument and reports "unknown
  player" for you.
- Outside the game (1.2): `services().get(RemoteCommands.class)` runs any
  `/admin` command for an `Actor.remote(name, permissions)` - with the same
  checks and audit entries as in game; `StaffChat` sends into and listens
  to the staff chat. The Discord bot is built on both.
- Status for displays (1.2): `services().get(PlayerStatus.class)` (vanished,
  staff mode, frozen, muted, warnings, punishment count), `ReportOverview`
  (open reports), `MaintenanceStatus` - all answered from memory, safe on
  any thread. The PlaceholderAPI extension is built on them.
- Mute kinds (1.4): `PlayerStatus#chatMute`/`#voiceMute` and
  `Mute#kind()` (`MuteKind.CHAT`/`VOICE`/`FULL`). An extension that
  connects a voice chat registers a `dev.universaladmin.voice.VoiceChat`
  service - then the core offers chat- and voice-only mutes, and the
  extension enforces the voice part. UniversalAdmin Voice Chat is built on
  this ([voicechat.md](voicechat.md)).
- Updates (1.5): `ua.extensions().updatesFrom(this, "owner/repository",
  "myextension-")` lets UniversalAdmin's update check cover your plugin -
  staff hear about a new release, it is downloaded on its own within the
  same major version, and `/admin update` includes it. Publish your jar as a
  release asset named `myextension-<version>.jar` (tag `v<version>`), with
  a `.sha256` next to it if you can.
- Forms (1.4): `GuiForm` asks for several values in one Paper dialog -
  text fields, sliders, checkboxes, option lists; see
  [gui-framework.md](../development/gui-framework.md#forms).
- Menus (1.7): your module's colour and main menu group
  (`ModuleDescriptor#color`/`#menuGroup`), a switch in "My status" and the
  status line (`ModuleContext#registerStatusToggle`), and the row of the
  player's profile your button goes in (`PlayerMenuEntry#group()`); see
  [gui-framework.md](../development/gui-framework.md#added-in-18).
- Hub pages and the audit log (1.8): `GuiRows` and `GuiButton#display` for
  rows behind coloured label panes, `GuiPage#live()` for a page that shows
  live values, `ActionDefinition.Builder#auditArgs` plus
  `audit.entry.<namespace>.<action>.title`/`.text` texts for audit entries in
  the server's language, `Migration#namespace()` to number your own
  migrations from 1, `MessageService#get(locale, ...)` for a message in one
  player's language.
- `UniversalAdminProvider.API_VERSION` - check before relying on API added
  in a later minor version (`network()`, `PlayerDirectory` and
  `withPlayer` are 1.1; the status services and
  `PlayerDirectory#addIfAbsent` are 1.2; `NetworkService#vanished` is 1.3;
  mute kinds, `VoiceChat` and `GuiForm` are 1.4 - UniversalAdmin 1.5;
  `updatesFrom` is 1.5 - UniversalAdmin 1.6; `Nicks` and `PlayerNames` are
  1.6 - UniversalAdmin 1.7; the menu additions are 1.7 - UniversalAdmin 1.8;
  hub rows, live pages, audit values and migration namespaces are 1.8 -
  UniversalAdmin 1.9).

## Reference

- [docs/architecture/extensions.md](../architecture/extensions.md) - every
  extension point at a glance
- [docs/architecture/actions.md](../architecture/actions.md) - the action
  pipeline
- [docs/development/gui-framework.md](../development/gui-framework.md) - the
  GUI framework
- [docs/development/settings.md](../development/settings.md) - typed
  settings
- Javadoc: `universaladmin-api-<version>-javadoc.jar` on every GitHub release
