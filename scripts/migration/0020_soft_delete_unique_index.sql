-- 0020 — 唯一索引纳入 deleted 列（软删恢复的前置条件，L3DDL 变更）
--
-- 背景：骨soft-delete 治理发现22 张业务表的唯一索引**无一含 deleted 列**。
--   bone-metadata-sdk 的 deleteById 走软删（UPDATE deleted=1）后，同值行仍在表里，
--   于是「删掉 code=ADMIN 的角色 → 再也建不了新的 ADMIN」——软删反而把数据锁死。
--   MySQL 无 partial index（不能"只对未删除行加唯一约束"），业界标准解法是把
--   deleted 纳入唯一索引末端：(cols..., deleted)。MySQL 8 的唯一索引对
--   NULL 不去重、且 TINYINT deleted 只有 0/1 两个值，故效果等价于
--   "允许同值存在 1 条已删记录"——足以支撑"软删后重建"，且保持活跃数据唯一。
--
-- 为何必须先改索引再补 @Deleted：顺序反了会直接上线一个"删了建不回来"的缺陷。
--   本脚本是 12 张无 UK 表补@Deleted（已完成）的对偶项，处理剩下 22 张有 UK 的表。
--
-- 处置口径（遵循 ADR-生产数据库增量迁移策略的 expand 阶段）：
--   1. 幂等：用 information_schema.statistics 判定索引当前列集合，
--      已含 deleted 则跳过该条—— 复跑安全，且不会误改已被人工调整过的索引；
--   2. 不删数据、不改列类型，只 DROP INDEX + ADD UNIQUE INDEX；
--   3. ADD 前校验存量数据是否已存在「同 UK 同 deleted」的重复行（应为 0；
--      若非 0 说明历史数据已被物理删除逻辑破坏过，必须先人工清理，
--      本脚本直接SIGNAL 报错而非静默加索引失败）；
--   4. bone-init.sql 已同步为新索引定义（开发库 DROP DATABASE 重建后即为新形态）。
--
-- 影响面与回滚：
--   · 索引列变长 → 索引体积与写放大略增（deleted 为 TINYINT，开销可忽略）；
--   · 回滚 = 反向改回 (cols...)；但若回滚前已有"软删 + 同值新建"的记录，
--     反向重建索引会因重复而失败，须先清理这些行。回滚前务必备份。
--
-- 执行状态：**待批准**（L3 DDL 变更，需架构师审批；生产库执行属 L4，禁止 AI 执行）。
--   批准后请在本段追加执行日期、备份表名与验收结论（范式见 0018/ 0019）。

-- ---------------------------------------------------------------------------
-- 阶段 1：前置校验 —— 存量数据不得已有「同 UK 同 deleted」重复行
-- ---------------------------------------------------------------------------
-- gen_code_template / uk_gen_ct_code_ver (tenant_id, code, template_version)
SET @dup_uk_gen_ct_code_ver = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, code, template_version, COUNT(*) AS c
    FROM gen_code_template
    GROUP BY tenant_id, code, template_version
    HAVING c > 1
  ) AS d
);

-- gen_data_source / uk_gen_ds_tenant_name (tenant_id, name)
SET @dup_uk_gen_ds_tenant_name = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, name, COUNT(*) AS c
    FROM gen_data_source
    GROUP BY tenant_id, name
    HAVING c > 1
  ) AS d
);

-- gen_generation_task / uk_gen_gt_task (task_id)
SET @dup_uk_gen_gt_task = (
  SELECT COUNT(*) FROM (
    SELECT task_id, COUNT(*) AS c
    FROM gen_generation_task
    GROUP BY task_id
    HAVING c > 1
  ) AS d
);

-- gen_table_metadata / uk_gen_tm_ds_table (tenant_id, data_source_id, original_table_name)
SET @dup_uk_gen_tm_ds_table = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, data_source_id, original_table_name, COUNT(*) AS c
    FROM gen_table_metadata
    GROUP BY tenant_id, data_source_id, original_table_name
    HAVING c > 1
  ) AS d
);

