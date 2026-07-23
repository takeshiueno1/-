CREATE TABLE company_calendar_imports (
    id BIGSERIAL PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 1 AND 10485760),
    page_count INTEGER NOT NULL CHECK (page_count BETWEEN 1 AND 50),
    imported_dates INTEGER NOT NULL CHECK (imported_dates >= 1),
    imported_by VARCHAR(100) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE company_holidays
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'SEED',
    ADD COLUMN import_id BIGINT REFERENCES company_calendar_imports(id) ON DELETE RESTRICT;
