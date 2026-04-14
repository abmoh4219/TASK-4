CREATE TABLE courses (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  code            VARCHAR(20)  NOT NULL,
  title           VARCHAR(300) NOT NULL,
  description     TEXT         NULL,
  credits         DECIMAL(4,2) NOT NULL DEFAULT 3.00,
  category        VARCHAR(100) NULL,
  tags            VARCHAR(500) NULL,
  author_name     VARCHAR(200) NULL,
  cover_image_url VARCHAR(500) NULL,
  price           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  rating_avg      DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  enroll_count    INT          NOT NULL DEFAULT 0,
  is_active       TINYINT(1)   NOT NULL DEFAULT 1,
  created_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_courses_code (code),
  KEY idx_courses_category (category),
  KEY idx_courses_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE course_materials (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  course_id   BIGINT NOT NULL,
  title       VARCHAR(300) NOT NULL,
  type        VARCHAR(50)  NOT NULL,
  file_url    VARCHAR(500) NULL,
  price       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  created_at  DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_material_course (course_id),
  CONSTRAINT fk_material_course FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
