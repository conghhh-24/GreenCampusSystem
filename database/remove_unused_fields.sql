-- 已有 MySQL 8 数据库：停止后端，再依次执行，成功后启动更新后的后端。
-- 仅执行一次。先备份将删除的字段值；任何语句报错时停止，不要继续执行后续语句。
-- 这些备份是旧字段值的存档，不是业务表，也不代替完整数据库备份。
USE campus_recycle;

CREATE TABLE backup_items_unused_fields_20261102 AS
SELECT id,original_price,estimated_recycle_price,condition_level FROM items;

CREATE TABLE backup_recycling_records_unused_fields_20261102 AS
SELECT id,recycle_amount FROM recycling_records;

CREATE TABLE backup_recycling_stations_unused_fields_20261102 AS
SELECT id,phone,accepted_categories FROM recycling_stations;

ALTER TABLE items
  DROP COLUMN original_price,
  DROP COLUMN estimated_recycle_price,
  DROP COLUMN condition_level;

ALTER TABLE recycling_records
  DROP COLUMN recycle_amount;

ALTER TABLE recycling_stations
  DROP COLUMN phone,
  DROP COLUMN accepted_categories;

-- 删除成功应返回0行。users.phone 不在删除范围内。
SELECT TABLE_NAME,COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE()
  AND ((TABLE_NAME='items' AND COLUMN_NAME IN ('original_price','estimated_recycle_price','condition_level'))
    OR (TABLE_NAME='recycling_records' AND COLUMN_NAME='recycle_amount')
    OR (TABLE_NAME='recycling_stations' AND COLUMN_NAME IN ('phone','accepted_categories')));