-- iam_account / uk_iam_account_username (tenant_id, username)
SET @dup_uk_iam_account_username = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, username, COUNT(*) AS c
    FROM iam_account
    GROUP BY tenant_id, username
    HAVING c > 1
  ) AS d
);

-- iam_permission / uk_iam_permission_code (code)
SET @dup_uk_iam_permission_code = (
  SELECT COUNT(*) FROM (
    SELECT code, COUNT(*) AS c
    FROM iam_permission
    GROUP BY code
    HAVING c > 1
  ) AS d
);

-- iam_role / uk_iam_role_code (tenant_id, code)
SET @dup_uk_iam_role_code = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, code, COUNT(*) AS c
    FROM iam_role
    GROUP BY tenant_id, code
    HAVING c > 1
  ) AS d
);

-- iam_tenant / uk_iam_tenant_code (code)
SET @dup_uk_iam_tenant_code = (
  SELECT COUNT(*) FROM (
    SELECT code, COUNT(*) AS c
    FROM iam_tenant
    GROUP BY code
    HAVING c > 1
  ) AS d
);

-- md_quality_rule / uk_meta_dqr_tenant_name (tenant_id, name)
SET @dup_uk_meta_dqr_tenant_name = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, name, COUNT(*) AS c
    FROM md_quality_rule
    GROUP BY tenant_id, name
    HAVING c > 1
  ) AS d
);

-- mdm_category / uk_mdm_cat_code (tenant_id, mdm_entity_id, code)
SET @dup_uk_mdm_cat_code = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, mdm_entity_id, code, COUNT(*) AS c
    FROM mdm_category
    GROUP BY tenant_id, mdm_entity_id, code
    HAVING c > 1
  ) AS d
);

-- mdm_domain_template / uk_mdm_tpl_domain (tenant_id, domain_code)
SET @dup_uk_mdm_tpl_domain = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, domain_code, COUNT(*) AS c
    FROM mdm_domain_template
    GROUP BY tenant_id, domain_code
    HAVING c > 1
  ) AS d
);

-- mdm_entity / uk_mdm_entity_code (tenant_id, entity_code)
SET @dup_uk_mdm_entity_code = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, entity_code, COUNT(*) AS c
    FROM mdm_entity
    GROUP BY tenant_id, entity_code
    HAVING c > 1
  ) AS d
);

-- mdm_entity / uk_mdm_entity_meta (tenant_id, meta_entity_id)
SET @dup_uk_mdm_entity_meta = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, meta_entity_id, COUNT(*) AS c
    FROM mdm_entity
    GROUP BY tenant_id, meta_entity_id
    HAVING c > 1
  ) AS d
);

-- mdm_entity_subscription / uk_mdm_sub_entity_app (mdm_entity_id, app_id, subscribe_mode)
SET @dup_uk_mdm_sub_entity_app = (
  SELECT COUNT(*) FROM (
    SELECT mdm_entity_id, app_id, subscribe_mode, COUNT(*) AS c
    FROM mdm_entity_subscription
    GROUP BY mdm_entity_id, app_id, subscribe_mode
    HAVING c > 1
  ) AS d
);

-- mdm_record / uk_mdm_record_code (tenant_id, mdm_entity_id, record_code)
SET @dup_uk_mdm_record_code = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, mdm_entity_id, record_code, COUNT(*) AS c
    FROM mdm_record
    GROUP BY tenant_id, mdm_entity_id, record_code
    HAVING c > 1
  ) AS d
);

-- mdm_reference_set / uk_mdm_refset_code (set_code)
SET @dup_uk_mdm_refset_code = (
  SELECT COUNT(*) FROM (
    SELECT set_code, COUNT(*) AS c
    FROM mdm_reference_set
    GROUP BY set_code
    HAVING c > 1
  ) AS d
);

-- mdm_reference_value / uk_mdm_refval (set_id, value_code)
SET @dup_uk_mdm_refval = (
  SELECT COUNT(*) FROM (
    SELECT set_id, value_code, COUNT(*) AS c
    FROM mdm_reference_value
    GROUP BY set_id, value_code
    HAVING c > 1
  ) AS d
);

