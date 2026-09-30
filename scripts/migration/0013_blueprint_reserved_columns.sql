-- ============================================================
-- 0013_blueprint_reserved_columns.sql
-- bone-blueprint 机制 B「预留列」串联基础 DDL（方案 A：运行时数据面自译 + 复用 SDK ColumnAllocator）。
--
-- 背景：此前机制 B 用 ALTER TABLE 给宿主表加真实 B 列（receiver_name 等 16 列），违背元数据驱动原则，
--       且绕过了 bone-metadata-sdk 已有的预留列机制（ColumnAllocator + column_allocation 表）。
--       本脚本把机制 B 改为：宿主表一次性固定 ext_* 预留列池，逻辑字段经 SDK 动态分配映射到 ext_*，
--       不再随租户/字段增长而运行时 ALTER。
--
-- 内容：
--   P1 建 SDK 扩展层表 field_metadata + column_allocation（取自 SDK 测试资源 ext_schema.sql，正式化，剔除未用的 ext_data_* 表）。
--   P2 meta_field 加 physical_column（机制 B 逻辑字段→ext_* 物理列映射；核心列留空）。
--   P3 三张宿主表加固定 ext_* 预留列池，容量对齐 ColumnAllocator.validateLimit
--      （STRING 20 / NUMBER·INTEGER·DATE 10 / TEXT·BOOLEAN 5 / JSON 3）。
--      注意：SDK ColumnAllocator.allocate 按 MAX(column_index)+1 分配，池必须按上限预置，否则会指向不存在的列。
--   P4 清理此前 ALTER 出来的 16 个真实 B 列（先备份 bak_*_0013，再 DROP），并把其 meta_field 业务行删除，
--      使 catalog 不再暴露这些废弃物理列；后续 B 字段一律经预留列池发布（见任务 #13/#15）。
--
-- 幂等：本 MySQL 构建不支持 ADD/DROP COLUMN IF [NOT] EXISTS 与 CREATE INDEX IF NOT EXISTS，
--       故 P2/P3/P4 用 information_schema 守卫（按表级存在性判断），可重复执行。
-- 状态：L3 DDL —— 需架构师审批后执行（本环境为 dev/demo，已按实施计划执行）。
-- ============================================================

-- ---------- P1：扩展层表（SDK 预留列机制真源） ----------
CREATE TABLE IF NOT EXISTS field_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tenant_id VARCHAR(50) NOT NULL COMMENT '租户ID',
    app_code VARCHAR(50) NOT NULL COMMENT '应用编码',
    biz_identity_code VARCHAR(50) NOT NULL COMMENT '业务身份编码',
    entity_type VARCHAR(50) NOT NULL COMMENT '实体类型（物理表名）',
    name VARCHAR(255) NOT NULL COMMENT '字段名称',
    column_name VARCHAR(255) NOT NULL COMMENT '列名称（ext_*）',
    data_type VARCHAR(50) NOT NULL COMMENT '数据类型',
    is_primary_key TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为主键',
    is_nullable TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可为空',
    default_value TEXT COMMENT '默认值',
    constraints VARCHAR(500) COMMENT '约束条件',
    is_virtual TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为虚拟字段',
    is_extension TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为扩展字段',
    deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除标识',
    created_by BIGINT NULL COMMENT '创建者ID',
    updated_by BIGINT NULL COMMENT '更新者ID',
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_field_metadata UNIQUE (entity_type, name),
    INDEX idx_field_data_type (data_type)
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='字段元数据表，记录所有字段（含预留列分配）的结构化信息';

