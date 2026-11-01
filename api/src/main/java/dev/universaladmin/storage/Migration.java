package dev.universaladmin.storage;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * One forward-only schema change, applied at most once. Modules register
 * their own migrations during {@link dev.universaladmin.module.Module#onEnable};
 * see docs/architecture/storage.md and docs/development/adding-module.md.
 *
 * <p>{@link #version()} must be globally unique and increasing across the
 * whole database, not per-module - migrations from every module apply
 * against the same schema and the same {@code schema_version} table.
 * Built-in modules use the 1000+version ranges documented in storage.md to
 * leave room below them for core migrations.
 */
public interface Migration {

    int version();

    /**
     * Whose numbering {@link #version()} belongs to. An extension returns its
     * own name - lowercase, e.g. its plugin name - and numbers its migrations
     * from 1; they are tracked apart from UniversalAdmin's and every other
     * extension's ({@code ua_extension_migrations}), so two extensions can
     * never take each other's numbers. The empty default is the numbering
     * shared with UniversalAdmin itself ({@code schema_version}), which is
     * what migrations written before API 1.8 use.
     *
     * @since 1.8
     */
    default String namespace() {
        return "";
    }

    String description();

    void migrate(Connection connection) throws SQLException;
}