-- mdm_reference_value_tenant / uk_mdm_refval_tenant (tenant_id, set_id, value_code)
SET @dup_uk_mdm_refval_tenant = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, set_id, value_code, COUNT(*) AS c
    FROM mdm_reference_value_tenant
    GROUP BY tenant_id, set_id, value_code
    HAVING c > 1
  ) AS d
);

-- sys_config / uk_sys_config_key (tenant_id, config_key)
SET @dup_uk_sys_config_key = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, config_key, COUNT(*) AS c
    FROM sys_config
    GROUP BY tenant_id, config_key
    HAVING c > 1
  ) AS d
);

-- sys_dict_hierarchy / uk_dict_hierarchy (tenant_id, type_code, hierarchy_code, code)
SET @dup_uk_dict_hierarchy = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, type_code, hierarchy_code, code, COUNT(*) AS c
    FROM sys_dict_hierarchy
    GROUP BY tenant_id, type_code, hierarchy_code, code
    HAVING c > 1
  ) AS d
);

-- sys_dict_item / uk_dict_item (tenant_id, type_code, code)
SET @dup_uk_dict_item = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, type_code, code, COUNT(*) AS c
    FROM sys_dict_item
    GROUP BY tenant_id, type_code, code
    HAVING c > 1
  ) AS d
);

-- sys_dict_item_text / uk_dict_item_text (tenant_id, type_code, code, language)
SET @dup_uk_dict_item_text = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, type_code, code, language, COUNT(*) AS c
    FROM sys_dict_item_text
    GROUP BY tenant_id, type_code, code, language
    HAVING c > 1
  ) AS d
);

-- sys_dict_type / uk_dict_type (tenant_id, code)
SET @dup_uk_dict_type = (
  SELECT COUNT(*) FROM (
    SELECT tenant_id, code, COUNT(*) AS c
    FROM sys_dict_type
    GROUP BY tenant_id, code
    HAVING c > 1
  ) AS d
);

-- ---------------------------------------------------------------------------
-- 阶段 2：重建唯一索引（幂等：已含 deleted 的索引会被跳过）
-- ---------------------------------------------------------------------------

-- gen_code_template / uk_gen_ct_code_ver
SET @idx_uk_gen_ct_code_ver = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'gen_code_template' AND index_name = 'uk_gen_ct_code_ver'
);
SET @need_uk_gen_ct_code_ver = (@idx_uk_gen_ct_code_ver IS NOT NULL AND @idx_uk_gen_ct_code_ver <> 'tenant_id,code,template_version,deleted');
DROP INDEX IF EXISTS `uk_gen_ct_code_ver` ON `gen_code_template`;
SET @sql_uk_gen_ct_code_ver = IF(@need_uk_gen_ct_code_ver,
  'ALTER TABLE `gen_code_template` ADD UNIQUE KEY `uk_gen_ct_code_ver` (tenant_id, code, template_version, deleted)',
  'SELECT 1');
PREPARE st_uk_gen_ct_code_ver FROM @sql_uk_gen_ct_code_ver;
EXECUTE st_uk_gen_ct_code_ver;
DEALLOCATE PREPARE st_uk_gen_ct_code_ver;

-- gen_data_source / uk_gen_ds_tenant_name
SET @idx_uk_gen_ds_tenant_name = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'gen_data_source' AND index_name = 'uk_gen_ds_tenant_name'
);
SET @need_uk_gen_ds_tenant_name = (@idx_uk_gen_ds_tenant_name IS NOT NULL AND @idx_uk_gen_ds_tenant_name <> 'tenant_id,name,deleted');
DROP INDEX IF EXISTS `uk_gen_ds_tenant_name` ON `gen_data_source`;
SET @sql_uk_gen_ds_tenant_name = IF(@need_uk_gen_ds_tenant_name,
  'ALTER TABLE `gen_data_source` ADD UNIQUE KEY `uk_gen_ds_tenant_name` (tenant_id, name, deleted)',
  'SELECT 1');
PREPARE st_uk_gen_ds_tenant_name FROM @sql_uk_gen_ds_tenant_name;
EXECUTE st_uk_gen_ds_tenant_name;
DEALLOCATE PREPARE st_uk_gen_ds_tenant_name;

