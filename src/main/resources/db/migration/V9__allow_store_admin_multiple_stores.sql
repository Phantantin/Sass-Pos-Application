-- A store administrator may own more than one store. Keep a non-unique index
-- in place before dropping the unique index because the foreign key on
-- store_admin_id must remain indexed throughout the migration.
SET @create_store_admin_index = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'store'
          AND INDEX_NAME = 'idx_store_admin_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_store_admin_created ON store (store_admin_id, created_at)'
);
PREPARE create_store_admin_index_stmt FROM @create_store_admin_index;
EXECUTE create_store_admin_index_stmt;
DEALLOCATE PREPARE create_store_admin_index_stmt;

SET @store_admin_unique_index = (
    SELECT INDEX_NAME
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'store'
      AND COLUMN_NAME = 'store_admin_id'
      AND NON_UNIQUE = 0
      AND INDEX_NAME <> 'PRIMARY'
    LIMIT 1
);
SET @drop_store_admin_unique = IF(
    @store_admin_unique_index IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE store DROP INDEX `', REPLACE(@store_admin_unique_index, '`', '``'), '`')
);
PREPARE drop_store_admin_unique_stmt FROM @drop_store_admin_unique;
EXECUTE drop_store_admin_unique_stmt;
DEALLOCATE PREPARE drop_store_admin_unique_stmt;
