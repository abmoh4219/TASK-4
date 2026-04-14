CREATE TABLE messages (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  recipient_id  BIGINT NOT NULL,
  sender_type   VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
  category      VARCHAR(50) NOT NULL,
  subject       VARCHAR(500) NOT NULL,
  body          TEXT NOT NULL,
  related_id    BIGINT NULL,
  related_type  VARCHAR(50) NULL,
  is_read       TINYINT(1) NOT NULL DEFAULT 0,
  thread_key    VARCHAR(200) NULL,
  thread_count  INT NOT NULL DEFAULT 1,
  created_at    DATETIME NOT NULL,
  deliver_at    DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_msg_recipient (recipient_id, is_read),
  KEY idx_msg_thread (thread_key),
  KEY idx_msg_related (related_type, related_id),
  CONSTRAINT fk_msg_recipient FOREIGN KEY (recipient_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE message_preferences (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  user_id           BIGINT NOT NULL,
  muted_categories  VARCHAR(500) NOT NULL DEFAULT '',
  quiet_start_hour  TINYINT NOT NULL DEFAULT 22,
  quiet_end_hour    TINYINT NOT NULL DEFAULT 7,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pref_user (user_id),
  CONSTRAINT fk_pref_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
