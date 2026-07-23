CREATE TABLE credential_recovery_requests (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RESOLVED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(100)
);

CREATE UNIQUE INDEX credential_recovery_pending_employee_idx
    ON credential_recovery_requests(employee_id)
    WHERE status = 'PENDING';

CREATE INDEX credential_recovery_status_requested_idx
    ON credential_recovery_requests(status, requested_at);