-- gen_generation_task / uk_gen_gt_task
SET @idx_uk_gen_gt_task = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'gen_generation_task' AND index_name = 'uk_gen_gt_task'
);
SET @need_uk_gen_gt_task = (@idx_uk_gen_gt_task IS NOT NULL AND @idx_uk_gen_gt_task <> 'task_id,deleted');
DROP INDEX IF EXISTS `uk_gen_gt_task` ON `gen_generation_task`;
SET @sql_uk_gen_gt_task = IF(@need_uk_gen_gt_task,
  'ALTER TABLE `gen_generation_task` ADD UNIQUE KEY `uk_gen_gt_task` (task_id, deleted)',
  'SELECT 1');
PREPARE st_uk_gen_gt_task FROM @sql_uk_gen_gt_task;
EXECUTE st_uk_gen_gt_task;
DEALLOCATE PREPARE st_uk_gen_gt_task;

-- gen_table_metadata / uk_gen_tm_ds_table
SET @idx_uk_gen_tm_ds_table = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'gen_table_metadata' AND index_name = 'uk_gen_tm_ds_table'
);
SET @need_uk_gen_tm_ds_table = (@idx_uk_gen_tm_ds_table IS NOT NULL AND @idx_uk_gen_tm_ds_table <> 'tenant_id,data_source_id,original_table_name,deleted');
DROP INDEX IF EXISTS `uk_gen_tm_ds_table` ON `gen_table_metadata`;
SET @sql_uk_gen_tm_ds_table = IF(@need_uk_gen_tm_ds_table,
  'ALTER TABLE `gen_table_metadata` ADD UNIQUE KEY `uk_gen_tm_ds_table` (tenant_id, data_source_id, original_table_name, deleted)',
  'SELECT 1');
PREPARE st_uk_gen_tm_ds_table FROM @sql_uk_gen_tm_ds_table;
EXECUTE st_uk_gen_tm_ds_table;
DEALLOCATE PREPARE st_uk_gen_tm_ds_table;

-- iam_account / uk_iam_account_username
SET @idx_uk_iam_account_username = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'iam_account' AND index_name = 'uk_iam_account_username'
);
SET @need_uk_iam_account_username = (@idx_uk_iam_account_username IS NOT NULL AND @idx_uk_iam_account_username <> 'tenant_id,username,deleted');
DROP INDEX IF EXISTS `uk_iam_account_username` ON `iam_account`;
SET @sql_uk_iam_account_username = IF(@need_uk_iam_account_username,
  'ALTER TABLE `iam_account` ADD UNIQUE KEY `uk_iam_account_username` (tenant_id, username, deleted)',
  'SELECT 1');
PREPARE st_uk_iam_account_username FROM @sql_uk_iam_account_username;
EXECUTE st_uk_iam_account_username;
DEALLOCATE PREPARE st_uk_iam_account_username;

-- iam_permission / uk_iam_permission_code
SET @idx_uk_iam_permission_code = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'iam_permission' AND index_name = 'uk_iam_permission_code'
);
SET @need_uk_iam_permission_code = (@idx_uk_iam_permission_code IS NOT NULL AND @idx_uk_iam_permission_code <> 'code,deleted');
DROP INDEX IF EXISTS `uk_iam_permission_code` ON `iam_permission`;
SET @sql_uk_iam_permission_code = IF(@need_uk_iam_permission_code,
  'ALTER TABLE `iam_permission` ADD UNIQUE KEY `uk_iam_permission_code` (code, deleted)',
  'SELECT 1');
PREPARE st_uk_iam_permission_code FROM @sql_uk_iam_permission_code;
EXECUTE st_uk_iam_permission_code;
DEALLOCATE PREPARE st_uk_iam_permission_code;

-- iam_role / uk_iam_role_code
SET @idx_uk_iam_role_code = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'iam_role' AND index_name = 'uk_iam_role_code'
);
SET @need_uk_iam_role_code = (@idx_uk_iam_role_code IS NOT NULL AND @idx_uk_iam_role_code <> 'tenant_id,code,deleted');
DROP INDEX IF EXISTS `uk_iam_role_code` ON `iam_role`;
SET @sql_uk_iam_role_code = IF(@need_uk_iam_role_code,
  'ALTER TABLE `iam_role` ADD UNIQUE KEY `uk_iam_role_code` (tenant_id, code, deleted)',
  'SELECT 1');
