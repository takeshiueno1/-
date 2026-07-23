ALTER TABLE timesheets
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by VARCHAR(100);

ALTER TABLE timesheets
    DROP CONSTRAINT timesheets_employee_month_unique;

CREATE UNIQUE INDEX timesheets_employee_month_active_unique
    ON timesheets(employee_id, target_year, target_month)
    WHERE deleted_at IS NULL;

CREATE INDEX timesheets_deleted_at_idx
    ON timesheets(deleted_at)
    WHERE deleted_at IS NOT NULL;
