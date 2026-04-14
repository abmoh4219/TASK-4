CREATE TABLE evaluation_cycles (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  course_id     BIGINT NOT NULL,
  faculty_id    BIGINT NOT NULL,
  title         VARCHAR(300) NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  reviewer_comment VARCHAR(2000) NULL,
  opened_at     DATETIME NULL,
  submitted_at  DATETIME NULL,
  closed_at     DATETIME NULL,
  created_at    DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cycle_course (course_id),
  KEY idx_cycle_faculty (faculty_id),
  KEY idx_cycle_status (status),
  CONSTRAINT fk_cycle_course  FOREIGN KEY (course_id)  REFERENCES courses(id),
  CONSTRAINT fk_cycle_faculty FOREIGN KEY (faculty_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE evaluation_indicators (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  cycle_id        BIGINT NOT NULL,
  indicator_name  VARCHAR(200) NOT NULL,
  weight          DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  score           DECIMAL(5,2) NULL,
  mean_score      DECIMAL(5,2) NULL,
  std_dev         DECIMAL(5,2) NULL,
  is_outlier      TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_ind_cycle (cycle_id),
  CONSTRAINT fk_ind_cycle FOREIGN KEY (cycle_id) REFERENCES evaluation_cycles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE evidence_attachments (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  cycle_id          BIGINT NOT NULL,
  original_filename VARCHAR(500) NOT NULL,
  stored_path       VARCHAR(1000) NOT NULL,
  mime_type         VARCHAR(100) NOT NULL,
  file_size_bytes   BIGINT NOT NULL,
  sha256_hash       VARCHAR(64) NOT NULL,
  uploaded_by       BIGINT NOT NULL,
  uploaded_at       DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_evidence_cycle (cycle_id),
  CONSTRAINT fk_evidence_cycle FOREIGN KEY (cycle_id) REFERENCES evaluation_cycles(id),
  CONSTRAINT fk_evidence_user  FOREIGN KEY (uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
