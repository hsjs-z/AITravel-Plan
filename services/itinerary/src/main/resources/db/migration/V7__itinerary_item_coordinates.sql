ALTER TABLE itinerary_item
  ADD COLUMN latitude decimal(10,7) DEFAULT NULL,
  ADD COLUMN longitude decimal(10,7) DEFAULT NULL;
