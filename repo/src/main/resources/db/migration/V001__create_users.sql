CREATE TABLE users (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  username          VARCHAR(100) NOT NULL,
  password_hash     VARCHAR(255) NOT NULL,
  role              VARCHAR(30)  NOT NULL,
  email             VARCHAR(200) NULL,
  full_name         VARCHAR(200) NULL,
  is_active         TINYINT(1)   NOT NULL DEFAULT 1,
  deleted_at        DATETIME     NULL,
  export_file_path  VARCHAR(500) NULL,
  created_at        DATETIME     NOT NULL,
  updated_at        DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
