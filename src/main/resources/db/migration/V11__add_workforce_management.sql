CREATE TABLE work_schedule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    note VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_work_schedule_employee_date UNIQUE (employee_id, work_date),
    CONSTRAINT fk_work_schedule_employee FOREIGN KEY (employee_id) REFERENCES users(id),
    CONSTRAINT fk_work_schedule_branch FOREIGN KEY (branch_id) REFERENCES branch(id)
);

CREATE TABLE attendance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    check_in DATETIME(6),
    check_out DATETIME(6),
    status VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
    note VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_attendance_employee_date UNIQUE (employee_id, work_date),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES users(id),
    CONSTRAINT fk_attendance_branch FOREIGN KEY (branch_id) REFERENCES branch(id)
);

CREATE TABLE payroll (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    base_salary DECIMAL(19,2) NOT NULL DEFAULT 0,
    bonus DECIMAL(19,2) NOT NULL DEFAULT 0,
    deduction DECIMAL(19,2) NOT NULL DEFAULT 0,
    net_salary DECIMAL(19,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    paid_at DATETIME(6),
    note VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_payroll_employee_period UNIQUE (employee_id, period_start, period_end),
    CONSTRAINT fk_payroll_employee FOREIGN KEY (employee_id) REFERENCES users(id),
    CONSTRAINT fk_payroll_store FOREIGN KEY (store_id) REFERENCES store(id)
);

CREATE INDEX idx_work_schedule_branch_date ON work_schedule (branch_id, work_date);
CREATE INDEX idx_attendance_branch_date ON attendance (branch_id, work_date);
CREATE INDEX idx_payroll_store_period ON payroll (store_id, period_start, period_end);
