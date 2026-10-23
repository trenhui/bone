-- wf_* 三表的 H2 测试 schema（与生产 MySQL DDL 草案同构）
-- 用途：让 JdbcWorkflowInstanceRepository 的原子 SQL 被 H2 原样执行，避免「测试另写一份 SQL → 测试绿、生产死锁」。

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
  updated_at        TIMESTAMP    NOT NULL,
  created_by        VARCHAR(64)  NOT NULL,
  created_at        TIMESTAMP    NOT NULL,
  updated_by        VARCHAR(64),
  deleted           INT          NOT NULL DEFAULT 0,
  version           INT       NOT NULL DEFAULT 0
);

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
  created_at  TIMESTAMP    NOT NULL,
  updated_at  TIMESTAMP    NOT NULL,
  decided_at  TIMESTAMP,
  created_by  VARCHAR(64)  NOT NULL,
  updated_by  VARCHAR(64),
  deleted     INT          NOT NULL DEFAULT 0,
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
  deleted     INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, entity_code, record_id)
);

-- 状态投影发件箱：引擎状态意图先落库，由中继重试落地到业务单据 status
CREATE TABLE wf_status_outbox (
  id           VARCHAR(64)       NOT NULL PRIMARY KEY,
  tenant_id    BIGINT       NOT NULL,
  instance_id  VARCHAR(64)       NOT NULL,
  entity_code  VARCHAR(128) NOT NULL,
  record_id    VARCHAR(128) NOT NULL,
  intent       VARCHAR(32)  NOT NULL,
  action_code  VARCHAR(64),
  params_json  TEXT,
  state        VARCHAR(16)  NOT NULL,
  retry_count  INT          NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMP   NULL,
  created_at   TIMESTAMP    NOT NULL,
  updated_at   TIMESTAMP    NOT NULL,
  deleted      INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_poll ON wf_status_outbox (tenant_id, state, next_retry_at);

CREATE TABLE wf_process_def (
  id              VARCHAR(64)       NOT NULL PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  def_key         VARCHAR(128) NOT NULL,
  def_name        VARCHAR(255) NOT NULL,
  def_version     INT          NOT NULL,
  status          VARCHAR(32)  NOT NULL,
  enabled         INT          NOT NULL DEFAULT 1,
  definition_json TEXT         NOT NULL,
  created_by      VARCHAR(64)  NOT NULL,
  created_at      TIMESTAMP    NOT NULL,
  updated_by      VARCHAR(64),
  updated_at      TIMESTAMP,
  deleted         INT          NOT NULL DEFAULT 0,
  version         INT       NOT NULL DEFAULT 0
);
