-- APPEND-ONLY by design. The application layer NEVER updates or deletes audit rows.
-- AuditLogRepository exposes save() (for inserts) and findBy* methods only.
CREATE TABLE audit_logs (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  actor_id          BIGINT NULL,
  actor_username    VARCHAR(100) NULL,
  action            VARCHAR(200) NOT NULL,
  entity_type       VARCHAR(100) NOT NULL,
  entity_id         BIGINT NULL,
  old_value_masked  TEXT NULL,
  new_value_masked  TEXT NULL,
  ip_address        VARCHAR(45) NULL,
  created_at        DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_actor (actor_id),
  KEY idx_audit_entity (entity_type, entity_id),
  KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
