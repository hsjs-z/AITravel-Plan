INSERT INTO resource (id, type, name, destination, status, attributes)
VALUES
  ('r-hotel-1', 'TRADABLE', '丽江古城精品酒店', '云南', 'ON_SHELF', NULL),
  ('r-attraction-1', 'TRADABLE', '玉龙雪山', '云南', 'ON_SHELF', NULL),
  ('r-food-1', 'CONTENT', '过桥米线', '云南', 'ON_SHELF', NULL),
  ('r-culture-1', 'CONTENT', '丽江古城', '云南', 'ON_SHELF', NULL)
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type), destination = VALUES(destination), status = VALUES(status);

INSERT INTO spec (id, resource_id, spec_type, name, occupancy, price)
VALUES
  ('s-room-1', 'r-hotel-1', 'ROOM_TYPE', '标准大床房', 2, 500),
  ('s-room-2', 'r-hotel-1', 'ROOM_TYPE', '家庭套房', 4, 900),
  ('s-ticket-1', 'r-attraction-1', 'TICKET_TYPE', '成人票', NULL, 260),
  ('s-ticket-2', 'r-attraction-1', 'TICKET_TYPE', '儿童票', NULL, 150)
ON DUPLICATE KEY UPDATE name = VALUES(name), spec_type = VALUES(spec_type), price = VALUES(price), occupancy = VALUES(occupancy);

INSERT INTO inventory_unit (id, resource_id, date, spec_id, total, available, locked, version)
SELECT
  CONCAT('u-', s.resource_id, '-', s.id, '-', DATE_FORMAT(d.d, '%Y%m%d')),
  s.resource_id,
  d.d,
  s.id,
  20,
  20,
  0,
  0
FROM spec s
CROSS JOIN (
  SELECT DATE('2026-10-14') + INTERVAL n DAY AS d
  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
        UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) nums
) d
WHERE s.resource_id IN ('r-hotel-1', 'r-attraction-1')
ON DUPLICATE KEY UPDATE total = VALUES(total);
