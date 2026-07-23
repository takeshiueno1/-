ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE employees
   SET display_name = '上野 豪',
       updated_at = CURRENT_TIMESTAMP
 WHERE username = 'test';
