-- wf_* 六表的 H2 测试 schema（与生产 MySQL DDL scripts/migration/0056_wf_workflow_tables.sql 同构）
-- 用途：让 Wf*SdkRepository 的生产 SQL 被 H2 原样执行，避免「测试另写一份 SQL → 测试绿、生产死锁」。
-- 同构口径：deleted/enabled 用 TINYINT（平台布尔列惯例；MySQL 侧写作 TINYINT(1)，H2 2.x 不接受宽度后缀）、
-- 时间列 TIMESTAMP（MySQL 侧为 DATETIME(3)）、
-- 索引集合与生产一致（含对账/轮询/绑定解析所需的二级索引）——列的**类型与索引**都镜像生产，只方言不同。
-- 已知方言差异（有意为之）：definition_json 生产为 MySQL JSON（写入期校验），H2 用 TEXT——损坏 JSON
-- 种子用例（SdkWorkflowDefinitionRepositoryH2Test#corruptedJsonFailsLoudly）需要能写入非法文本；
-- params_json 生产与 H2 均为 TEXT：它实际承载自由文本原因（驳回/挂起理由），JSON 类型会拒掉合法写入。

CREATE TABLE wf_instance (
  id                VARCHAR(64)       NOT NULL PRIMARY KEY,
  tenant_id         BIGINT       NOT NULL,
  entity_code       VARCHAR(128) NOT NULL,
  record_id         VARCHAR(128) NOT NULL,
  def_key           VARCHAR(128) NOT NULL,
  def_version       INT          NOT NULL,
  current_node_code VARCHAR(128) NOT NULL,
  phase             VARCHAR(32)  NOT NULL,
  started_by        VARCHAR(64)  NOT NULL,
  started_at        TIMESTAMP    NOT NULL,
  node_round        INT          NOT NULL DEFAULT 0,
  node_entered_at   TIMESTAMP    NOT NULL,
  reject_rounds     INT          NOT NULL DEFAULT 0,
  updated_at        TIMESTAMP    NOT NULL,
  created_by        VARCHAR(64)  NOT NULL,
  created_at        TIMESTAMP    NOT NULL,
  updated_by        VARCHAR(64),
  deleted           TINYINT   NOT NULL DEFAULT 0,
  version           INT       NOT NULL DEFAULT 0
);
-- findLatestInstanceRowByRecord / 对账窗口扫描都需要按 (租户, 实体, 记录) 定位
CREATE INDEX idx_wf_instance_record ON wf_instance (tenant_id, entity_code, record_id);

CREATE TABLE wf_task (
  id          VARCHAR(64)       NOT NULL PRIMARY KEY,
  tenant_id   BIGINT       NOT NULL,
  instance_id VARCHAR(64)       NOT NULL,
  node_code   VARCHAR(128) NOT NULL,
  assignee_id VARCHAR(64)  NOT NULL,
  status      VARCHAR(32)  NOT NULL,
  decision    VARCHAR(32),
  comment     VARCHAR(1024),
  due_at      TIMESTAMP,
  node_round  INT       NOT NULL DEFAULT 0,
  created_at  TIMESTAMP    NOT NULL,
  updated_at  TIMESTAMP    NOT NULL,
  decided_at  TIMESTAMP,
  created_by  VARCHAR(64)  NOT NULL,
  updated_by  VARCHAR(64),
  deleted     TINYINT   NOT NULL DEFAULT 0,
  version     INT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_wf_task_assignee ON wf_task (tenant_id, assignee_id, status, deleted);
CREATE INDEX idx_wf_task_instance ON wf_task (tenant_id, instance_id, deleted);

-- 活动实例指针：以 (tenant_id, entity_code, record_id) 为主键，是「一单据一活动实例」的原子闸门
-- created_at/updated_at/deleted 为 HC-008 平台通则四列（指针行随实例同事务生灭，无独立生命周期）
CREATE TABLE wf_active_instance (
  tenant_id   BIGINT       NOT NULL,
  entity_code VARCHAR(128) NOT NULL,
  record_id   VARCHAR(128) NOT NULL,
  instance_id VARCHAR(64)       NOT NULL,
  created_at  TIMESTAMP    NOT NULL,
  updated_at  TIMESTAMP    NOT NULL,
  deleted     TINYINT   NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, entity_code, record_id)
);

