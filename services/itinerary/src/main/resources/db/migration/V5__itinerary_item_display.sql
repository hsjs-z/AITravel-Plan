ALTER TABLE itinerary_item
  ADD COLUMN name varchar(255) DEFAULT NULL,
  ADD COLUMN suggested_hours decimal(4,1) DEFAULT NULL,
  ADD COLUMN ticket_price bigint DEFAULT NULL;