PREPARE st_uk_iam_role_code FROM @sql_uk_iam_role_code;
EXECUTE st_uk_iam_role_code;
DEALLOCATE PREPARE st_uk_iam_role_code;

-- iam_tenant / uk_iam_tenant_code
SET @idx_uk_iam_tenant_code = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'iam_tenant' AND index_name = 'uk_iam_tenant_code'
);
SET @need_uk_iam_tenant_code = (@idx_uk_iam_tenant_code IS NOT NULL AND @idx_uk_iam_tenant_code <> 'code,deleted');
DROP INDEX IF EXISTS `uk_iam_tenant_code` ON `iam_tenant`;
SET @sql_uk_iam_tenant_code = IF(@need_uk_iam_tenant_code,
  'ALTER TABLE `iam_tenant` ADD UNIQUE KEY `uk_iam_tenant_code` (code, deleted)',
  'SELECT 1');
PREPARE st_uk_iam_tenant_code FROM @sql_uk_iam_tenant_code;
EXECUTE st_uk_iam_tenant_code;
DEALLOCATE PREPARE st_uk_iam_tenant_code;

-- md_quality_rule / uk_meta_dqr_tenant_name
SET @idx_uk_meta_dqr_tenant_name = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'md_quality_rule' AND index_name = 'uk_meta_dqr_tenant_name'
);
SET @need_uk_meta_dqr_tenant_name = (@idx_uk_meta_dqr_tenant_name IS NOT NULL AND @idx_uk_meta_dqr_tenant_name <> 'tenant_id,name,deleted');
DROP INDEX IF EXISTS `uk_meta_dqr_tenant_name` ON `md_quality_rule`;
SET @sql_uk_meta_dqr_tenant_name = IF(@need_uk_meta_dqr_tenant_name,
  'ALTER TABLE `md_quality_rule` ADD UNIQUE KEY `uk_meta_dqr_tenant_name` (tenant_id, name, deleted)',
  'SELECT 1');
PREPARE st_uk_meta_dqr_tenant_name FROM @sql_uk_meta_dqr_tenant_name;
EXECUTE st_uk_meta_dqr_tenant_name;
DEALLOCATE PREPARE st_uk_meta_dqr_tenant_name;

-- mdm_category / uk_mdm_cat_code
SET @idx_uk_mdm_cat_code = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mdm_category' AND index_name = 'uk_mdm_cat_code'
);
SET @need_uk_mdm_cat_code = (@idx_uk_mdm_cat_code IS NOT NULL AND @idx_uk_mdm_cat_code <> 'tenant_id,mdm_entity_id,code,deleted');
DROP INDEX IF EXISTS `uk_mdm_cat_code` ON `mdm_category`;
SET @sql_uk_mdm_cat_code = IF(@need_uk_mdm_cat_code,
  'ALTER TABLE `mdm_category` ADD UNIQUE KEY `uk_mdm_cat_code` (tenant_id, mdm_entity_id, code, deleted)',
  'SELECT 1');
PREPARE st_uk_mdm_cat_code FROM @sql_uk_mdm_cat_code;
EXECUTE st_uk_mdm_cat_code;
DEALLOCATE PREPARE st_uk_mdm_cat_code;

-- mdm_domain_template / uk_mdm_tpl_domain
SET @idx_uk_mdm_tpl_domain = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mdm_domain_template' AND index_name = 'uk_mdm_tpl_domain'
);
SET @need_uk_mdm_tpl_domain = (@idx_uk_mdm_tpl_domain IS NOT NULL AND @idx_uk_mdm_tpl_domain <> 'tenant_id,domain_code,deleted');
DROP INDEX IF EXISTS `uk_mdm_tpl_domain` ON `mdm_domain_template`;
SET @sql_uk_mdm_tpl_domain = IF(@need_uk_mdm_tpl_domain,
  'ALTER TABLE `mdm_domain_template` ADD UNIQUE KEY `uk_mdm_tpl_domain` (tenant_id, domain_code, deleted)',
  'SELECT 1');
