-- Fresh-install schema. Existing deployments are baselined at version 1 by Flyway;
-- follow-on migrations must be reviewed against their data before deployment.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255), phone VARCHAR(255),
    role VARCHAR(40) NOT NULL,
    created_at DATETIME(6), updated_at DATETIME(6), last_login DATETIME(6),
    store_id BIGINT, branch_id BIGINT
);
CREATE TABLE store (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    brand VARCHAR(255) NOT NULL, description VARCHAR(255), store_type VARCHAR(255),
    status VARCHAR(20), created_at DATETIME(6), updated_at DATETIME(6),
    store_admin_id BIGINT UNIQUE, address VARCHAR(255), phone VARCHAR(255), email VARCHAR(255)
);
CREATE TABLE branch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255), address VARCHAR(255), phone VARCHAR(255), email VARCHAR(255),
    open_time TIME, close_time TIME, created_at DATETIME(6), updated_at DATETIME(6),
    store_id BIGINT, manager_id BIGINT UNIQUE
);
CREATE TABLE branch_working_days (branch_id BIGINT NOT NULL, working_days VARCHAR(255));
CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, store_id BIGINT,
    CONSTRAINT uk_category_store_name UNIQUE (store_id, name)
);
CREATE TABLE product (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL, sku VARCHAR(255) NOT NULL UNIQUE, description VARCHAR(255),
    mrp DECIMAL(19,2), selling_price DECIMAL(19,2) NOT NULL, brand VARCHAR(255), image VARCHAR(255),
    created_at DATETIME(6), updated_at DATETIME(6), category_id BIGINT, store_id BIGINT
);
CREATE TABLE inventory (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, quantity INT NOT NULL, version BIGINT,
    last_update DATETIME(6), branch_id BIGINT, product_id BIGINT,
    CONSTRAINT uk_inventory_branch_product UNIQUE (branch_id, product_id)
);
CREATE TABLE customer (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255), phone VARCHAR(255), created_at DATETIME(6), updated_at DATETIME(6), store_id BIGINT NOT NULL
);
CREATE TABLE shift_report (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, shift_start DATETIME(6), shift_end DATETIME(6),
    total_sales DECIMAL(19,2), total_refund DECIMAL(19,2), net_sale DECIMAL(19,2), total_order INT NOT NULL,
    cashier_id BIGINT, branch_id BIGINT
);
CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, total_amount DECIMAL(19,2) NOT NULL,
    created_at DATETIME(6), updated_at DATETIME(6), branch_id BIGINT, cashier_id BIGINT, customer_id BIGINT,
    payment_type VARCHAR(20), status VARCHAR(30) NOT NULL, idempotency_key VARCHAR(100) UNIQUE, shift_report_id BIGINT
);
CREATE TABLE order_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, quantity INT, price DECIMAL(19,2) NOT NULL,
    product_id BIGINT, order_id BIGINT
);
CREATE TABLE refund (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, reason VARCHAR(255), amount DECIMAL(19,2) NOT NULL,
    created_at DATETIME(6), order_id BIGINT, shift_report_id BIGINT, cashier_id BIGINT, branch_id BIGINT, payment_type VARCHAR(20)
);
CREATE TABLE refund_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, quantity INT NOT NULL, amount DECIMAL(19,2) NOT NULL,
    refund_id BIGINT NOT NULL, product_id BIGINT NOT NULL
);
ALTER TABLE users ADD CONSTRAINT fk_users_store FOREIGN KEY (store_id) REFERENCES store(id);
ALTER TABLE users ADD CONSTRAINT fk_users_branch FOREIGN KEY (branch_id) REFERENCES branch(id);
ALTER TABLE store ADD CONSTRAINT fk_store_admin FOREIGN KEY (store_admin_id) REFERENCES users(id);
ALTER TABLE branch ADD CONSTRAINT fk_branch_store FOREIGN KEY (store_id) REFERENCES store(id);
ALTER TABLE branch ADD CONSTRAINT fk_branch_manager FOREIGN KEY (manager_id) REFERENCES users(id);
ALTER TABLE branch_working_days ADD CONSTRAINT fk_working_days_branch FOREIGN KEY (branch_id) REFERENCES branch(id);
ALTER TABLE category ADD CONSTRAINT fk_category_store FOREIGN KEY (store_id) REFERENCES store(id);
ALTER TABLE product ADD CONSTRAINT fk_product_store FOREIGN KEY (store_id) REFERENCES store(id);
ALTER TABLE product ADD CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id);
ALTER TABLE inventory ADD CONSTRAINT fk_inventory_branch FOREIGN KEY (branch_id) REFERENCES branch(id);
ALTER TABLE inventory ADD CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES product(id);
ALTER TABLE customer ADD CONSTRAINT fk_customer_store FOREIGN KEY (store_id) REFERENCES store(id);
ALTER TABLE shift_report ADD CONSTRAINT fk_shift_cashier FOREIGN KEY (cashier_id) REFERENCES users(id);
ALTER TABLE shift_report ADD CONSTRAINT fk_shift_branch FOREIGN KEY (branch_id) REFERENCES branch(id);
ALTER TABLE orders ADD CONSTRAINT fk_order_branch FOREIGN KEY (branch_id) REFERENCES branch(id);
ALTER TABLE orders ADD CONSTRAINT fk_order_cashier FOREIGN KEY (cashier_id) REFERENCES users(id);
ALTER TABLE orders ADD CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id);
ALTER TABLE orders ADD CONSTRAINT fk_order_shift FOREIGN KEY (shift_report_id) REFERENCES shift_report(id);
ALTER TABLE order_item ADD CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product(id);
ALTER TABLE order_item ADD CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id);
ALTER TABLE refund ADD CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders(id);
ALTER TABLE refund ADD CONSTRAINT fk_refund_shift FOREIGN KEY (shift_report_id) REFERENCES shift_report(id);
ALTER TABLE refund ADD CONSTRAINT fk_refund_cashier FOREIGN KEY (cashier_id) REFERENCES users(id);
ALTER TABLE refund ADD CONSTRAINT fk_refund_branch FOREIGN KEY (branch_id) REFERENCES branch(id);
ALTER TABLE refund_item ADD CONSTRAINT fk_refund_item_refund FOREIGN KEY (refund_id) REFERENCES refund(id);
ALTER TABLE refund_item ADD CONSTRAINT fk_refund_item_product FOREIGN KEY (product_id) REFERENCES product(id);
