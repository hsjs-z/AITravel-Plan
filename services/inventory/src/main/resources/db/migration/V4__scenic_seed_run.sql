CREATE TABLE scenic_seed_run (
  id varchar(255) NOT NULL,
  status enum('RUNNING','SUCCEEDED','PARTIAL_FAILED','FAILED') NOT NULL,
  requested_by varchar(255) NOT NULL,
  started_at datetime(6) NOT NULL,
  finished_at datetime(6) DEFAULT NULL,
  failure_reason varchar(512) DEFAULT NULL,
  report_json longtext,
  PRIMARY KEY (id),
  KEY idx_scenic_seed_status (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