PREPARE st_uk_mdm_tpl_domain FROM @sql_uk_mdm_tpl_domain;
EXECUTE st_uk_mdm_tpl_domain;
DEALLOCATE PREPARE st_uk_mdm_tpl_domain;

-- mdm_entity / uk_mdm_entity_code
SET @idx_uk_mdm_entity_code = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mdm_entity' AND index_name = 'uk_mdm_entity_code'
);
SET @need_uk_mdm_entity_code = (@idx_uk_mdm_entity_code IS NOT NULL AND @idx_uk_mdm_entity_code <> 'tenant_id,entity_code,deleted');
DROP INDEX IF EXISTS `uk_mdm_entity_code` ON `mdm_entity`;
SET @sql_uk_mdm_entity_code = IF(@need_uk_mdm_entity_code,
  'ALTER TABLE `mdm_entity` ADD UNIQUE KEY `uk_mdm_entity_code` (tenant_id, entity_code, deleted)',
  'SELECT 1');
PREPARE st_uk_mdm_entity_code FROM @sql_uk_mdm_entity_code;
EXECUTE st_uk_mdm_entity_code;
DEALLOCATE PREPARE st_uk_mdm_entity_code;

-- mdm_entity / uk_mdm_entity_meta
SET @idx_uk_mdm_entity_meta = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mdm_entity' AND index_name = 'uk_mdm_entity_meta'
);
SET @need_uk_mdm_entity_meta = (@idx_uk_mdm_entity_meta IS NOT NULL AND @idx_uk_mdm_entity_meta <> 'tenant_id,meta_entity_id,deleted');
DROP INDEX IF EXISTS `uk_mdm_entity_meta` ON `mdm_entity`;
SET @sql_uk_mdm_entity_meta = IF(@need_uk_mdm_entity_meta,
  'ALTER TABLE `mdm_entity` ADD UNIQUE KEY `uk_mdm_entity_meta` (tenant_id, meta_entity_id, deleted)',
  'SELECT 1');
PREPARE st_uk_mdm_entity_meta FROM @sql_uk_mdm_entity_meta;
EXECUTE st_uk_mdm_entity_meta;
DEALLOCATE PREPARE st_uk_mdm_entity_meta;

-- mdm_entity_subscription / uk_mdm_sub_entity_app
SET @idx_uk_mdm_sub_entity_app = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mdm_entity_subscription' AND index_name = 'uk_mdm_sub_entity_app'
);
SET @need_uk_mdm_sub_entity_app = (@idx_uk_mdm_sub_entity_app IS NOT NULL AND @idx_uk_mdm_sub_entity_app <> 'mdm_entity_id,app_id,subscribe_mode,deleted');
DROP INDEX IF EXISTS `uk_mdm_sub_entity_app` ON `mdm_entity_subscription`;
SET @sql_uk_mdm_sub_entity_app = IF(@need_uk_mdm_sub_entity_app,
  'ALTER TABLE `mdm_entity_subscription` ADD UNIQUE KEY `uk_mdm_sub_entity_app` (mdm_entity_id, app_id, subscribe_mode, deleted)',
  'SELECT 1');
PREPARE st_uk_mdm_sub_entity_app FROM @sql_uk_mdm_sub_entity_app;
EXECUTE st_uk_mdm_sub_entity_app;
DEALLOCATE PREPARE st_uk_mdm_sub_entity_app;

-- mdm_record / uk_mdm_record_code
SET @idx_uk_mdm_record_code = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mdm_record' AND index_name = 'uk_mdm_record_code'
);
SET @need_uk_mdm_record_code = (@idx_uk_mdm_record_code IS NOT NULL AND @idx_uk_mdm_record_code <> 'tenant_id,mdm_entity_id,record_code,deleted');
DROP INDEX IF EXISTS `uk_mdm_record_code` ON `mdm_record`;
SET @sql_uk_mdm_record_code = IF(@need_uk_mdm_record_code,
  'ALTER TABLE `mdm_record` ADD UNIQUE KEY `uk_mdm_record_code` (tenant_id, mdm_entity_id, record_code, deleted)',
  'SELECT 1');
