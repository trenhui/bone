-- synap-claim 第二十批 DDL：对账处置流水（§11.7 对账三表之三）
-- 字段真源：《工程详细设计（落地版）》§9.5 reconciliation_action
-- 说明：t_reconciliation_action 为 append-only（仅 INSERT，处置动作不可改写）。
--       实体 ReconciliationAction 与仓储 ReconciliationActionRepository 已存在，此前缺建表脚本，
--       运行期会因「表不存在」失败；本批补齐，与 t_reconciliation_diff 同风格。

CREATE TABLE t_reconciliation_action (
  id           BIGINT       NOT NULL COMMENT '处置流水ID(分布式ID)',
  tenant_id    BIGINT       NOT NULL COMMENT '租户(安全隔离)',
  target_type  VARCHAR(32)  NOT NULL COMMENT '处置目标类型(DIFF/QUOTA_ACCOUNT)',
  target_id    BIGINT       NOT NULL COMMENT '处置目标ID',
  action_type  VARCHAR(32)  NOT NULL COMMENT '动作类型(DIFF_RESOLVE/PROJECTION_REBUILD)',
  resolution   VARCHAR(16)  DEFAULT '' COMMENT '处置结论(RESOLVED/ADJUSTED/WAIVED,仅DIFF_RESOLVE有值)',
  detail       VARCHAR(512) DEFAULT '' COMMENT '处置说明/重算逐列前后值',
  operator_id  VARCHAR(64)  NOT NULL COMMENT '操作人(取自鉴权,不接受请求体自报)',
  occurred_at  DATETIME(3)  NOT NULL COMMENT '动作发生时间',
  created_at   DATETIME(3)  NOT NULL COMMENT '创建时间',
  created_by   BIGINT       DEFAULT NULL COMMENT '创建人ID',
  updated_at   DATETIME(3)  NOT NULL COMMENT '更新时间',
  updated_by   BIGINT       DEFAULT NULL COMMENT '修改人ID',
  deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_target (tenant_id, target_type, target_id),
  KEY idx_occurred (tenant_id, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对账处置流水(append-only)';
