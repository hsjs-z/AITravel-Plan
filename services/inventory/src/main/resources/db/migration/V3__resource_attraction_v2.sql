-- V2 resource model. MySQL DDL is not transactional, so every step is written
-- to be safe to rerun: columns/indexes are added only when absent, and backfills
-- are idempotent. The unique key is added last so duplicate data fails loudly.

-- Pre-check before applying (must return no rows):
-- SELECT name, city, COUNT(*) FROM resource GROUP BY name, city HAVING COUNT(*) > 1;

ALTER TABLE resource
  MODIFY COLUMN type enum('TRADABLE','CONTENT','ATTRACTION') NOT NULL;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN province varchar(64) NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'province'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN city varchar(64) NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'city'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN level varchar(8) NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'level'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN honor_title varchar(10) NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'honor_title'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN summary varchar(512) NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'summary'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN suggested_hours decimal(4,1) NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'suggested_hours'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN ticket_price bigint NOT NULL DEFAULT 0',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'ticket_price'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN latitude decimal(10,7) NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'latitude'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN longitude decimal(10,7) NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'longitude'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN coordinate_status enum(''READY'',''PENDING'') NOT NULL DEFAULT ''PENDING''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'coordinate_status'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN image_url varchar(512) NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'image_url'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN seed_managed bit(1) NOT NULL DEFAULT 0',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'seed_managed'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD COLUMN seeded_at datetime(6) NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND column_name = 'seeded_at'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

-- Backfill legacy Yunnan/Beijing locations. These updates are idempotent.
UPDATE resource SET province = '云南', city = '丽江'
WHERE id IN ('r-hotel-1', 'r-attraction-1', 'r-culture-1', 'r-off-shelf-1');

UPDATE resource SET province = '云南', city = '昆明'
WHERE id IN ('r-train-1', 'r-food-3');

UPDATE resource SET province = '云南', city = '大理'
WHERE id IN ('r-content-1', 'r-content-4');

UPDATE resource SET province = '云南', city = '迪庆'
WHERE id = 'r-content-2';

UPDATE resource SET province = '云南', city = '蒙自'
WHERE id = 'r-food-1';

UPDATE resource SET province = '云南', city = '建水'
WHERE id = 'r-food-2';

UPDATE resource SET province = '云南', city = '丽江'
WHERE id = 'r-culture-2';

UPDATE resource SET province = '云南', city = NULL
WHERE id = 'r-content-3';

UPDATE resource SET province = '北京', city = '北京'
WHERE id IN (
  'r-hotel-2', 'r-attraction-2', 'r-food-4', 'r-food-5',
  'r-culture-3', 'r-culture-4', 'r-content-5', 'r-content-6', 'r-content-7'
);

-- Performance-test fixture: not a tourist resource; keep it out of province/city
-- search and out of the scenic-spot seed skip checks.
UPDATE resource SET province = NULL, city = NULL
WHERE id = 'load-test-resource';

-- Remove the obsolete two-column key if a previous attempt created it.
SET @ddl = (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE resource DROP INDEX uk_resource_name_city',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'resource' AND index_name = 'uk_resource_name_city'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

-- The three-column key is added last so duplicate rows fail without hiding
-- the already-applied additive migration steps.
SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD UNIQUE KEY uk_resource_name_city_type (name, city, type)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'resource'
    AND index_name = 'uk_resource_name_city_type'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE resource ADD KEY idx_resource_province_city_status (province, city, status)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'resource'
    AND index_name = 'idx_resource_province_city_status'
);
PREPARE v2_stmt FROM @ddl;
EXECUTE v2_stmt;
DEALLOCATE PREPARE v2_stmt;

-- Post-check (must return no rows):
-- SELECT name, city, COUNT(*) FROM resource GROUP BY name, city HAVING COUNT(*) > 1;
