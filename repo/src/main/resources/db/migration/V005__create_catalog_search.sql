CREATE TABLE search_terms (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  term              VARCHAR(255) NOT NULL,
  search_count      INT NOT NULL DEFAULT 0,
  last_searched_at  DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_search_terms_term (term),
  KEY idx_search_terms_count (search_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE catalog_ratings (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  item_type   VARCHAR(30) NOT NULL,
  item_id     BIGINT NOT NULL,
  score       TINYINT NOT NULL,
  created_at  DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rating_user_item (user_id, item_type, item_id),
  KEY idx_rating_item (item_type, item_id),
  CONSTRAINT fk_rating_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT chk_rating_score CHECK (score BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
