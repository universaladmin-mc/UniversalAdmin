# Storage

## Repository Pattern

Every bit of persistence goes through a `Repository<T, ID>`
([`src/main/java/dev/universaladmin/storage/Repository.java`](../../src/main/java/dev/universaladmin/storage/Repository.java)):

```java
public interface Repository<T, ID> {
    CompletableFuture<Optional<T>> findById(ID id);
    CompletableFuture<List<T>> findAll();
    CompletableFuture<T> save(T entity);
    CompletableFuture<Void> deleteById(ID id);
}
```

Services and actions only know this interface (or a module-specific
extension of it, e.g. `AuditEventRepository.recent(int)`). SQL,
`Connection`, `PreparedStatement` exist exclusively in `*Repository`
implementations, usually in a `jdbc` subpackage next to the interface
(`storage.jdbc`, `audit.jdbc`, `modules.players.jdbc`).

Every method is async (`CompletableFuture`) - see
[threading.md](threading.md) for why.

## Database Configuration

The default is SQLite (`plugins/UniversalAdmin/data.db`), MySQL/MariaDB can
be enabled via `config.yml`:

```yaml
database:
  type: sqlite   # or: mysql
  file: data.db
  host: localhost
  port: 3306
  database: universaladmin
  username: universaladmin
  password: ""
  ssl: true
  pool-size: 10
```

`CoreSettings.readDatabaseConfig(settingsService)` reads the individual
typed `database.*` settings (see
[docs/development/settings.md](../development/settings.md)) and builds a
`DatabaseConfig` record from them (see
[`src/main/java/dev/universaladmin/storage/DatabaseConfig.java`](../../src/main/java/dev/universaladmin/storage/DatabaseConfig.java)).
`storage.jdbc.DataSourceFactory` is the only place that builds a pooled
`javax.sql.DataSource` (HikariCP) from it - SQLite gets a pool of size 1
(SQLite has no real concurrent write concurrency), MySQL/MariaDB gets the
configured pool size. The MariaDB JDBC driver is also used for "real" MySQL
- it speaks the MySQL protocol, and a second driver just for MySQL would be
an additional dependency without benefit. User documentation (SQLite vs.
MySQL, when which makes sense) is in
[docs/user/database.md](../user/database.md).

`StorageService` is this system's "database manager" - a name the code
deliberately doesn't carry, to avoid introducing a second abstraction next
to `DataSourceFactory`/`MigrationRunner` doing the same thing.

### HikariCP as the Connection Pool

HikariCP is the only connection-pool library in the project (see
[decisions/0003-repository-storage.md](decisions/0003-repository-storage.md))
- no pool implementation of our own. For SQLite the pool is deliberately
limited to size 1 (see above); for MySQL/MariaDB it uses the configured
`database.pool-size`. HikariCP also resets the state of every connection
(autocommit, read-only, catalog) before returning it to the pool -
`Transactions` (see below) deliberately relies on that instead of
restoring `setAutoCommit(true)` itself.

### SQLite Pragmas

`DataSourceFactory` sets four pragmas for SQLite via the JDBC URL
(`?journal_mode=WAL&synchronous=NORMAL&foreign_keys=on&busy_timeout=5000`):

- `journal_mode=WAL` - lets future readers proceed without waiting on the
  single writer, instead of the default rollback journal. Creates two
  additional files next to the `.db` file (`-wal`, `-shm`).
- `synchronous=NORMAL` - the documented safe combination for WAL (fsync at
  checkpoints, not on every commit).
- `foreign_keys=on` - SQLite doesn't enforce foreign keys per connection by
  default; explicitly enabled so `REFERENCES` constraints in migrations
  actually take effect.
- `busy_timeout=5000` - avoids an immediate "database is locked" if
  something outside the pool (e.g. a backup tool) briefly locks the file.

No automatic backup: see [docs/user/database.md](../user/database.md) for
the deliberate decision not to build a dedicated feature for that.

## Dialect Differences

SQLite and MySQL/MariaDB aren't syntactically compatible, in particular:

- Auto-increment: SQLite `INTEGER PRIMARY KEY AUTOINCREMENT` vs. MySQL
  `BIGINT AUTO_INCREMENT PRIMARY KEY`.
- Upsert: SQLite `INSERT ... ON CONFLICT (id) DO UPDATE SET ...` vs. MySQL
  `INSERT ... ON DUPLICATE KEY UPDATE ...`.
- `CREATE INDEX IF NOT EXISTS`: SQLite and MariaDB accept it, real MySQL
  (not MariaDB) doesn't - `CREATE INDEX` there has no `IF NOT EXISTS`
  (unlike `CREATE TABLE IF NOT EXISTS`, which works everywhere). Doesn't
  need its own check anyway: `MigrationRunner` already guarantees every
  migration runs at most once - an index-creating
  `Statement#execute(...)` simply drops the `IF NOT EXISTS`, see
  `PlayerProfileIndexMigration`/`ModerationPunishmentIndexMigration`.

Every migration/repository affected by this checks
`connection.getMetaData().getDatabaseProductName()` and picks the matching
SQL variant - see `AuditSchemaMigration` and `JdbcPlayerProfileRepository`
as examples. There is deliberately no SQL abstraction layer (no
JPA/Hibernate) on top; at the current number of tables that's more
overhead than benefit, see
[decisions/0003-repository-storage.md](decisions/0003-repository-storage.md).

## Migrations

`Migration` is a forward-only schema change:

```java
public interface Migration {
    int version();
    String description();
    void migrate(Connection connection) throws SQLException;
}
```

