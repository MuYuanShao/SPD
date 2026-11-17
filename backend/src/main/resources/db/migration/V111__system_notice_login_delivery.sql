CREATE TABLE system_notice_delivery (
  user_id BIGINT UNSIGNED NOT NULL,
  notice_id BIGINT UNSIGNED NOT NULL,
  login_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (user_id, notice_id),
  KEY idx_notice_delivery_login (user_id, login_id, notice_id),
  FOREIGN KEY (notice_id) REFERENCES system_notice (notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
