CREATE TABLE grade_components (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  course_id       BIGINT NOT NULL,
  student_id      BIGINT NOT NULL,
  component_name  VARCHAR(100) NOT NULL,
  score           DECIMAL(5,2) NOT NULL,
  max_score       DECIMAL(5,2) NOT NULL DEFAULT 100.00,
  attempt_number  INT NOT NULL DEFAULT 1,
  recorded_by     BIGINT NULL,
  recorded_at     DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_gc_course_student (course_id, student_id),
  KEY idx_gc_attempt (attempt_number),
  CONSTRAINT fk_gc_course FOREIGN KEY (course_id) REFERENCES courses(id),
  CONSTRAINT fk_gc_student FOREIGN KEY (student_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_grades (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  student_id      BIGINT NOT NULL,
  course_id       BIGINT NOT NULL,
  rule_version_id BIGINT NOT NULL,
  weighted_score  DECIMAL(5,2) NOT NULL,
  letter_grade    VARCHAR(5) NOT NULL,
  gpa_points      DECIMAL(3,2) NOT NULL,
  credits         DECIMAL(4,2) NOT NULL,
  calculated_at   DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_sg_student (student_id),
  KEY idx_sg_course (course_id),
  KEY idx_sg_rule (rule_version_id),
  CONSTRAINT fk_sg_student FOREIGN KEY (student_id) REFERENCES users(id),
  CONSTRAINT fk_sg_course  FOREIGN KEY (course_id)  REFERENCES courses(id),
  CONSTRAINT fk_sg_rule    FOREIGN KEY (rule_version_id) REFERENCES grade_rules(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