`MigrationRunner` runs every registered migration in version order and
tracks progress in a `schema_version` table. It applies every migration
whose version is not recorded there yet - not only those above the highest
applied one - so a migration added in a later release with a lower number
(e.g. a core migration after module migrations already ran) still runs. On
MySQL/MariaDB the whole run holds a named database lock
(`GET_LOCK('universaladmin_migrations')`), so servers of a network that
start at the same time against one database migrate one after another. Versions are globally unique
across the whole database, not per module:

- **1-999**: core migrations (e.g. `AuditSchemaMigration`, version 1),
  registered directly in `UniversalAdminPlugin`.
- **1000+**: built-in module migrations, registered by the respective
  module in `onLoad` (e.g. `PlayerProfileMigration`, version 1000).
- **Extensions** (since API 1.8) return their own name from
  `Migration#namespace()` and number their migrations from 1. They are
  tracked in `ua_extension_migrations (namespace, version)`, apart from
  UniversalAdmin's numbering and every other extension's, so two extensions
  can never take each other's numbers. An extension written before 1.8
  (empty namespace) shares UniversalAdmin's numbering in `schema_version` as
  before and picks a range far away from the built-ins, such as 50 000-50 999.
  A collision (same namespace and version, different migration) is refused
  at startup. Migrations of the shared numbering run first, so an
  extension's migrations can build on UniversalAdmin's tables.

Used versions: 1-2 audit, 3 network tables (`ua_network_servers`,
`ua_network_players`, `ua_network_messages`), 4 `ua_network_players.vanished`, 1000-1001 players, 1002-1003
punishments, 1004 vanish state, 1005 staff-mode snapshots, 1006
maintenance state, 1007 whitelist, 1008 `ua_player_notes`, 1009
`ua_reports`, 1010 `ua_reports.server`, 1011 `ua_import_records` (see
[ADR 0015](decisions/0015-punishment-import.md)), 1012 `ua_maintenance` (see
[ADR 0017](decisions/0017-maintenance-scopes.md)), 1013 `punishments.template` (see
[ADR 0018](decisions/0018-punishment-templates.md)), 1014 `ua_whitelist` and
`ua_whitelist_state` (see [ADR 0019](decisions/0019-network-whitelist-and-vanish.md)), 1015
`ua_player_addresses` and `ua_alt_key` (see [ADR 0020](decisions/0020-alt-detection.md)), 1016
`punishments.mute_kind` (see [ADR 0022](decisions/0022-mute-kinds-and-voice-chat.md)), 1017
`ua_nicks` (see [ADR 0025](decisions/0025-nick-system.md)).

Migrations run at plugin start (`storage.migrations().runPending()` in
`UniversalAdminPlugin#onEnable`, between every module's `onLoad` and
`onEnable`), and once more for each extension module when its plugin
registers it - see [threading.md](threading.md) for the exception to the
"no blocking on the main thread" principle this represents.

## Table names

New tables carry a prefix - `ua_` for UniversalAdmin itself, the
extension's own for extensions - so they never collide with another
plugin's tables in a shared MySQL database. The tables from before 1.0
(`punishments`, `player_profiles`, `whitelist_entries`, ...) keep their
names; renaming them would risk data for no benefit.

## Adding a New Migration

1. Write a `Migration` implementation in the module's package, versioned
   after the last value used by this module (see existing migrations for
   the current highest version).
2. Register it in `Module#onLoad`: `context.registerMigration(...)`.
3. A migration is forward-only - never modify an already-released
   migration after the fact; add a new one with a higher version instead.

## Health

`StorageService` tracks a `DatabaseHealth` (`DISCONNECTED`, `CONNECTING`,
`READY`, `FAILED`):

```java
public enum DatabaseHealth {
    DISCONNECTED, CONNECTING, READY, FAILED
}
```

The constructor sets `CONNECTING`, builds the pool via
`DataSourceFactory`, and validates a connection (`Connection#isValid`);
success sets `READY`, any error sets `FAILED` and rethrows the exception.
`close()` sets `DISCONNECTED`. `UniversalAdmin#status()` maps that onto the
coarser `ComponentStatus` (`READY→ONLINE`, `CONNECTING→DEGRADED`,
`FAILED`/`DISCONNECTED→OFFLINE`) that `/admin` displays.

**This is a start/stop snapshot, not a running live check** - a remote
database going down mid-operation (e.g. the MySQL server crashes) doesn't
retroactively change this value. A periodic health check would be
possible, but is deliberately deferred: it would need a recurring task
(`TaskScheduler` doesn't offer one today, see [threading.md](threading.md))
and the practical benefit without an auto-reconnect or auto-restart
strategy behind it is limited.

**Decision: a complete DB failure at startup disables the whole plugin, no
degraded operation.** `StorageService` is a *critical* bootstrap component
(see
[modules.md](modules.md#failure-isolation-whats-critical-whats-isnt)) - if
the constructor throws, `UniversalAdminPlugin#bootstrapCore` aborts and the
plugin disables itself before any module loads. There's deliberately no
"storage-less" mode: every built-in module assumes a working database, and
a plugin that appears to start normally but can't persist anything is
worse failure behavior than a plugin that doesn't start at all.

## Transactions

For repository methods that need to run multiple statements atomically
(e.g. two tables for one logical change), there's
`dev.universaladmin.storage.Transactions`:

```java
Transactions.run(dataSource, scheduler, connection -> {
    // multiple PreparedStatements over the same connection
    return result;
});
```

Runs like any other JDBC call through the given `TaskScheduler` (never the
main thread, see [threading.md](threading.md)), sets `autoCommit=false`,
commits on success, fully rolls back on any exception and rethrows it
(unchecked exceptions unchanged, `SQLException` wrapped in
`StorageException`). A repository method with only one statement doesn't
need this - a single statement is already atomic on its own.