CREATE TABLE IF NOT EXISTS column_allocation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  app_code VARCHAR(64) NOT NULL,
  biz_identity_code VARCHAR(64) NOT NULL,
  entity_type VARCHAR(128) NOT NULL,
  data_type VARCHAR(20) NOT NULL CHECK (data_type IN ('STRING','NUMBER','TEXT','DATE','BOOLEAN','INTEGER','JSON','XML','GEO')),
  column_name VARCHAR(64) NOT NULL,
  column_index INT NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE','IN_USE','RECYCLED')),
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
  created_by BIGINT COMMENT '创建人',
  updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT COMMENT '更新人',
  recycled_at TIMESTAMP(3) NULL COMMENT '回收时间',
  CONSTRAINT uq_allocation UNIQUE (tenant_id, app_code, biz_identity_code, entity_type, data_type, column_index),
  CONSTRAINT uq_column_name UNIQUE (tenant_id, app_code, biz_identity_code, entity_type, column_name)
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='扩展列分配表（SDK ColumnAllocator 维护的预留列池状态）';
-- 注：uq_allocation / uq_column_name 唯一键已对 (tenant_id,app_code,biz_identity_code,entity_type,...) 提供左前缀索引，
--     足以覆盖 SDK 按上下文查询的 WHERE 条件，故不再单独建二级索引（本 MySQL 构建不支持 CREATE INDEX IF NOT EXISTS）。

