CREATE TABLE notification (
  id varchar(255) NOT NULL,
  user_id varchar(255) NOT NULL,
  type enum('BOOKING_SUCCEEDED','BOOKING_FAILED','PAYMENT_SUCCEEDED','PAYMENT_FAILED',
            'REFUND_SUCCEEDED','ORDER_CONFIRMED','ORDER_CANCELLED','ITINERARY_CHANGED') NOT NULL,
  channel enum('IN_APP','EMAIL') NOT NULL,
  business_ref varchar(255) DEFAULT NULL,
  dedup_key varchar(255) NOT NULL,
  read_status enum('UNREAD','READ') NOT NULL,
  title varchar(255) NOT NULL,
  body varchar(512) DEFAULT NULL,
  delivery_status enum('PENDING','SENT','FAILED') NOT NULL,
  delivery_attempts int NOT NULL,
  last_error varchar(512) DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  read_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_dedup (dedup_key),
  KEY idx_notification_user_channel_created (user_id, channel, created_at),
  KEY idx_notification_delivery (channel, delivery_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消费侧幂等表：同一事件 id 只处理一次（宪法 IV；FR-035）。
CREATE TABLE processed_event (
  event_id varchar(255) NOT NULL,
  event_type varchar(64) NOT NULL,
  topic varchar(64) NOT NULL,
  outcome varchar(32) NOT NULL,
  processed_at datetime(6) NOT NULL,
  PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
