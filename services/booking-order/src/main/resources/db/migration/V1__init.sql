CREATE TABLE orders (
  id varchar(255) NOT NULL,
  booked_at datetime(6) DEFAULT NULL,
  confirmed_at datetime(6) DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  failure_reason varchar(512) DEFAULT NULL,
  idempotency_key varchar(255) NOT NULL,
  itinerary_id varchar(255) NOT NULL,
  paid_at datetime(6) DEFAULT NULL,
  status enum('CREATED','BOOKED','PAID','CONFIRMED','CANCELLED','FAILED') NOT NULL,
  total_amount bigint NOT NULL,
  user_id varchar(255) NOT NULL,
  payment_id varchar(255) DEFAULT NULL,
  refund_id varchar(255) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_orders_idempotency (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_item (
  id varchar(255) NOT NULL,
  date date NOT NULL,
  lock_id varchar(255) DEFAULT NULL,
  order_id varchar(255) NOT NULL,
  quantity int NOT NULL,
  resource_id varchar(255) NOT NULL,
  spec_id varchar(255) NOT NULL,
  status enum('PENDING','BOOKED','FAILED','RELEASED') NOT NULL,
  unit_price bigint NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE booking_settlement_log (
  id varchar(255) NOT NULL,
  order_id varchar(255) NOT NULL,
  status enum('PENDING','SUCCEEDED','COMPENSATING','COMPENSATED','FAILED') NOT NULL,
  step varchar(255) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
