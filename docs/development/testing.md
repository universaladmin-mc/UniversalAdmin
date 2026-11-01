# Testing

## Principle

Business logic (services, actions, non-trivial migrations) must be
testable without a running Paper server. That's the real test of whether
the layering from [ARCHITECTURE.md](../../ARCHITECTURE.md) was actually
followed - if a service can't be tested without Bukkit mocking, business
logic probably ended up somewhere that should have stayed frontend/adapter.

## Tools

- JUnit 5 (Jupiter) - test framework.
- Mockito - available (`testImplementation`), but not the first choice:
  see below.
- No Paper server mocking framework (MockBukkit or similar) set up
  currently. Becomes relevant once `GuiPage` implementations or Bukkit
  event listeners with meaningful logic of their own show up (see
  [ROADMAP.md](../../ROADMAP.md) Phase 1) - don't add it prematurely
  before then.

## Repositories: Fakes Instead of Mocks

A service depends on a `Repository` *interface* - that can be faked with a
simple in-memory implementation instead of mocking every method with
Mockito. A fake behaves like a real implementation (consistent state
across multiple calls), a mock only responds to what was explicitly
programmed - for repository tests, a fake is usually the test that
actually checks service behavior instead of just the call sequence.

Example:
[`PlayerServiceTest`](../../src/test/java/dev/universaladmin/modules/players/PlayerServiceTest.java)
- a `record` implementing `PlayerProfileRepository` against a
`ConcurrentHashMap`, directly in the test class.

Mockito is the right choice when a dependency needs to simulate *behavior*
a simple fake can't reasonably represent (e.g. a failure case hard to force
through real state).

## Migrations: a Real SQLite Database, No Mock

Test `Migration`/`MigrationRunner` against a real, temporary SQLite file
(`@TempDir` + `DataSourceFactory`), not against a mocked `Connection` - SQL
syntax errors only show up against a real database. Example:
[`MigrationRunnerTest`](../../src/test/java/dev/universaladmin/storage/MigrationRunnerTest.java).

**Important on Windows:** close the `DataSource` (HikariCP) created by
`DataSourceFactory.create(...)` at the end of the test
(`((AutoCloseable) dataSource).close()`), or JUnit's `@TempDir` can't
delete the file after the test because SQLite is still holding it open.

## Settings/Config: a Real `YamlConfiguration`, No Mock

Same as for migrations: test `YamlSettingsService` against a real
(in-memory) `org.bukkit.configuration.file.YamlConfiguration`
(`config.loadFromString("gui:\n  page-size: 27\n")`), not against a mocked
`FileConfiguration` - `YamlConfiguration` is a plain data-structure class
in `paper-api`, no server needed. For a reload between two values, an
`AtomicReference<YamlConfiguration>` read by the `Supplier<FileConfiguration>`
is enough. Example:
[`YamlSettingsServiceTest`](../../src/test/java/dev/universaladmin/settings/YamlSettingsServiceTest.java).

Test `YamlLocaleMessageService` the same way, against real `lang/*.yml`
files written into a `@TempDir`, not against a mocked message map - see
[`YamlLocaleMessageServiceTest`](../../src/test/java/dev/universaladmin/localization/YamlLocaleMessageServiceTest.java).
`SettingsService` itself (only for `general.language`) is fine to mock
here, because this test isn't checking the settings system, only the
locale fallback logic.

## Binary Compatibility: Every Supported Paper Version

The jar is compiled against one Paper API but runs on every version from
1.21.7 to the newest. Compiling against the others (the CI does) is not
enough: a method whose signature differs - e.g. between Adventure 4 on Paper
1.21 and Adventure 5 on Paper 26 - compiles and fails with
`NoSuchMethodError` only on the other version. `./gradlew binaryCompatibility`
(part of `check`, so of every `build`) resolves every class, method and field
the plugin's own classes use in the server's API against Paper 1.21.7,
1.21.11 and the version compiled against
(`gradle/binary-check/BinCheck.java`, Java 25). The CI runs it once more
with the jar built against the newest Paper version.

## What's Still Missing

- Tests for the eight module skeletons follow once they get real logic
  beyond `PlayersModule`/`PlayerService` (see
  [adding-module.md](adding-module.md)).
- Permission tests so far only at the validation level
  (`PermissionNodeTest`) - tests for actual authorization *decisions*
  follow with the first action that makes one.
- No integration test running `UniversalAdminPlugin#onEnable` end to end
  (would need a Paper test server) - deliberately deferred until there's a
  concrete need for it.
