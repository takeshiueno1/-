CREATE TABLE excel_timesheet_imports (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    source_filename VARCHAR(255) NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    source_bytes BIGINT NOT NULL CHECK (source_bytes > 0),
    source_employee_name VARCHAR(100) NOT NULL DEFAULT '',
    source_employee_code VARCHAR(50) NOT NULL DEFAULT '',
    target_year INTEGER NOT NULL CHECK (target_year BETWEEN 2000 AND 2100),
    target_month INTEGER NOT NULL CHECK (target_month BETWEEN 1 AND 12),
    imported_rows INTEGER NOT NULL CHECK (imported_rows BETWEEN 1 AND 31),
    imported_by VARCHAR(100) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE timesheets
    ADD COLUMN source_import_id BIGINT REFERENCES excel_timesheet_imports(id) ON DELETE SET NULL;

CREATE INDEX excel_timesheet_imports_employee_month_idx
    ON excel_timesheet_imports(employee_id, target_year, target_month);
