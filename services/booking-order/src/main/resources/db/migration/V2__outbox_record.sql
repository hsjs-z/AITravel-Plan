CREATE TABLE outbox_record (
  id varchar(255) NOT NULL,
  topic varchar(64) NOT NULL,
  event_type varchar(64) NOT NULL,
  aggregate_type varchar(32) NOT NULL,
  aggregate_id varchar(64) NOT NULL,
  status enum('NEW','SENT') NOT NULL,
  payload text NOT NULL,
  trace_id varchar(64) DEFAULT NULL,
  attempts int NOT NULL,
  last_error varchar(512) DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  published_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_outbox_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
