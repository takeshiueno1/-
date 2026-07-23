ALTER TABLE users
    ADD COLUMN failed_login_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ;

ALTER TABLE timesheets
    ADD COLUMN required_work_minutes INTEGER NOT NULL DEFAULT 480
        CHECK (required_work_minutes BETWEEN 1 AND 1440);
