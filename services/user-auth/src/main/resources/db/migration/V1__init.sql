CREATE TABLE app_user (
  id varchar(255) NOT NULL,
  email varchar(255) NOT NULL,
  password_hash varchar(255) NOT NULL,
  phone varchar(32) DEFAULT NULL,
  role enum('USER','ADMIN') NOT NULL DEFAULT 'USER',
  created_at datetime(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