-- 状态投影发件箱：引擎状态意图先落库，由中继重试落地到业务单据 status
CREATE TABLE wf_status_outbox (
  id           VARCHAR(64)       NOT NULL PRIMARY KEY,
  tenant_id    BIGINT       NOT NULL,
  instance_id  VARCHAR(64)       NOT NULL,
  entity_code  VARCHAR(128) NOT NULL,
  record_id    VARCHAR(128) NOT NULL,
  operator_id  VARCHAR(64)  DEFAULT NULL,
  intent       VARCHAR(32)  NOT NULL,
  action_code  VARCHAR(64),
  params_json  TEXT,
  state        VARCHAR(16)  NOT NULL,
  retry_count  INT          NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMP   NULL,
  created_at   TIMESTAMP    NOT NULL,
  updated_at   TIMESTAMP    NOT NULL,
  deleted      TINYINT   NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_poll ON wf_status_outbox (tenant_id, state, next_retry_at);
-- 对账的 NOT EXISTS 子查询按 (tenant_id, instance_id) 关联；pollDue 队头守卫也按 instance_id 自关联
CREATE INDEX idx_outbox_instance ON wf_status_outbox (tenant_id, instance_id);

CREATE TABLE wf_process_def (
  id              VARCHAR(64)       NOT NULL PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  def_key         VARCHAR(128) NOT NULL,
  def_name        VARCHAR(255) NOT NULL,
  def_version     INT          NOT NULL,
  status          VARCHAR(32)  NOT NULL,
  enabled         TINYINT   NOT NULL DEFAULT 1,
  definition_json TEXT         NOT NULL,
  created_by      VARCHAR(64)  NOT NULL,
  created_at      TIMESTAMP    NOT NULL,
  updated_by      VARCHAR(64),
  updated_at      TIMESTAMP,
  deleted         TINYINT   NOT NULL DEFAULT 0,
  version         INT       NOT NULL DEFAULT 0
);
-- findLatestPublishedRow / listPublishedRows / findByVersionRow 都按 (租户, def_key[, status]) 定位
CREATE INDEX idx_wf_def_key ON wf_process_def (tenant_id, def_key, status);
-- 版本唯一（生产 0056 缺失，设计器 M0 补上）：并发发布时靠它兜底，而不是靠 SELECT MAX+1。
-- 缺它的后果不是「多一条脏数据」——findByVersionRow 会读出两行并在在途实例推进时抛
-- IncorrectResultSizeDataAccessException，错误现场与根因完全脱节。
CREATE UNIQUE INDEX uk_wf_def_ver ON wf_process_def (tenant_id, def_key, def_version);

-- 流程定义草稿（设计器设计态，2026-10-27 新增）：
-- 与 wf_process_def 分表是硬要求——findByVersionRow 只判 deleted、不判 status，
-- 草稿若与发布版共用 (def_key, def_version)，读取侧会读出两行。一 key 一稿靠唯一键保证。
CREATE TABLE wf_process_draft (
  id              VARCHAR(64)  NOT NULL PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  def_key         VARCHAR(128) NOT NULL,
  def_name        VARCHAR(255) NOT NULL,
  definition_json TEXT         NOT NULL,
  revision        INT          NOT NULL DEFAULT 0,
  created_by      VARCHAR(64)  NOT NULL,
  created_at      TIMESTAMP    NOT NULL,
  updated_by      VARCHAR(64),
  updated_at      TIMESTAMP,
  deleted         TINYINT      NOT NULL DEFAULT 0,
  version         INT          NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_wf_draft_key ON wf_process_draft (tenant_id, def_key);

-- 业务实体 → 流程定义绑定：entity_code 运营侧解析成 def_key（换流程改配置不发版，设计文档 §5.5）
-- v1 只读（写路径随建模界面排期）；查询以 (entity_code, tenant_id) 期望唯一命中——
-- 同一实体配了多条生效绑定时 findBindingRow 抛 TooManyResults（fail-fast，不静默挑一条）。
CREATE TABLE wf_binding (
  id           VARCHAR(64)  NOT NULL PRIMARY KEY,
  tenant_id    BIGINT       NOT NULL,
  entity_code  VARCHAR(128) NOT NULL,
  def_key      VARCHAR(128) NOT NULL,
  start_action VARCHAR(64),
  enabled      TINYINT   NOT NULL DEFAULT 1,
  created_by   VARCHAR(64)  NOT NULL,
  created_at   TIMESTAMP    NOT NULL,
  updated_by   VARCHAR(64),
  updated_at   TIMESTAMP,
  deleted      TINYINT   NOT NULL DEFAULT 0,
  version      INT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_wf_binding_entity ON wf_binding (tenant_id, entity_code);

-- 站内信收件箱：审批流通知落库（与生产 wf_notification 同构，仅方言差异）。
-- 见 scripts/migration/0056_wf_workflow_tables.sql 的建表注释与索引口径。
CREATE TABLE wf_notification (
  id           VARCHAR(64)  NOT NULL PRIMARY KEY,
  tenant_id    BIGINT       NOT NULL,
  recipient_id VARCHAR(64)  NOT NULL,
  type         VARCHAR(32)  NOT NULL,
  instance_id  VARCHAR(64),
  entity_code  VARCHAR(128),
  record_id    VARCHAR(128),
  node_code    VARCHAR(128),
  title        VARCHAR(255) NOT NULL,
  body         VARCHAR(1024),
  read_flag    TINYINT   NOT NULL DEFAULT 0,
  created_at   TIMESTAMP    NOT NULL,
  updated_at   TIMESTAMP    NOT NULL,
  created_by   VARCHAR(64)  NOT NULL,
  updated_by   VARCHAR(64),
  deleted      TINYINT   NOT NULL DEFAULT 0,
  version      INT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_wf_notification_inbox ON wf_notification (tenant_id, recipient_id, read_flag, deleted);
CREATE INDEX idx_wf_notification_instance ON wf_notification (tenant_id, instance_id);
