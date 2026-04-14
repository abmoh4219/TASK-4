CREATE TABLE enrollments (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  student_id   BIGINT NOT NULL,
  course_id    BIGINT NOT NULL,
  enrolled_at  DATETIME NOT NULL,
  status       VARCHAR(20) NOT NULL DEFAULT 'active',
  PRIMARY KEY (id),
  UNIQUE KEY uk_enrollment_student_course (student_id, course_id),
  KEY idx_enrollment_student (student_id),
  KEY idx_enrollment_course (course_id),
  CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES users(id),
  CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
