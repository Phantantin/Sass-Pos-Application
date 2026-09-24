CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NULL,
    actor_id BIGINT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NULL,
    detail VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_audit_log_store FOREIGN KEY (store_id) REFERENCES store(id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_log_store_created ON audit_log (store_id, created_at);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
