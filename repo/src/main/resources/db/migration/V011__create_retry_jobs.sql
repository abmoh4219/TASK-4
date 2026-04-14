CREATE TABLE retry_jobs (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  job_type        VARCHAR(100) NOT NULL,
  payload         TEXT NOT NULL,
  attempt_count   INT NOT NULL DEFAULT 0,
  max_attempts    INT NOT NULL DEFAULT 3,
  next_retry_at   DATETIME NOT NULL,
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  error_message   TEXT NULL,
  created_at      DATETIME NOT NULL,
  updated_at      DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_retry_status_next (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
