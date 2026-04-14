CREATE TABLE grade_rules (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  course_id       BIGINT NOT NULL,
  version         INT NOT NULL DEFAULT 1,
  is_active       TINYINT(1) NOT NULL DEFAULT 1,
  retake_policy   VARCHAR(30) NOT NULL DEFAULT 'HIGHEST_SCORE',
  weights_json    VARCHAR(2000) NOT NULL,
  created_by      BIGINT NULL,
  created_at      DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_rule_course (course_id),
  KEY idx_rule_active (is_active),
  CONSTRAINT fk_rule_course FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE grade_rule_history (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  rule_id       BIGINT NOT NULL,
  old_weights   VARCHAR(2000) NOT NULL,
  new_weights   VARCHAR(2000) NOT NULL,
  changed_by    BIGINT NULL,
  changed_at    DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_rule_history_rule (rule_id),
  CONSTRAINT fk_rule_history_rule FOREIGN KEY (rule_id) REFERENCES grade_rules(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
