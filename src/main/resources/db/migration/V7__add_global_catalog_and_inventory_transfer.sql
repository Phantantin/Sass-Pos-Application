-- Existing products are trusted so the migration does not hide the current catalog.
-- Products created by store-level roles after this migration start as PENDING in the service.
ALTER TABLE product ADD COLUMN catalog_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
CREATE INDEX idx_product_catalog_status_created ON product (catalog_status, created_at);

CREATE TABLE inventory_transfer_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    source_branch_id BIGINT NOT NULL,
    destination_branch_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    requested_by_id BIGINT NOT NULL,
    reviewed_by_id BIGINT,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    review_note VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT fk_transfer_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_transfer_source_branch FOREIGN KEY (source_branch_id) REFERENCES branch(id),
    CONSTRAINT fk_transfer_destination_branch FOREIGN KEY (destination_branch_id) REFERENCES branch(id),
    CONSTRAINT fk_transfer_requested_by FOREIGN KEY (requested_by_id) REFERENCES users(id),
    CONSTRAINT fk_transfer_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES users(id),
    CONSTRAINT chk_transfer_positive_quantity CHECK (quantity > 0),
    CONSTRAINT chk_transfer_distinct_branches CHECK (source_branch_id <> destination_branch_id)
);

CREATE INDEX idx_transfer_source_status_created
    ON inventory_transfer_request (source_branch_id, status, created_at);
CREATE INDEX idx_transfer_destination_status_created
    ON inventory_transfer_request (destination_branch_id, status, created_at);
CREATE INDEX idx_transfer_product_status
    ON inventory_transfer_request (product_id, status);
