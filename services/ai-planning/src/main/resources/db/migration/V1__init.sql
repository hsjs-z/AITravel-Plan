CREATE TABLE planning_task (
  id varchar(255) NOT NULL,
  budget bigint NOT NULL,
  created_at datetime(6) NOT NULL,
  days int NOT NULL,
  destination varchar(255) NOT NULL,
  failure_reason varchar(512) DEFAULT NULL,
  itinerary_id varchar(255) DEFAULT NULL,
  party_size int NOT NULL,
  preferences varchar(255) DEFAULT NULL,
  result text,
  retry_of varchar(255) DEFAULT NULL,
  status enum('PENDING','RUNNING','SUCCEEDED','FAILED') NOT NULL,
  updated_at datetime(6) NOT NULL,
  user_id varchar(255) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
