CREATE TABLE store_subscription (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    plan VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    trial_start DATETIME(6),
    trial_end DATETIME(6),
    current_period_start DATETIME(6),
    current_period_end DATETIME(6),
    stripe_customer_id VARCHAR(255) UNIQUE,
    stripe_subscription_id VARCHAR(255) UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_store_subscription_store UNIQUE (store_id),
    CONSTRAINT fk_store_subscription_store FOREIGN KEY (store_id) REFERENCES store(id)
);

CREATE INDEX idx_store_subscription_status ON store_subscription (status);

INSERT INTO store_subscription (
    store_id, plan, status, trial_start, trial_end,
    current_period_start, current_period_end, version, created_at, updated_at
)
SELECT
    s.id, 'FREE', 'TRIALING', CURRENT_TIMESTAMP(6), DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 14 DAY),
    CURRENT_TIMESTAMP(6), DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 14 DAY), 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM store s
LEFT JOIN store_subscription existing_subscription ON existing_subscription.store_id = s.id
WHERE existing_subscription.store_id IS NULL;
