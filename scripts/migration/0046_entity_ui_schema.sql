-- 0046：业务对象界面 schema 服务端化（P0-3 · 团队级 UI 协议落地）
--
-- 背景：业务对象「界面」设计从「个人级列投影（UiOverlay / 0042）」升级为「团队级 BoneUiSchema 协议」
-- （sections / controls / linkageRules / submitRules / actionBar / variants / i18n）。协议设计见
-- doc/design/lowcode/ui/ui-protocol/BONE-业务对象界面JSON协议设计.md，前后端技术方案见同目录
-- BONE-业务对象界面前后端技术方案.md。
--
-- 设计决策（与 0042 区分）：
--   A. 新表 meta_entity_ui_schema（团队级，键 (tenant_id, entity_id)）——不复用 meta_ui_overlay（用户级），
--      不给 meta_entity 加 MEDIUMTEXT 列（行膨胀 + 版本耦合）。
--   B. 建表为 expand-only（仅新建表 + 模板表加可空列）；零存量迁移，无 schema 的实体走前端回落渲染，行为不变。
--   C. 发布即快照：published_schema_json 随实体发布同事务冻结；base 指针用于模板跟随（notify 模式，永不自动推送）。
--   D. meta_model_template_ui_history：模板界面版本快照（只追加不更新），三路归并的 base.old 来源；
--      随模板发版同事务写入（模板发版写口补齐前暂由模板资产维护流程手工/脚本写入，见技术方案 §5.3）。
--
-- 治理：本文件为 DDL，属 L3（需架构师审批）；AI 不执行、不自行连库。审批通过后由 DBA / CI 执行。
-- 执行前请先备份；本 SQL 全量 IF NOT EXISTS / information_schema 条件判断，可重复执行幂等。

CREATE TABLE IF NOT EXISTS meta_entity_ui_schema (
  id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增ID（schema 为轻量团队资产，无需雪花业务语义）',
  tenant_id             BIGINT       NOT NULL COMMENT '租户（与 meta_entity 同链路）',
  entity_id             BIGINT       NOT NULL COMMENT 'meta_entity.id（逻辑外键，不建物理 FK，防删实体级联惊动）',
  schema_json           MEDIUMTEXT   NOT NULL COMMENT 'BoneUiSchema 草稿（schemaVersion=1）',
  schema_version        INT          NOT NULL DEFAULT 1 COMMENT '协议大版本（const 1）',
  schema_minor          INT          NOT NULL DEFAULT 1 COMMENT '协议增量版本（版本协商：未知 minor 只告警不拒绝）',
  published_schema_json MEDIUMTEXT   NULL COMMENT '随实体发布打的快照（§发布联动）',
  published_version     INT          NULL COMMENT '快照时的实体 version（对账用）',
  base_template_code    VARCHAR(128) NULL COMMENT '实例化来源模板 code（base 指针）',
  base_version          VARCHAR(32)  NULL COMMENT '实例化时的模板版本',
  follow_mode           VARCHAR(16)  NOT NULL DEFAULT 'pinned' COMMENT 'pinned/notify（永不自动推送）',
  lock_version          BIGINT       NOT NULL DEFAULT 0 COMMENT 'SDK @Version 乐观锁（禁 FOR UPDATE，ADR-0031）',
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  created_by            BIGINT       NULL COMMENT '创建人（AbstractEntity 审计字段，SDK 写路径必填，须与 meta_entity 同列）',
  updated_by            BIGINT       NULL COMMENT '更新人（同上）',
  deleted               TINYINT      NOT NULL DEFAULT 0 COMMENT '0-在用 1-已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_uischema_tenant_entity (tenant_id, entity_id),
  KEY idx_uischema_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务对象界面 schema（团队级，协议驱动渲染）';

-- 模板资产补齐（与 0040 动作展开对称：模板缺 uiSchema 则实例化产出「有形无面」）。
-- expand-only 加可空列；回滚只需删列。MySQL ADD COLUMN 不支持 IF NOT EXISTS，
-- 用 information_schema 条件判断保证重复执行安全（与 0041 同模式）。
SET @ui_col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meta_model_template' AND COLUMN_NAME = 'ui_schema_json'
);
SET @ui_ddl = IF(@ui_col_exists = 0,
  'ALTER TABLE meta_model_template ADD COLUMN ui_schema_json MEDIUMTEXT NULL COMMENT ''模板 baseUiSchema 快照''',
  'SELECT 1'
);
PREPARE ui_stmt FROM @ui_ddl;
EXECUTE ui_stmt;
DEALLOCATE PREPARE ui_stmt;

SET @ui_ver_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meta_model_template' AND COLUMN_NAME = 'ui_schema_version'
);
SET @ui_ver_ddl = IF(@ui_ver_exists = 0,
  'ALTER TABLE meta_model_template ADD COLUMN ui_schema_version VARCHAR(32) NULL COMMENT ''模板界面版本（base_version 来源）''',
  'SELECT 1'
);
PREPARE ui_ver_stmt FROM @ui_ver_ddl;
EXECUTE ui_ver_stmt;
DEALLOCATE PREPARE ui_ver_stmt;

-- 模板界面版本快照（三路归并的 base.old 来源：随模板发版同事务追加写，只增不改，天然审计）。
CREATE TABLE IF NOT EXISTS meta_model_template_ui_history (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  tenant_id     BIGINT       NOT NULL COMMENT '租户（平台模板为 0）',
  template_code VARCHAR(128) NOT NULL COMMENT '模板 code',
  version       VARCHAR(32)  NOT NULL COMMENT '模板界面版本',
  schema_json   MEDIUMTEXT   NOT NULL COMMENT '该版本的 baseUiSchema 全量',
  published_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '快照时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tmpl_ui_hist (tenant_id, template_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板界面版本快照（归并基线）';

-- 存量数据迁移：无（无 schema 的实体走前端回落 projectColumns 渲染，与升级前「仅列投影」等价）。
-- 注意：模板资产完整性（含 ui_schema_json 快照 + 语言包分段）补齐前，跟随入口保持 disabled（详见技术方案 §5.2）。

-- 兼容已建库：meta_entity_ui_schema 可能缺失 created_by / updated_by（AbstractEntity 审计列，
-- SDK 写路径按类 @Column 生成 INSERT，缺列即 BadSqlGrammar）。幂等补列（MySQL 不支持 ADD COLUMN IF NOT EXISTS）。
SET @cb_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meta_entity_ui_schema' AND COLUMN_NAME = 'created_by'
);
SET @cb_ddl = IF(@cb_exists = 0,
  'ALTER TABLE meta_entity_ui_schema ADD COLUMN created_by BIGINT NULL COMMENT ''创建人（AbstractEntity 审计字段）'' AFTER updated_at, ADD COLUMN updated_by BIGINT NULL COMMENT ''更新人'' AFTER created_by',
  'SELECT 1'
);
PREPARE cb_stmt FROM @cb_ddl;
EXECUTE cb_stmt;
DEALLOCATE PREPARE cb_stmt;
