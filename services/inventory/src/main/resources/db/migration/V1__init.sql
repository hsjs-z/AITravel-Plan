CREATE TABLE resource (
  id varchar(255) NOT NULL,
  attributes text,
  destination varchar(255) NOT NULL,
  name varchar(255) NOT NULL,
  status enum('ON_SHELF','OFF_SHELF') NOT NULL,
  type enum('TRADABLE','CONTENT') NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE spec (
  id varchar(255) NOT NULL,
  name varchar(255) NOT NULL,
  occupancy int DEFAULT NULL,
  resource_id varchar(255) NOT NULL,
  spec_type enum('ROOM_TYPE','TICKET_TYPE','SCHEDULE') NOT NULL,
  price bigint DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory_unit (
  id varchar(255) NOT NULL,
  available int NOT NULL,
  date date NOT NULL,
  locked int NOT NULL,
  resource_id varchar(255) NOT NULL,
  spec_id varchar(255) NOT NULL,
  total int NOT NULL,
  version bigint DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory_lock (
  id varchar(255) NOT NULL,
  created_at datetime(6) NOT NULL,
  holder_id varchar(255) DEFAULT NULL,
  holder_type varchar(255) DEFAULT NULL,
  inventory_unit_id varchar(255) NOT NULL,
  purpose enum('PLANNING','BOOKING') NOT NULL,
  quantity int NOT NULL,
  status enum('ACTIVE','RELEASED','EXPIRED') NOT NULL,
  ttl_seconds int NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
