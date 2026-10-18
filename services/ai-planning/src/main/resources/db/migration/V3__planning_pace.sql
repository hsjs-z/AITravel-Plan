SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE planning_task ADD COLUMN pace enum(''RELAXED'',''BALANCED'',''VIBRANT'') NOT NULL DEFAULT ''BALANCED''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'planning_task' AND column_name = 'pace'
);
PREPARE v3_stmt FROM @ddl;
EXECUTE v3_stmt;
DEALLOCATE PREPARE v3_stmt;

ALTER TABLE planning_task MODIFY COLUMN budget bigint NULL;
