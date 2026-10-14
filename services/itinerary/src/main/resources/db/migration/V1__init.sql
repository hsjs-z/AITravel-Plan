CREATE TABLE itinerary (
  id varchar(255) NOT NULL,
  budget bigint NOT NULL,
  created_at datetime(6) NOT NULL,
  destination varchar(255) NOT NULL,
  end_date date NOT NULL,
  has_order bit(1) NOT NULL,
  party_size int NOT NULL,
  start_date date NOT NULL,
  status enum('DRAFT','CONFIRMED','LOCKED','COMPLETED') NOT NULL,
  updated_at datetime(6) NOT NULL,
  user_id varchar(255) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE itinerary_item (
  id varchar(255) NOT NULL,
  day_date date NOT NULL,
  description varchar(1024) NOT NULL,
  itinerary_id varchar(255) NOT NULL,
  resource_id varchar(255) DEFAULT NULL,
  resource_type varchar(255) DEFAULT NULL,
  source enum('AI','MANUAL') NOT NULL,
  spec_id varchar(255) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
