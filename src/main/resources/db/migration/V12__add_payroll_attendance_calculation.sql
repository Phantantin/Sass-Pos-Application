ALTER TABLE payroll
    ADD COLUMN hourly_rate DECIMAL(19,2) NOT NULL DEFAULT 0 AFTER base_salary,
    ADD COLUMN worked_minutes INT NOT NULL DEFAULT 0 AFTER hourly_rate;
