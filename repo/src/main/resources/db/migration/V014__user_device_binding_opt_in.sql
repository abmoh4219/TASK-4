-- Make per-user device binding opt-in (audit #12).
-- Default OFF so existing users are unaffected; users that enable it get the
-- full unusual-login notice path (already wired through MessageService).
ALTER TABLE users
    ADD COLUMN device_binding_enabled TINYINT(1) NOT NULL DEFAULT 0;
