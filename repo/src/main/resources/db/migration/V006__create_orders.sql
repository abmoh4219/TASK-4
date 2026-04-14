CREATE TABLE orders (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  correlation_id    VARCHAR(36) NOT NULL,
  student_id        BIGINT NOT NULL,
  status            VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  total_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  exception_status  TINYINT(1) NOT NULL DEFAULT 0,
  cancel_reason     VARCHAR(500) NULL,
  created_at        DATETIME NOT NULL,
  updated_at        DATETIME NOT NULL,
  paid_at           DATETIME NULL,
  canceled_at       DATETIME NULL,
  refunded_at       DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_correlation (correlation_id),
  KEY idx_order_student (student_id),
  KEY idx_order_status (status),
  KEY idx_order_created (created_at),
  CONSTRAINT fk_order_student FOREIGN KEY (student_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  order_id    BIGINT NOT NULL,
  item_type   VARCHAR(30) NOT NULL,
  item_id     BIGINT NOT NULL,
  item_name   VARCHAR(300) NOT NULL,
  unit_price  DECIMAL(10,2) NOT NULL,
  quantity    INT NOT NULL DEFAULT 1,
  subtotal    DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_orderitem_order (order_id),
  CONSTRAINT fk_orderitem_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
