ALTER TABLE product
    ADD COLUMN cost_price DECIMAL(19,2) NOT NULL DEFAULT 0 AFTER mrp;

ALTER TABLE inventory
    ADD COLUMN min_stock_level INT NOT NULL DEFAULT 5 AFTER quantity;

ALTER TABLE order_item
    ADD COLUMN cost_amount DECIMAL(19,2) NOT NULL DEFAULT 0 AFTER price;

ALTER TABLE refund_item
    ADD COLUMN cost_amount DECIMAL(19,2) NOT NULL DEFAULT 0 AFTER amount;

CREATE INDEX idx_inventory_low_stock ON inventory (branch_id, min_stock_level, quantity);
