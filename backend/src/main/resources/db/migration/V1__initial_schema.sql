CREATE TABLE users (
    username VARCHAR(100) PRIMARY KEY,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE authorities (
    username VARCHAR(100) NOT NULL REFERENCES users(username) ON DELETE CASCADE,
    authority VARCHAR(100) NOT NULL,
    CONSTRAINT authorities_unique UNIQUE (username, authority)
);

CREATE INDEX authorities_username_idx ON authorities(username);

CREATE TABLE employees (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE REFERENCES users(username) ON DELETE RESTRICT,
    department VARCHAR(100) NOT NULL DEFAULT '',
    display_name VARCHAR(100) NOT NULL,
    position_name VARCHAR(100) NOT NULL DEFAULT '',
    employee_code VARCHAR(50) NOT NULL UNIQUE,
    work_schedule_type VARCHAR(50) NOT NULL DEFAULT '正社員（8時間）',
    standard_start TIME NOT NULL DEFAULT TIME '09:30',
    standard_end TIME NOT NULL DEFAULT TIME '18:30',
    standard_break_minutes INTEGER NOT NULL DEFAULT 60 CHECK (standard_break_minutes BETWEEN 0 AND 1440),
    default_system_code VARCHAR(50) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE company_holidays (
    work_date DATE PRIMARY KEY,
    holiday_name VARCHAR(100) NOT NULL
);

CREATE TABLE timesheets (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    target_year INTEGER NOT NULL CHECK (target_year BETWEEN 2000 AND 2100),
    target_month INTEGER NOT NULL CHECK (target_month BETWEEN 1 AND 12),
    standard_start TIME NOT NULL,
    standard_end TIME NOT NULL,
    standard_break_minutes INTEGER NOT NULL CHECK (standard_break_minutes BETWEEN 0 AND 1440),
    default_system_code VARCHAR(50) NOT NULL DEFAULT '',
    wg_participation VARCHAR(20),
    pmark_confirmation_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT timesheets_employee_month_unique UNIQUE (employee_id, target_year, target_month),
    CONSTRAINT wg_participation_allowed CHECK (
        wg_participation IS NULL OR wg_participation IN ('参加', '不参加', '当月未開催')
    )
);

CREATE TABLE daily_entries (
    id BIGSERIAL PRIMARY KEY,
    timesheet_id BIGINT NOT NULL REFERENCES timesheets(id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    day_type VARCHAR(20) NOT NULL CHECK (day_type IN ('WORKDAY', 'SATURDAY', 'SUNDAY', 'HOLIDAY')),
    start_time TIME,
    end_time TIME,
    break_minutes INTEGER CHECK (break_minutes BETWEEN 0 AND 1440),
    leave_type VARCHAR(50),
    work_detail VARCHAR(500) NOT NULL DEFAULT '',
    system_code VARCHAR(50) NOT NULL DEFAULT '',
    weekday_minutes INTEGER,
    holiday_minutes INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT daily_entries_timesheet_date_unique UNIQUE (timesheet_id, work_date)
);

CREATE INDEX daily_entries_timesheet_idx ON daily_entries(timesheet_id, work_date);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_username VARCHAR(100) NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    detail VARCHAR(1000) NOT NULL DEFAULT '',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO company_holidays (work_date, holiday_name) VALUES
    ('2025-12-29', '年末年始休暇'),
    ('2025-12-30', '年末年始休暇'),
    ('2025-12-31', '年末年始休暇'),
    ('2026-01-01', '元旦'),
    ('2026-01-02', '年末年始休暇'),
    ('2026-01-12', '成人の日'),
    ('2026-02-11', '建国記念の日'),
    ('2026-02-23', '天皇誕生日'),
    ('2026-03-20', '春分の日'),
    ('2026-04-29', '昭和の日'),
    ('2026-05-04', 'みどりの日'),
    ('2026-05-05', 'こどもの日'),
    ('2026-05-06', '振替休日'),
    ('2026-07-20', '海の日'),
    ('2026-08-10', 'クエリ創立記念日'),
    ('2026-08-11', '山の日'),
    ('2026-08-12', '夏季休暇'),
    ('2026-08-13', '夏季休暇'),
    ('2026-08-14', '夏季休暇'),
    ('2026-09-21', '敬老の日'),
    ('2026-09-22', '国民の休日'),
    ('2026-09-23', '秋分の日'),
    ('2026-10-12', 'スポーツの日'),
    ('2026-11-03', '文化の日'),
    ('2026-11-23', '勤労感謝の日'),
    ('2026-12-29', '年末年始休暇'),
    ('2026-12-30', '年末年始休暇'),
    ('2026-12-31', '年末年始休暇'),
    ('2027-01-01', '元旦'),
    ('2027-01-11', '成人の日'),
    ('2027-02-11', '建国記念の日'),
    ('2027-02-23', '天皇誕生日'),
    ('2027-03-22', '振替休日');