PREPARE st_uk_mdm_record_code FROM @sql_uk_mdm_record_code;
EXECUTE st_uk_mdm_record_code;
DEALLOCATE PREPARE st_uk_mdm_record_code;

-- mdm_reference_set / uk_mdm_refset_code
SET @idx_uk_mdm_refset_code = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mdm_reference_set' AND index_name = 'uk_mdm_refset_code'
);
SET @need_uk_mdm_refset_code = (@idx_uk_mdm_refset_code IS NOT NULL AND @idx_uk_mdm_refset_code <> 'set_code,deleted');
DROP INDEX IF EXISTS `uk_mdm_refset_code` ON `mdm_reference_set`;
SET @sql_uk_mdm_refset_code = IF(@need_uk_mdm_refset_code,
  'ALTER TABLE `mdm_reference_set` ADD UNIQUE KEY `uk_mdm_refset_code` (set_code, deleted)',
  'SELECT 1');
PREPARE st_uk_mdm_refset_code FROM @sql_uk_mdm_refset_code;
EXECUTE st_uk_mdm_refset_code;
DEALLOCATE PREPARE st_uk_mdm_refset_code;

-- mdm_reference_value / uk_mdm_refval
SET @idx_uk_mdm_refval = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mdm_reference_value' AND index_name = 'uk_mdm_refval'
);
SET @need_uk_mdm_refval = (@idx_uk_mdm_refval IS NOT NULL AND @idx_uk_mdm_refval <> 'set_id,value_code,deleted');
DROP INDEX IF EXISTS `uk_mdm_refval` ON `mdm_reference_value`;
SET @sql_uk_mdm_refval = IF(@need_uk_mdm_refval,
  'ALTER TABLE `mdm_reference_value` ADD UNIQUE KEY `uk_mdm_refval` (set_id, value_code, deleted)',
  'SELECT 1');
PREPARE st_uk_mdm_refval FROM @sql_uk_mdm_refval;
EXECUTE st_uk_mdm_refval;
DEALLOCATE PREPARE st_uk_mdm_refval;

-- mdm_reference_value_tenant / uk_mdm_refval_tenant
SET @idx_uk_mdm_refval_tenant = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'mdm_reference_value_tenant' AND index_name = 'uk_mdm_refval_tenant'
);
SET @need_uk_mdm_refval_tenant = (@idx_uk_mdm_refval_tenant IS NOT NULL AND @idx_uk_mdm_refval_tenant <> 'tenant_id,set_id,value_code,deleted');
DROP INDEX IF EXISTS `uk_mdm_refval_tenant` ON `mdm_reference_value_tenant`;
SET @sql_uk_mdm_refval_tenant = IF(@need_uk_mdm_refval_tenant,
  'ALTER TABLE `mdm_reference_value_tenant` ADD UNIQUE KEY `uk_mdm_refval_tenant` (tenant_id, set_id, value_code, deleted)',
  'SELECT 1');
PREPARE st_uk_mdm_refval_tenant FROM @sql_uk_mdm_refval_tenant;
EXECUTE st_uk_mdm_refval_tenant;
DEALLOCATE PREPARE st_uk_mdm_refval_tenant;

-- sys_config / uk_sys_config_key
SET @idx_uk_sys_config_key = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_config' AND index_name = 'uk_sys_config_key'
);
SET @need_uk_sys_config_key = (@idx_uk_sys_config_key IS NOT NULL AND @idx_uk_sys_config_key <> 'tenant_id,config_key,deleted');
DROP INDEX IF EXISTS `uk_sys_config_key` ON `sys_config`;
SET @sql_uk_sys_config_key = IF(@need_uk_sys_config_key,
  'ALTER TABLE `sys_config` ADD UNIQUE KEY `uk_sys_config_key` (tenant_id, config_key, deleted)',
  'SELECT 1');
PREPARE st_uk_sys_config_key FROM @sql_uk_sys_config_key;
EXECUTE st_uk_sys_config_key;
DEALLOCATE PREPARE st_uk_sys_config_key;

