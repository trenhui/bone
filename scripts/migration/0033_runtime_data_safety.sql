-- =============================================================================
-- 0033_runtime_data_safety.sql
--
-- 目的（低代码 Runtime 数据面设计 §0.1 Must-1「数据安全」+ §8 判据 R11/R12/R13）：
--   ① RC-009 记录级变更审计表（meta_runtime_record_audit）——「谁把哪张单据从
--      APPROVED 改成 PAID」必须可回溯；
--   ② RC-004 实体级权限码 runtime:{entityCode}:{read|write} + 通配 runtime:*:{read|write}
--      ——现状 metadata:runtime:write = 租户内全库 CRUD，必须可按实体收回；
--   ③ 动作级权限码 runtime:expense_claim:{action}（逃逸动作上线门禁 §4.6）。
--
-- 设计
-- ----------------------
-- · 审计表 append-only：只 INSERT，不提供 UPDATE/DELETE 通道（可回溯性优先）。
-- · 权限码按 iam_permission 既有模式注册（参照 0024 / bone-init.sql 的 metadata:* 行）。
-- · 通配码 runtime:*:read|write 语义 = 旧粗粒度码（全实体 CRUD）；形状统一后，
--   「无实体级权限 → 403」在生产才可能成立：只需把 wildcard 换发成具体实体码。
-- · 幂等：按 code 唯一键 ON DUPLICATE KEY UPDATE，可重复执行。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0033_runtime_data_safety.sql
-- 前置：0032_runtime_action_spi.sql（动作定义表）已执行。
-- =============================================================================

USE bone;

-- ── 1. 记录级变更审计（RC-009 · append-only） ───────────────────────────────
CREATE TABLE IF NOT EXISTS meta_runtime_record_audit (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL DEFAULT 0,
  entity_code   VARCHAR(128) NOT NULL,
  record_id     VARCHAR(64)  NOT NULL,
  field_code    VARCHAR(128) NOT NULL,
  from_value    VARCHAR(512) DEFAULT NULL,
  to_value      VARCHAR(512) DEFAULT NULL,
  operator_id   VARCHAR(64)  DEFAULT NULL,
  reason        VARCHAR(512) DEFAULT NULL,
  source        VARCHAR(32)  NOT NULL DEFAULT 'action' COMMENT 'action|api|admin-fix|delete|restore',
  action_code   VARCHAR(128) DEFAULT NULL,
  trace_id      VARCHAR(64)  DEFAULT NULL,
  created_at    DATETIME(3)  DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_tenant_entity_record (tenant_id, entity_code, record_id),
  KEY idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行时记录级变更审计（RC-009，append-only）';

-- ── 2. 权限码：实体级通配 + 报销实体细码 ───────────────────────────────────
-- 注：iam_permission.code 唯一；与既有 metadata:* 行同域（resource_type=metadata）。
INSERT INTO iam_permission
    (id, code, name, type, resource_type, resource_path, action, description, parent_id, sort_order, created_by, created_at, updated_at, deleted)
VALUES
    -- 通配（等价旧粗粒度码，形状统一到实体级命名空间）
    (9101, 'runtime:*:read',  '运行时-全实体读',   'OPERATION', 'metadata', '/api/v1/runtime/**', 'read',  '通配：全部低代码实体的读权限（等价旧 metadata:runtime:read）', 58, 101, 1, NOW(3), NOW(3), 0),
    (9102, 'runtime:*:write', '运行时-全实体写',   'OPERATION', 'metadata', '/api/v1/runtime/**', 'write', '通配：全部低代码实体的写权限（等价旧 metadata:runtime:write）', 58, 102, 1, NOW(3), NOW(3), 0),
    -- 报销实体（expense_claim）实体级
    (9111, 'runtime:expense_claim:read',  '运行时-费用报销-读', 'OPERATION', 'metadata', '/api/v1/runtime/entities/expense_claim/**', 'read',  '费用报销实体的运行时读权限', 58, 111, 1, NOW(3), NOW(3), 0),
    (9112, 'runtime:expense_claim:write', '运行时-费用报销-写', 'OPERATION', 'metadata', '/api/v1/runtime/entities/expense_claim/**', 'write', '费用报销实体的运行时写权限', 58, 112, 1, NOW(3), NOW(3), 0),
    -- 动作级（逃逸 confirmPayout 上线门禁：不得仅靠实体级 write）
    (9121, 'runtime:expense_claim:submit',        '运行时-报销-提交',     'OPERATION', 'metadata', '/api/v1/runtime/entities/expense_claim/records/*/actions/submit',        'execute', '费用报销：提交（DRAFT→SUBMITTED）', 58, 121, 1, NOW(3), NOW(3), 0),
    (9122, 'runtime:expense_claim:approve',       '运行时-报销-审批通过', 'OPERATION', 'metadata', '/api/v1/runtime/entities/expense_claim/records/*/actions/approve',       'execute', '费用报销：审批通过（SUBMITTED→APPROVED）', 58, 122, 1, NOW(3), NOW(3), 0),
    (9123, 'runtime:expense_claim:reject',        '运行时-报销-驳回',     'OPERATION', 'metadata', '/api/v1/runtime/entities/expense_claim/records/*/actions/reject',        'execute', '费用报销：驳回（SUBMITTED→REJECTED）', 58, 123, 1, NOW(3), NOW(3), 0),
    (9124, 'runtime:expense_claim:confirmPayout', '运行时-报销-确认打款', 'OPERATION', 'metadata', '/api/v1/runtime/entities/expense_claim/records/*/actions/confirmPayout', 'execute', '费用报销：确认打款（APPROVED→PAID，资金类需 Idempotency-Key）', 58, 124, 1, NOW(3), NOW(3), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), resource_path = VALUES(resource_path), action = VALUES(action),
    description = VALUES(description), sort_order = VALUES(sort_order), updated_at = NOW(3);

-- ── 3. 校验 ────────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = 'bone' AND table_name = 'meta_runtime_record_audit') AS audit_table_created,
  (SELECT COUNT(*) FROM iam_permission WHERE deleted = 0 AND code LIKE 'runtime:%') AS runtime_permission_count,
  (SELECT COUNT(*) FROM iam_permission WHERE deleted = 0 AND code LIKE 'runtime:expense_claim:%') AS expense_permission_count;
