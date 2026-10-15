-- 演示目录与库存种子数据（可重复执行：所有写入均带 ON DUPLICATE KEY UPDATE）。
-- 说明：资源 + 规格先落库，再由规格展开生成库存单元（库存单元 = 资源 + 日期 + 规格）。

INSERT INTO resource (id, type, name, destination, status, attributes)
VALUES
  ('r-hotel-1', 'TRADABLE', '丽江古城精品酒店', '云南', 'ON_SHELF', NULL),
  ('r-attraction-1', 'TRADABLE', '玉龙雪山', '云南', 'ON_SHELF', NULL),
  ('r-food-1', 'CONTENT', '过桥米线', '云南', 'ON_SHELF', NULL),
  ('r-culture-1', 'CONTENT', '丽江古城', '云南', 'ON_SHELF', NULL),
  -- US6 检索目录扩展：第二个目的地（北京）与车票。
  ('r-train-1', 'TRADABLE', '昆明—大理动车票', '云南', 'ON_SHELF', NULL),
  ('r-hotel-2', 'TRADABLE', '北京王府井商务酒店', '北京', 'ON_SHELF', NULL),
  ('r-attraction-2', 'TRADABLE', '故宫博物院门票', '北京', 'ON_SHELF', NULL),
  -- 下架示例：刻意不生成库存单元，用于验证「下架 != 售罄」且详情页不提供下单入口。
  ('r-off-shelf-1', 'TRADABLE', '停业装修中的客栈', '云南', 'OFF_SHELF', NULL)
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type),
  destination = VALUES(destination), status = VALUES(status);

INSERT INTO spec (id, resource_id, spec_type, name, occupancy, price)
VALUES
  ('s-room-1', 'r-hotel-1', 'ROOM_TYPE', '标准大床房', 2, 500),
  ('s-room-2', 'r-hotel-1', 'ROOM_TYPE', '家庭套房', 4, 900),
  ('s-ticket-1', 'r-attraction-1', 'TICKET_TYPE', '成人票', NULL, 260),
  ('s-ticket-2', 'r-attraction-1', 'TICKET_TYPE', '儿童票', NULL, 150),
  ('s-schedule-1', 'r-train-1', 'SCHEDULE', 'D8802 08:20 二等座', NULL, 145),
  ('s-schedule-2', 'r-train-1', 'SCHEDULE', 'D8802 08:20 一等座', NULL, 235),
  ('s-room-3', 'r-hotel-2', 'ROOM_TYPE', '标准双床房', 2, 680),
  ('s-ticket-3', 'r-attraction-2', 'TICKET_TYPE', '成人票', NULL, 60),
  ('s-room-9', 'r-off-shelf-1', 'ROOM_TYPE', '标准大床房', 2, 420)
ON DUPLICATE KEY UPDATE name = VALUES(name), spec_type = VALUES(spec_type),
  price = VALUES(price), occupancy = VALUES(occupancy);

-- 库存单元：2026-10-14 起 30 天；下架资源不参与生成。
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
  FROM (
    SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
    UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
    UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23
    UNION ALL SELECT 24 UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27
    UNION ALL SELECT 28 UNION ALL SELECT 29
  ) nums
) d
WHERE s.resource_id IN ('r-hotel-1', 'r-attraction-1', 'r-train-1', 'r-hotel-2', 'r-attraction-2')
ON DUPLICATE KEY UPDATE total = VALUES(total);
