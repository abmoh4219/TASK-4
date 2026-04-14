CREATE TABLE login_attempts (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  username      VARCHAR(100) NOT NULL,
  ip_address    VARCHAR(45)  NULL,
  attempted_at  DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_login_attempts_username (username),
  KEY idx_login_attempts_attempted_at (attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE device_bindings (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  user_id      BIGINT NOT NULL,
  device_hash  VARCHAR(128) NOT NULL,
  label        VARCHAR(200) NULL,
  bound_at     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_device_user (user_id),
  CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE security_notices (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  user_id      BIGINT NOT NULL,
  notice_type  VARCHAR(50)  NOT NULL,
  message      VARCHAR(500) NOT NULL,
  is_read      TINYINT(1)   NOT NULL DEFAULT 0,
  created_at   DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_notice_user (user_id),
  CONSTRAINT fk_notice_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
