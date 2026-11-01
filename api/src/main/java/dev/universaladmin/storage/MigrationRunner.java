package dev.universaladmin.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Applies pending {@link Migration}s in version order and tracks what has
 * already run in a {@code schema_version} table.
 *
 * <p>Modules call {@link #register} during {@code onEnable}; nothing runs
 * until {@link #runPending()} is called once by {@link StorageService}
 * during plugin startup, after every module has had a chance to register its
 * migrations.
 */
public final class MigrationRunner {

    private static final String LOCK_NAME = "universaladmin_migrations";
    private static final int LOCK_TIMEOUT_SECONDS = 120;

    private final DataSource dataSource;
    private final Logger logger;
    private final List<Migration> migrations = new ArrayList<>();

    public MigrationRunner(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    /**
     * Registers a migration. Registering the same migration class again (an
     * extension whose plugin was reloaded) is a no-op.
     *
     * @throws IllegalStateException if a <i>different</i> migration already uses the same version in the
     *                               same {@link Migration#namespace() namespace}
     */
    public synchronized void register(Migration migration) {
        for (Migration existing : migrations) {
            if (existing.version() == migration.version() && existing.namespace().equals(migration.namespace())) {
                if (existing.getClass().equals(migration.getClass())) {
                    return;
                }
                throw new IllegalStateException("Migration version " + migration.version()
                        + (migration.namespace().isEmpty() ? "" : " of " + migration.namespace()) + " is used by both "
                        + existing.getClass().getName() + " and " + migration.getClass().getName());
            }
        }
        migrations.add(migration);
    }

    /**
     * Runs every registered migration that has not been applied yet, in
     * version order. Blocking JDBC work - must be called from a background
     * thread, never the Paper main thread. See docs/architecture/threading.md.
     *
     * <p>"Not applied yet" means: its version has no row in
     * {@code schema_version} - not "newer than the highest version applied".
     * A migration with a lower number can be added later (a new core
     * migration after the module migrations 1000+ ran, or an extension
     * installed afterwards) and must still run.
     *
     * <p>On MySQL/MariaDB several servers of a network may start at the same
     * time against one database; a named database lock makes them run the
     * migrations one after the other, so each one sees what the previous one
     * applied.
     */
    public void runPending() {
        // The shared numbering (UniversalAdmin's own tables) first - an
        // extension's migrations may build on them.
        List<Migration> ordered = new ArrayList<>(migrations);
        ordered.sort(Comparator.comparing((Migration migration) -> !migration.namespace().isEmpty())
                .thenComparing(Migration::namespace)
                .thenComparingInt(Migration::version));

        try (Connection connection = dataSource.getConnection()) {
            boolean locked = lock(connection);
            try {
                ensureSchemaVersionTable(connection);
                Set<String> applied = appliedVersions(connection);
                for (Migration migration : ordered) {
                    if (applied.contains(id(migration.namespace(), migration.version()))) {
                        continue;
                    }
                    logger.info(() -> "Applying migration " + (migration.namespace().isEmpty() ? "" : migration.namespace() + ":")
                            + migration.version() + ": " + migration.description());
                    migration.migrate(connection);
                    recordVersion(connection, migration);
                }
            } finally {
                if (locked) {
                    unlock(connection);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to run database migrations", e);
        }
    }

    /** MySQL/MariaDB only - SQLite is one file for one server. */
    private boolean lock(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (!product.contains("mysql") && !product.contains("mariadb")) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, LOCK_NAME);
            statement.setInt(2, LOCK_TIMEOUT_SECONDS);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new SQLException("Another server held the migration lock for more than " + LOCK_TIMEOUT_SECONDS + "s");
                }
            }
        }
        return true;
    }

    private void unlock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, LOCK_NAME);
            statement.executeQuery().close();
        } catch (SQLException e) {
            // The lock also ends with the connection.
            logger.log(Level.FINE, "Could not release the migration lock", e);
        }
    }

    private void ensureSchemaVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER PRIMARY KEY,
                        description VARCHAR(255) NOT NULL,
                        applied_at BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ua_extension_migrations (
                        namespace VARCHAR(64) NOT NULL,
                        version INTEGER NOT NULL,
                        description VARCHAR(255) NOT NULL,
                        applied_at BIGINT NOT NULL,
                        PRIMARY KEY (namespace, version)
                    )
                    """);
        }
    }

    private static String id(String namespace, int version) {
        return namespace + ":" + version;
    }

    private Set<String> appliedVersions(Connection connection) throws SQLException {
        Set<String> applied = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version")) {
            while (resultSet.next()) {
                applied.add(id("", resultSet.getInt(1)));
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT namespace, version FROM ua_extension_migrations")) {
            while (resultSet.next()) {
                applied.add(id(resultSet.getString(1), resultSet.getInt(2)));
            }
        }
        return applied;
    }

    private void recordVersion(Connection connection, Migration migration) throws SQLException {
        if (migration.namespace().isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO schema_version (version, description, applied_at) VALUES (?, ?, ?)")) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.description());
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ua_extension_migrations (namespace, version, description, applied_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, migration.namespace());
            statement.setInt(2, migration.version());
            statement.setString(3, migration.description());
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }
}
