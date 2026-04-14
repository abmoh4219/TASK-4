-- Admin-managed policy settings (audit #4).
-- Simple key/value store so operators can adjust policy without a redeploy.
-- Changes are logged via AuditService ('POLICY_UPDATED').
CREATE TABLE IF NOT EXISTS policy_settings (
    setting_key   VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    updated_by    BIGINT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
);

-- Seed the canonical order-policy keys with current defaults.
INSERT INTO policy_settings (setting_key, setting_value)
VALUES ('orders.payment_timeout_minutes', '30'),
       ('orders.refund_window_days',      '14'),
       ('orders.idempotency_window_minutes', '10'),
       ('retry.max_attempts',            '3'),
       ('notifications.quiet_start_hour', '22'),
       ('notifications.quiet_end_hour',   '7');
