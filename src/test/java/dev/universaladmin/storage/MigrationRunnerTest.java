package dev.universaladmin.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.universaladmin.storage.jdbc.DataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationRunnerTest {

    @Test
    void appliesMigrationsInOrderExactlyOnce(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("test.db"), dir);
        try {
            MigrationRunner runner = new MigrationRunner(dataSource, Logger.getLogger("test"));
            runner.register(createTableMigration());
            runner.register(insertRowMigration());

            runner.runPending();
            runner.runPending(); // must be idempotent

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM widgets")) {
                resultSet.next();
                assertEquals(1, resultSet.getInt(1));
            }
            // Each migration is recorded exactly once in schema_version, even
            // though runPending() above ran twice - proves migrations don't
            // silently re-apply on every plugin start.
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                resultSet.next();
                assertEquals(2, resultSet.getInt(1));
            }
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private Migration createTableMigration() {
        return new Migration() {
            @Override
            public int version() {
                return 1;
            }

            @Override
            public String description() {
                return "create widgets table";
            }

            @Override
            public void migrate(Connection connection) throws SQLException {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE widgets (id INTEGER PRIMARY KEY)");
                }
            }
        };
    }

    private Migration insertRowMigration() {
        return new Migration() {
            @Override
            public int version() {
                return 2;
            }

            @Override
            public String description() {
                return "seed one widget";
            }

            @Override
            public void migrate(Connection connection) throws SQLException {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("INSERT INTO widgets (id) VALUES (1)");
                }
            }
        };
    }

    @Test
    void aMigrationWithALowerVersionAddedLaterStillRuns(@TempDir Path dir) throws Exception {
        // The upgrade case: a server already applied 1 and 2 (think: module
        // migrations 1000+), then an update brings a new migration whose
        // number is lower than the highest one applied (a new core migration).
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("test.db"), dir);
        try {
            MigrationRunner first = new MigrationRunner(dataSource, Logger.getLogger("test"));
            first.register(createTableMigration());
            first.register(new Migration() {
                @Override
                public int version() {
                    return 1000;
                }

                @Override
                public String description() {
                    return "a module table";
                }

                @Override
                public void migrate(Connection connection) {
                }
            });
            first.runPending();

            MigrationRunner afterUpdate = new MigrationRunner(dataSource, Logger.getLogger("test"));
            afterUpdate.register(createTableMigration());
            afterUpdate.register(insertRowMigration());
            afterUpdate.runPending();

            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM widgets")) {
                resultSet.next();
                assertEquals(1, resultSet.getInt(1), "migration 2 must run although 1000 was applied before it existed");
            }
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @org.junit.jupiter.api.Test
    void refusesTwoDifferentMigrationsWithTheSameVersionButIgnoresARepeat() {
        MigrationRunner runner = new MigrationRunner(null, java.util.logging.Logger.getLogger("test"));
        runner.register(new VersionedMigration(4242));
        runner.register(new VersionedMigration(4242));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> runner.register(new Migration() {
            @Override
            public int version() {
                return 4242;
            }

            @Override
            public String description() {
                return "clash";
            }

            @Override
            public void migrate(java.sql.Connection connection) {
            }
        }));
    }

    @Test
    void extensionsCountInTheirOwnNamespaceAndNeverTakeEachOthersNumbers(@TempDir Path dir) throws Exception {
        DataSource dataSource = DataSourceFactory.create(DatabaseConfig.sqlite("test.db"), dir);
        try {
            java.util.List<String> ran = new java.util.ArrayList<>();
            MigrationRunner runner = new MigrationRunner(dataSource, Logger.getLogger("test"));
            runner.register(new NamespacedMigration("alpha", 1, ran));
            runner.register(new NamespacedMigration("beta", 1, ran));
            runner.register(new VersionedMigration(1));
            runner.runPending();
            assertEquals(java.util.List.of("alpha:1", "beta:1"), ran);

            // A restart, and a server that only had alpha so far gets beta later: only what is new runs.
            MigrationRunner restarted = new MigrationRunner(dataSource, Logger.getLogger("test"));
            restarted.register(new NamespacedMigration("alpha", 1, ran));
            restarted.register(new NamespacedMigration("alpha", 2, ran));
            restarted.register(new NamespacedMigration("beta", 1, ran));
            restarted.runPending();
            assertEquals(java.util.List.of("alpha:1", "beta:1", "alpha:2"), ran);
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private record NamespacedMigration(String namespace, int version, java.util.List<String> ran) implements Migration {
        @Override
        public String description() {
            return namespace + " " + version;
        }

        @Override
        public void migrate(java.sql.Connection connection) {
            ran.add(namespace + ":" + version);
        }
    }

    private record VersionedMigration(int version) implements Migration {
        @Override
        public String description() {
            return "test " + version;
        }

        @Override
        public void migrate(java.sql.Connection connection) {
        }
    }
}