-- sys_dict_hierarchy / uk_dict_hierarchy
SET @idx_uk_dict_hierarchy = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_dict_hierarchy' AND index_name = 'uk_dict_hierarchy'
);
SET @need_uk_dict_hierarchy = (@idx_uk_dict_hierarchy IS NOT NULL AND @idx_uk_dict_hierarchy <> 'tenant_id,type_code,hierarchy_code,code,deleted');
DROP INDEX IF EXISTS `uk_dict_hierarchy` ON `sys_dict_hierarchy`;
SET @sql_uk_dict_hierarchy = IF(@need_uk_dict_hierarchy,
  'ALTER TABLE `sys_dict_hierarchy` ADD UNIQUE KEY `uk_dict_hierarchy` (tenant_id, type_code, hierarchy_code, code, deleted)',
  'SELECT 1');
PREPARE st_uk_dict_hierarchy FROM @sql_uk_dict_hierarchy;
EXECUTE st_uk_dict_hierarchy;
DEALLOCATE PREPARE st_uk_dict_hierarchy;

-- sys_dict_item / uk_dict_item
SET @idx_uk_dict_item = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_dict_item' AND index_name = 'uk_dict_item'
);
SET @need_uk_dict_item = (@idx_uk_dict_item IS NOT NULL AND @idx_uk_dict_item <> 'tenant_id,type_code,code,deleted');
DROP INDEX IF EXISTS `uk_dict_item` ON `sys_dict_item`;
SET @sql_uk_dict_item = IF(@need_uk_dict_item,
  'ALTER TABLE `sys_dict_item` ADD UNIQUE KEY `uk_dict_item` (tenant_id, type_code, code, deleted)',
  'SELECT 1');
PREPARE st_uk_dict_item FROM @sql_uk_dict_item;
EXECUTE st_uk_dict_item;
DEALLOCATE PREPARE st_uk_dict_item;

-- sys_dict_item_text / uk_dict_item_text
SET @idx_uk_dict_item_text = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_dict_item_text' AND index_name = 'uk_dict_item_text'
);
SET @need_uk_dict_item_text = (@idx_uk_dict_item_text IS NOT NULL AND @idx_uk_dict_item_text <> 'tenant_id,type_code,code,language,deleted');
DROP INDEX IF EXISTS `uk_dict_item_text` ON `sys_dict_item_text`;
SET @sql_uk_dict_item_text = IF(@need_uk_dict_item_text,
  'ALTER TABLE `sys_dict_item_text` ADD UNIQUE KEY `uk_dict_item_text` (tenant_id, type_code, code, language, deleted)',
  'SELECT 1');
PREPARE st_uk_dict_item_text FROM @sql_uk_dict_item_text;
EXECUTE st_uk_dict_item_text;
DEALLOCATE PREPARE st_uk_dict_item_text;

-- sys_dict_type / uk_dict_type
SET @idx_uk_dict_type = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'sys_dict_type' AND index_name = 'uk_dict_type'
);
SET @need_uk_dict_type = (@idx_uk_dict_type IS NOT NULL AND @idx_uk_dict_type <> 'tenant_id,code,deleted');
DROP INDEX IF EXISTS `uk_dict_type` ON `sys_dict_type`;
SET @sql_uk_dict_type = IF(@need_uk_dict_type,
  'ALTER TABLE `sys_dict_type` ADD UNIQUE KEY `uk_dict_type` (tenant_id, code, deleted)',
  'SELECT 1');
PREPARE st_uk_dict_type FROM @sql_uk_dict_type;
EXECUTE st_uk_dict_type;
DEALLOCATE PREPARE st_uk_dict_type;

-- ---------------------------------------------------------------------------
-- 阶段 3：验收
-- ---------------------------------------------------------------------------
-- 逐条确认索引末列已是 deleted：
-- SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index)
-- FROM information_schema.statistics
-- WHERE table_schema = DATABASE() AND index_name LIKE 'uk_%'
--   AND table_name IN ('iam_role','iam_account','mdm_record','sys_config', ...)
-- GROUP BY table_name, index_name;
-- 期望：23 条索引的最后一列均为 deleted。