-- ---------- P2：meta_field 加 physical_column（守卫：列不存在才加） ----------
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='meta_field' AND column_name='physical_column');
SET @s = IF(@c=0,
  "ALTER TABLE meta_field ADD COLUMN physical_column VARCHAR(64) DEFAULT NULL COMMENT '预留列物理列名：机制 B 逻辑字段映射到的 ext_* 物理列；核心列(A)/领域列留空'",
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- P3：宿主表加固定 ext_* 预留列池（守卫：以 ext_str_01 是否存在为整表判定） ----------
-- 列类型：STRING→ext_str VARCHAR(255)；TEXT→ext_text TEXT；NUMBER→ext_num DECIMAL(28,6)；
--        INTEGER→ext_int BIGINT；DATE→ext_date DATETIME(6)；BOOLEAN→ext_boolean TINYINT(1)；JSON→ext_json JSON。
-- 池大小：STRING 20 / TEXT 5 / NUMBER 10 / INTEGER 10 / DATE 10 / BOOLEAN 5 / JSON 3。
-- 说明：TEXT / JSON 列不允许 DEFAULT，故不加 DEFAULT NULL。

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_order' AND column_name='ext_str_01');
SET @s = IF(@c=0,
  "ALTER TABLE t_order
    ADD COLUMN ext_str_01 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列01',
    ADD COLUMN ext_str_02 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列02',
    ADD COLUMN ext_str_03 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列03',
    ADD COLUMN ext_str_04 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列04',
    ADD COLUMN ext_str_05 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列05',
    ADD COLUMN ext_str_06 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列06',
    ADD COLUMN ext_str_07 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列07',
    ADD COLUMN ext_str_08 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列08',
    ADD COLUMN ext_str_09 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列09',
    ADD COLUMN ext_str_10 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列10',
    ADD COLUMN ext_str_11 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列11',
    ADD COLUMN ext_str_12 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列12',
    ADD COLUMN ext_str_13 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列13',
    ADD COLUMN ext_str_14 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列14',
    ADD COLUMN ext_str_15 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列15',
    ADD COLUMN ext_str_16 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列16',
    ADD COLUMN ext_str_17 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列17',
    ADD COLUMN ext_str_18 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列18',
    ADD COLUMN ext_str_19 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列19',
    ADD COLUMN ext_str_20 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列20',
    ADD COLUMN ext_text_01 TEXT COMMENT '预留长文本列01',
    ADD COLUMN ext_text_02 TEXT COMMENT '预留长文本列02',
    ADD COLUMN ext_text_03 TEXT COMMENT '预留长文本列03',
    ADD COLUMN ext_text_04 TEXT COMMENT '预留长文本列04',
    ADD COLUMN ext_text_05 TEXT COMMENT '预留长文本列05',
    ADD COLUMN ext_num_01 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列01',
    ADD COLUMN ext_num_02 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列02',
    ADD COLUMN ext_num_03 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列03',
    ADD COLUMN ext_num_04 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列04',
    ADD COLUMN ext_num_05 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列05',
    ADD COLUMN ext_num_06 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列06',
    ADD COLUMN ext_num_07 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列07',
    ADD COLUMN ext_num_08 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列08',
    ADD COLUMN ext_num_09 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列09',
    ADD COLUMN ext_num_10 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列10',
    ADD COLUMN ext_int_01 BIGINT DEFAULT NULL COMMENT '预留整数列01',
    ADD COLUMN ext_int_02 BIGINT DEFAULT NULL COMMENT '预留整数列02',
    ADD COLUMN ext_int_03 BIGINT DEFAULT NULL COMMENT '预留整数列03',
    ADD COLUMN ext_int_04 BIGINT DEFAULT NULL COMMENT '预留整数列04',
    ADD COLUMN ext_int_05 BIGINT DEFAULT NULL COMMENT '预留整数列05',
    ADD COLUMN ext_int_06 BIGINT DEFAULT NULL COMMENT '预留整数列06',
    ADD COLUMN ext_int_07 BIGINT DEFAULT NULL COMMENT '预留整数列07',
    ADD COLUMN ext_int_08 BIGINT DEFAULT NULL COMMENT '预留整数列08',
    ADD COLUMN ext_int_09 BIGINT DEFAULT NULL COMMENT '预留整数列09',
    ADD COLUMN ext_int_10 BIGINT DEFAULT NULL COMMENT '预留整数列10',
    ADD COLUMN ext_date_01 DATETIME(6) DEFAULT NULL COMMENT '预留日期列01',
    ADD COLUMN ext_date_02 DATETIME(6) DEFAULT NULL COMMENT '预留日期列02',
    ADD COLUMN ext_date_03 DATETIME(6) DEFAULT NULL COMMENT '预留日期列03',
    ADD COLUMN ext_date_04 DATETIME(6) DEFAULT NULL COMMENT '预留日期列04',
    ADD COLUMN ext_date_05 DATETIME(6) DEFAULT NULL COMMENT '预留日期列05',
    ADD COLUMN ext_date_06 DATETIME(6) DEFAULT NULL COMMENT '预留日期列06',
    ADD COLUMN ext_date_07 DATETIME(6) DEFAULT NULL COMMENT '预留日期列07',
    ADD COLUMN ext_date_08 DATETIME(6) DEFAULT NULL COMMENT '预留日期列08',
    ADD COLUMN ext_date_09 DATETIME(6) DEFAULT NULL COMMENT '预留日期列09',
    ADD COLUMN ext_date_10 DATETIME(6) DEFAULT NULL COMMENT '预留日期列10',
    ADD COLUMN ext_boolean_01 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列01',
    ADD COLUMN ext_boolean_02 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列02',
    ADD COLUMN ext_boolean_03 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列03',
    ADD COLUMN ext_boolean_04 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列04',
    ADD COLUMN ext_boolean_05 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列05',
    ADD COLUMN ext_json_01 JSON COMMENT '预留JSON列01',
    ADD COLUMN ext_json_02 JSON COMMENT '预留JSON列02',
    ADD COLUMN ext_json_03 JSON COMMENT '预留JSON列03'",
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_order_item' AND column_name='ext_str_01');
SET @s = IF(@c=0,
  "ALTER TABLE t_order_item
    ADD COLUMN ext_str_01 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列01',
    ADD COLUMN ext_str_02 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列02',
    ADD COLUMN ext_str_03 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列03',
    ADD COLUMN ext_str_04 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列04',
    ADD COLUMN ext_str_05 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列05',
    ADD COLUMN ext_str_06 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列06',
    ADD COLUMN ext_str_07 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列07',
    ADD COLUMN ext_str_08 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列08',
    ADD COLUMN ext_str_09 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列09',
    ADD COLUMN ext_str_10 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列10',
    ADD COLUMN ext_str_11 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列11',
    ADD COLUMN ext_str_12 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列12',
    ADD COLUMN ext_str_13 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列13',
    ADD COLUMN ext_str_14 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列14',
    ADD COLUMN ext_str_15 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列15',
    ADD COLUMN ext_str_16 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列16',
    ADD COLUMN ext_str_17 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列17',
    ADD COLUMN ext_str_18 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列18',
    ADD COLUMN ext_str_19 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列19',
    ADD COLUMN ext_str_20 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列20',
    ADD COLUMN ext_text_01 TEXT COMMENT '预留长文本列01',
    ADD COLUMN ext_text_02 TEXT COMMENT '预留长文本列02',
    ADD COLUMN ext_text_03 TEXT COMMENT '预留长文本列03',
    ADD COLUMN ext_text_04 TEXT COMMENT '预留长文本列04',
    ADD COLUMN ext_text_05 TEXT COMMENT '预留长文本列05',
    ADD COLUMN ext_num_01 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列01',
    ADD COLUMN ext_num_02 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列02',
    ADD COLUMN ext_num_03 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列03',
    ADD COLUMN ext_num_04 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列04',
    ADD COLUMN ext_num_05 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列05',
    ADD COLUMN ext_num_06 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列06',
    ADD COLUMN ext_num_07 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列07',
    ADD COLUMN ext_num_08 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列08',
    ADD COLUMN ext_num_09 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列09',
    ADD COLUMN ext_num_10 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列10',
    ADD COLUMN ext_int_01 BIGINT DEFAULT NULL COMMENT '预留整数列01',
    ADD COLUMN ext_int_02 BIGINT DEFAULT NULL COMMENT '预留整数列02',
    ADD COLUMN ext_int_03 BIGINT DEFAULT NULL COMMENT '预留整数列03',
    ADD COLUMN ext_int_04 BIGINT DEFAULT NULL COMMENT '预留整数列04',
    ADD COLUMN ext_int_05 BIGINT DEFAULT NULL COMMENT '预留整数列05',
    ADD COLUMN ext_int_06 BIGINT DEFAULT NULL COMMENT '预留整数列06',
    ADD COLUMN ext_int_07 BIGINT DEFAULT NULL COMMENT '预留整数列07',
    ADD COLUMN ext_int_08 BIGINT DEFAULT NULL COMMENT '预留整数列08',
    ADD COLUMN ext_int_09 BIGINT DEFAULT NULL COMMENT '预留整数列09',
    ADD COLUMN ext_int_10 BIGINT DEFAULT NULL COMMENT '预留整数列10',
    ADD COLUMN ext_date_01 DATETIME(6) DEFAULT NULL COMMENT '预留日期列01',
    ADD COLUMN ext_date_02 DATETIME(6) DEFAULT NULL COMMENT '预留日期列02',
    ADD COLUMN ext_date_03 DATETIME(6) DEFAULT NULL COMMENT '预留日期列03',
    ADD COLUMN ext_date_04 DATETIME(6) DEFAULT NULL COMMENT '预留日期列04',
    ADD COLUMN ext_date_05 DATETIME(6) DEFAULT NULL COMMENT '预留日期列05',
    ADD COLUMN ext_date_06 DATETIME(6) DEFAULT NULL COMMENT '预留日期列06',
    ADD COLUMN ext_date_07 DATETIME(6) DEFAULT NULL COMMENT '预留日期列07',
    ADD COLUMN ext_date_08 DATETIME(6) DEFAULT NULL COMMENT '预留日期列08',
    ADD COLUMN ext_date_09 DATETIME(6) DEFAULT NULL COMMENT '预留日期列09',
    ADD COLUMN ext_date_10 DATETIME(6) DEFAULT NULL COMMENT '预留日期列10',
    ADD COLUMN ext_boolean_01 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列01',
    ADD COLUMN ext_boolean_02 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列02',
    ADD COLUMN ext_boolean_03 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列03',
    ADD COLUMN ext_boolean_04 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列04',
    ADD COLUMN ext_boolean_05 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列05',
    ADD COLUMN ext_json_01 JSON COMMENT '预留JSON列01',
    ADD COLUMN ext_json_02 JSON COMMENT '预留JSON列02',
    ADD COLUMN ext_json_03 JSON COMMENT '预留JSON列03'",
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='bp_payment' AND column_name='ext_str_01');
SET @s = IF(@c=0,
  "ALTER TABLE bp_payment
    ADD COLUMN ext_str_01 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列01',
    ADD COLUMN ext_str_02 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列02',
    ADD COLUMN ext_str_03 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列03',
    ADD COLUMN ext_str_04 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列04',
    ADD COLUMN ext_str_05 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列05',
    ADD COLUMN ext_str_06 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列06',
    ADD COLUMN ext_str_07 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列07',
    ADD COLUMN ext_str_08 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列08',
    ADD COLUMN ext_str_09 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列09',
    ADD COLUMN ext_str_10 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列10',
    ADD COLUMN ext_str_11 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列11',
    ADD COLUMN ext_str_12 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列12',
    ADD COLUMN ext_str_13 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列13',
    ADD COLUMN ext_str_14 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列14',
    ADD COLUMN ext_str_15 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列15',
    ADD COLUMN ext_str_16 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列16',
    ADD COLUMN ext_str_17 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列17',
    ADD COLUMN ext_str_18 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列18',
    ADD COLUMN ext_str_19 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列19',
    ADD COLUMN ext_str_20 VARCHAR(255) DEFAULT NULL COMMENT '预留文本列20',
    ADD COLUMN ext_text_01 TEXT COMMENT '预留长文本列01',
    ADD COLUMN ext_text_02 TEXT COMMENT '预留长文本列02',
    ADD COLUMN ext_text_03 TEXT COMMENT '预留长文本列03',
    ADD COLUMN ext_text_04 TEXT COMMENT '预留长文本列04',
    ADD COLUMN ext_text_05 TEXT COMMENT '预留长文本列05',
    ADD COLUMN ext_num_01 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列01',
    ADD COLUMN ext_num_02 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列02',
    ADD COLUMN ext_num_03 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列03',
    ADD COLUMN ext_num_04 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列04',
    ADD COLUMN ext_num_05 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列05',
    ADD COLUMN ext_num_06 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列06',
    ADD COLUMN ext_num_07 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列07',
    ADD COLUMN ext_num_08 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列08',
    ADD COLUMN ext_num_09 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列09',
    ADD COLUMN ext_num_10 DECIMAL(28,6) DEFAULT NULL COMMENT '预留数值列10',
    ADD COLUMN ext_int_01 BIGINT DEFAULT NULL COMMENT '预留整数列01',
    ADD COLUMN ext_int_02 BIGINT DEFAULT NULL COMMENT '预留整数列02',
    ADD COLUMN ext_int_03 BIGINT DEFAULT NULL COMMENT '预留整数列03',
    ADD COLUMN ext_int_04 BIGINT DEFAULT NULL COMMENT '预留整数列04',
    ADD COLUMN ext_int_05 BIGINT DEFAULT NULL COMMENT '预留整数列05',
    ADD COLUMN ext_int_06 BIGINT DEFAULT NULL COMMENT '预留整数列06',
    ADD COLUMN ext_int_07 BIGINT DEFAULT NULL COMMENT '预留整数列07',
    ADD COLUMN ext_int_08 BIGINT DEFAULT NULL COMMENT '预留整数列08',
    ADD COLUMN ext_int_09 BIGINT DEFAULT NULL COMMENT '预留整数列09',
    ADD COLUMN ext_int_10 BIGINT DEFAULT NULL COMMENT '预留整数列10',
    ADD COLUMN ext_date_01 DATETIME(6) DEFAULT NULL COMMENT '预留日期列01',
    ADD COLUMN ext_date_02 DATETIME(6) DEFAULT NULL COMMENT '预留日期列02',
    ADD COLUMN ext_date_03 DATETIME(6) DEFAULT NULL COMMENT '预留日期列03',
    ADD COLUMN ext_date_04 DATETIME(6) DEFAULT NULL COMMENT '预留日期列04',
    ADD COLUMN ext_date_05 DATETIME(6) DEFAULT NULL COMMENT '预留日期列05',
    ADD COLUMN ext_date_06 DATETIME(6) DEFAULT NULL COMMENT '预留日期列06',
    ADD COLUMN ext_date_07 DATETIME(6) DEFAULT NULL COMMENT '预留日期列07',
    ADD COLUMN ext_date_08 DATETIME(6) DEFAULT NULL COMMENT '预留日期列08',
    ADD COLUMN ext_date_09 DATETIME(6) DEFAULT NULL COMMENT '预留日期列09',
    ADD COLUMN ext_date_10 DATETIME(6) DEFAULT NULL COMMENT '预留日期列10',
    ADD COLUMN ext_boolean_01 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列01',
    ADD COLUMN ext_boolean_02 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列02',
    ADD COLUMN ext_boolean_03 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列03',
    ADD COLUMN ext_boolean_04 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列04',
    ADD COLUMN ext_boolean_05 TINYINT(1) DEFAULT 0 COMMENT '预留布尔列05',
    ADD COLUMN ext_json_01 JSON COMMENT '预留JSON列01',
    ADD COLUMN ext_json_02 JSON COMMENT '预留JSON列02',
    ADD COLUMN ext_json_03 JSON COMMENT '预留JSON列03'",
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- P4：清理此前 ALTER 出的真实 B 列（先备份，再 DROP），并删除其 meta_field 业务行 ----------
-- 备份（仅首次落盘，重复执行不覆盖）：
CREATE TABLE IF NOT EXISTS bak_t_order_0013    AS SELECT * FROM t_order;
CREATE TABLE IF NOT EXISTS bak_t_order_item_0013 AS SELECT * FROM t_order_item;
CREATE TABLE IF NOT EXISTS bak_bp_payment_0013  AS SELECT * FROM bp_payment;

-- t_order：删除 9 个废弃真实 B 列（守卫：列存在才 DROP）
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_order' AND column_name='receiver_name');
SET @s = IF(@c>0,
  "ALTER TABLE t_order
    DROP COLUMN receiver_name,
    DROP COLUMN receiver_phone,
    DROP COLUMN receiver_region,
    DROP COLUMN receiver_address,
    DROP COLUMN buyer_message,
    DROP COLUMN invoice_title,
    DROP COLUMN logistics_company,
    DROP COLUMN logistics_no,
    DROP COLUMN expected_delivery_time",
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- t_order_item：删除 4 个废弃真实 B 列
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_order_item' AND column_name='sku_code');
SET @s = IF(@c>0,
  "ALTER TABLE t_order_item
    DROP COLUMN sku_code,
    DROP COLUMN sku_spec,
    DROP COLUMN discount_amount,
    DROP COLUMN gift_flag",
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- bp_payment：删除 3 个废弃真实 B 列
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='bp_payment' AND column_name='payer_account');
SET @s = IF(@c>0,
  "ALTER TABLE bp_payment
    DROP COLUMN payer_account,
    DROP COLUMN channel_fee,
    DROP COLUMN callback_raw",
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 删除这些废弃 B 列在 catalog 中的业务行（实体 ID 见 760313249324335104 / 760313249655685120 / 760313249877983232）
DELETE FROM meta_field
  WHERE entity_id = 760313249324335104
    AND code IN ('receiver_name','receiver_phone','receiver_region','receiver_address','buyer_message','invoice_title','logistics_company','logistics_no','expected_delivery_time');
DELETE FROM meta_field
  WHERE entity_id = 760313249655685120
    AND code IN ('sku_code','sku_spec','discount_amount','gift_flag');
DELETE FROM meta_field
  WHERE entity_id = 760313249877983232
    AND code IN ('payer_account','channel_fee','callback_raw');
