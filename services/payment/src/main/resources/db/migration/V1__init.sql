CREATE TABLE payment (
  id varchar(255) NOT NULL,
  amount bigint NOT NULL,
  channel varchar(16) NOT NULL,
  created_at datetime(6) NOT NULL,
  idempotency_key varchar(255) NOT NULL,
  order_id varchar(255) NOT NULL,
  paid_at datetime(6) DEFAULT NULL,
  status enum('PENDING','SUCCEEDED','FAILED') NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_idempotency (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refund (
  id varchar(255) NOT NULL,
  amount bigint NOT NULL,
  created_at datetime(6) NOT NULL,
  idempotency_key varchar(255) NOT NULL,
  order_id varchar(255) NOT NULL,
  payment_id varchar(255) NOT NULL,
  reason varchar(255) DEFAULT NULL,
  status enum('PENDING','SUCCEEDED','FAILED') NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_idempotency (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE callback_record (
  id varchar(255) NOT NULL,
  channel_ref varchar(255) DEFAULT NULL,
  payload_hash varchar(255) DEFAULT NULL,
  payment_id varchar(255) NOT NULL,
  received_at datetime(6) NOT NULL,
  reconciled bit(1) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
