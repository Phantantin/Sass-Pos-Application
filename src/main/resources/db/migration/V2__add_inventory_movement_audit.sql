CREATE TABLE inventory_movement (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    performed_by_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    quantity_before INT NOT NULL,
    quantity_after INT NOT NULL,
    quantity_delta INT NOT NULL,
    reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_inventory_movement_branch FOREIGN KEY (branch_id) REFERENCES branch(id),
    CONSTRAINT fk_inventory_movement_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_inventory_movement_user FOREIGN KEY (performed_by_id) REFERENCES users(id)
);

CREATE INDEX idx_inventory_movement_branch_created ON inventory_movement (branch_id, created_at);
CREATE INDEX idx_inventory_movement_branch_product_created ON inventory_movement (branch_id, product_id, created_at);
