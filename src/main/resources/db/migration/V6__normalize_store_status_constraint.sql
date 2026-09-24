-- Hibernate previously generated an unnamed enum check (for example
-- store_chk_1) on installations that persisted StoreStatus as an ordinal.
-- Drop that status-only check before converting existing rows to enum names.
SET @store_status_check := (
    SELECT tc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    INNER JOIN information_schema.CHECK_CONSTRAINTS cc
        ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
        AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'store'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND LOWER(cc.CHECK_CLAUSE) LIKE '%status%'
    LIMIT 1
);

SET @drop_store_status_check := IF(
    @store_status_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE `store` DROP CHECK `', REPLACE(@store_status_check, '`', '``'), '`')
);
PREPARE drop_store_status_check_statement FROM @drop_store_status_check;
EXECUTE drop_store_status_check_statement;
DEALLOCATE PREPARE drop_store_status_check_statement;

-- Preserve any ordinal values produced by older Hibernate mappings; unknown
-- and NULL legacy values become the safe onboarding state, PENDING.
UPDATE store
SET status = CASE CAST(status AS CHAR)
    WHEN '0' THEN 'ACTIVE'
    WHEN '1' THEN 'PENDING'
    WHEN '2' THEN 'BLOCKED'
    WHEN 'ACTIVE' THEN 'ACTIVE'
    WHEN 'PENDING' THEN 'PENDING'
    WHEN 'BLOCKED' THEN 'BLOCKED'
    ELSE 'PENDING'
END;

ALTER TABLE store
    MODIFY COLUMN status ENUM('ACTIVE', 'PENDING', 'BLOCKED') NOT NULL;
