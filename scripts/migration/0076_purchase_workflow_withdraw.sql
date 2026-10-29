-- =============================================================================
-- 0076_purchase_workflow_withdraw.sql
--
-- 目的（采购审批流接入 bone-workflow-engine · 补齐段）：
--   ① 采购「撤回」动作：补 meta_entity_action_def.withdraw（SUBMITTED→DRAFT，逃逸 →
--      purchaseWorkflowWithdrawHandler）。接入前采购只有 submit/approve/reject 三个逃逸动作，
--      单据一旦提交就<b>只有审批人能推动</b>：金额填错、附件贴错、预算科目选错时，发起人只能求审批人驳回，
--      而驳回是 REJECTED 终态——改完得重新建单。撤回把纠错权还给发起人（作废待办 + 实例 WITHDRAWN + 释放活动槽位）。
--   ② 采购动作级权限码登记：meta_entity_action_def.permission_code 已引用
--      runtime:purchase_order:{submit,approve,reject,startReceive,confirmReceive,pay}，但 iam_permission 里
--      一条都没有——超管靠绕过可见，真实采购员/审批人却会被 403 挡在门外（「配置了不生效」的典型形态）。
--      本脚本按报销 0057 §4 的口径把六个码补登记并授权。
--
-- 分工（勿重复造表）：wf_* 六表由 0056、采购流程定义与绑定由 0068 负责；本脚本只补动作与权限，幂等可重跑。
--
-- ⚠ L3 待审批：动作定义与权限授权属审批数据，须架构师审批后由 DBA/CI 执行。
-- ⚠ 前置：0056（wf 表）、0068（采购定义 + 绑定）、0075（逃逸幂等）已执行。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0076_purchase_workflow_withdraw.sql
-- =============================================================================

USE bone;

-- ── 1. withdraw 动作定义（实例层，按「存在 purchase_order 实体」的租户展开）─────
--    id 由自增列生成（meta_entity_action_def.id 是 auto_increment，勿硬编码）。
--    requires_idempotency = 0：撤回是「把流程停掉」的幂等语义动作，重放第二次会被引擎
--    以 CONFLICT 拒绝并翻译成 409（而非把业务状态推进两次），不属逃逸副作用需幂等的范畴。
INSERT INTO meta_entity_action_def
    (tenant_id, entity_code, action_code, label, from_status, to_status, status_field,
     is_escape, handler_bean, params_schema, permission_code, requires_idempotency,
     sort_order, is_entry, created_at, updated_at, deleted, version)
SELECT
    e.tenant_id, 'purchase_order', 'withdraw', '撤回', 'SUBMITTED', 'DRAFT', 'status',
    1, 'purchaseWorkflowWithdrawHandler', NULL, 'runtime:purchase_order:withdraw', 0,
    4, 0, NOW(3), NOW(3), 0, 0
  FROM meta_entity e
 WHERE e.code = 'purchase_order' AND e.deleted = 0
   AND NOT EXISTS (
       SELECT 1 FROM meta_entity_action_def d
        WHERE d.tenant_id = e.tenant_id AND d.entity_code = 'purchase_order'
          AND d.action_code = 'withdraw' AND d.deleted = 0
   );

-- sort_order 让位：撤回排在「驳回」之后、「开始收货」之前（原 4/5/6 顺延）
UPDATE meta_entity_action_def
   SET sort_order = 5, updated_at = NOW(3), version = version + 1
 WHERE entity_code = 'purchase_order' AND deleted = 0 AND action_code = 'startReceive';
UPDATE meta_entity_action_def
   SET sort_order = 6, updated_at = NOW(3), version = version + 1
 WHERE entity_code = 'purchase_order' AND deleted = 0 AND action_code = 'confirmReceive';
UPDATE meta_entity_action_def
   SET sort_order = 7, updated_at = NOW(3), version = version + 1
 WHERE entity_code = 'purchase_order' AND deleted = 0 AND action_code = 'pay';

-- ── 1b. status 字段默认值补齐（否则新建采购单 status 为 NULL，任何动作都 409）──────
-- 实测：purchase_order.status 的 default_value 为空（报销 expense_reimbursement.status 为 'DRAFT'），
-- 于是 POST /records 建出来的单子 status=NULL，submit 直接以「要求 DRAFT，实际为 null」409 拒绝，
-- 采购审批链在真实种子实体上根本走不起来（e2e 用的是动态建模实体，自带 DRAFT 默认值，掩盖了这一点）。
UPDATE meta_field
   SET default_value = 'DRAFT', updated_at = NOW(3), version = version + 1
 WHERE deleted = 0
   AND code = 'status'
   AND (default_value IS NULL OR default_value = '')
   AND entity_id IN (SELECT id FROM meta_entity WHERE code = 'purchase_order' AND deleted = 0);

-- ── 1c. status 物理列默认值（元数据默认值只影响表单回填；建单 INSERT 不带该列时吃的是列默认）──
-- 实测：t_purchase_order.status 是 nullable 无默认（t_expense_reimbursement.status 为 NOT NULL DEFAULT 'DRAFT'），
-- 只改 meta_field 不足以让运行时建单拿到 DRAFT —— 列默认必须一起补，否则单据永远「无状态」，任何动作 409。
UPDATE t_purchase_order SET status = 'DRAFT' WHERE status IS NULL OR status = '';
ALTER TABLE t_purchase_order
    MODIFY COLUMN status varchar(255) NOT NULL DEFAULT 'DRAFT'
    COMMENT '状态机（meta_entity_action_def 的 from/to_status 驱动）';

-- ── 2. 采购动作级权限码登记（6 个既有码 + 1 个新码）──────────────────────────
-- resource_path 与报销 0033/0057 同构：/api/v1/runtime/entities/<entity>/records/*/actions/<action>
INSERT INTO iam_permission
    (id, code, name, type, resource_type, resource_path, action, description,
     parent_id, sort_order, created_by, created_at, updated_at, deleted)
VALUES
    (9130, 'runtime:purchase_order:submit', '运行时-采购-提交', 'OPERATION', 'metadata',
     '/api/v1/runtime/entities/purchase_order/records/*/actions/submit', 'execute',
     '采购订单：提交审批（DRAFT→SUBMITTED，逃逸动作启动采购审批流）', 58, 130, 1, NOW(3), NOW(3), 0),
    (9131, 'runtime:purchase_order:approve', '运行时-采购-审批通过', 'OPERATION', 'metadata',
     '/api/v1/runtime/entities/purchase_order/records/*/actions/approve', 'execute',
     '采购订单：审批通过（SUBMITTED→APPROVED，须为当前节点审批人）', 58, 131, 1, NOW(3), NOW(3), 0),
    (9132, 'runtime:purchase_order:reject', '运行时-采购-驳回', 'OPERATION', 'metadata',
     '/api/v1/runtime/entities/purchase_order/records/*/actions/reject', 'execute',
     '采购订单：驳回（SUBMITTED→REJECTED，可退回指定节点）', 58, 132, 1, NOW(3), NOW(3), 0),
    (9133, 'runtime:purchase_order:withdraw', '运行时-采购-撤回', 'OPERATION', 'metadata',
     '/api/v1/runtime/entities/purchase_order/records/*/actions/withdraw', 'execute',
     '采购订单：撤回（SUBMITTED→DRAFT，仅发起人；逃逸动作终结审批流）', 58, 133, 1, NOW(3), NOW(3), 0),
    (9134, 'runtime:purchase_order:startReceive', '运行时-采购-开始收货', 'OPERATION', 'metadata',
     '/api/v1/runtime/entities/purchase_order/records/*/actions/startReceive', 'execute',
     '采购订单：开始收货（APPROVED→RECEIVING）', 58, 134, 1, NOW(3), NOW(3), 0),
    (9135, 'runtime:purchase_order:confirmReceive', '运行时-采购-确认收货', 'OPERATION', 'metadata',
     '/api/v1/runtime/entities/purchase_order/records/*/actions/confirmReceive', 'execute',
     '采购订单：确认收货（RECEIVING→RECEIVED）', 58, 135, 1, NOW(3), NOW(3), 0),
    (9136, 'runtime:purchase_order:pay', '运行时-采购-付款', 'OPERATION', 'metadata',
     '/api/v1/runtime/entities/purchase_order/records/*/actions/pay', 'execute',
     '采购订单：付款（RECEIVED→PAID，三单匹配 + 幂等键强制）', 58, 136, 1, NOW(3), NOW(3), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), resource_path = VALUES(resource_path), action = VALUES(action),
    description = VALUES(description), sort_order = VALUES(sort_order), updated_at = NOW(3);

-- ── 3. 授权：SUPER_ADMIN(1) / demo 租户总管(121001) / demo 管理员(3，若存在) ──
-- 授权 id 不硬编码（既有手工 9xxx 段与雪花 id 并存，硬编码必撞）；取 MAX(id) 派生 + ROW_NUMBER 递增。
-- ⚠ 不能写成「每行都取 (SELECT MAX(id)+1)」：同一语句内 MAX 不变，多行必然撞主键（本脚本首跑即踩）。
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT t.mx + ROW_NUMBER() OVER (ORDER BY t.role_id, t.permission_id), t.role_id, t.permission_id, NOW(3)
  FROM (
        SELECT (SELECT COALESCE(MAX(id), 9000) FROM iam_role_permission) AS mx,
               r.role_id AS role_id,
               p.id AS permission_id
          FROM iam_permission p
          JOIN (SELECT 1 AS role_id UNION ALL SELECT 3 UNION ALL SELECT 121001) r
            ON EXISTS (SELECT 1 FROM iam_role i WHERE i.id = r.role_id AND i.deleted = 0)
         WHERE p.code LIKE 'runtime:purchase_order:%' AND p.deleted = 0
           AND NOT EXISTS (
               SELECT 1 FROM iam_role_permission rp
                WHERE rp.role_id = r.role_id AND rp.permission_id = p.id
           )
       ) t;

-- ── 4. 校验（任一为 0 即脚本未达预期）────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM meta_entity_action_def
     WHERE entity_code = 'purchase_order' AND action_code = 'withdraw'
       AND is_escape = 1 AND handler_bean = 'purchaseWorkflowWithdrawHandler' AND deleted = 0)
    AS withdraw_action_wired,
  (SELECT COUNT(*) FROM iam_permission
     WHERE deleted = 0 AND code LIKE 'runtime:purchase_order:%') AS purchase_permission_count,
  (SELECT COUNT(*) FROM iam_role_permission rp
     JOIN iam_permission p ON p.id = rp.permission_id
    WHERE p.deleted = 0 AND p.code LIKE 'runtime:purchase_order:%') AS purchase_permission_grant_count,
  (SELECT COUNT(*) FROM iam_role_permission rp
     JOIN iam_permission p ON p.id = rp.permission_id
    WHERE p.code = 'runtime:purchase_order:withdraw') AS withdraw_grant_count,
  (SELECT GROUP_CONCAT(CONCAT(action_code, ':', sort_order) ORDER BY sort_order)
     FROM meta_entity_action_def WHERE entity_code = 'purchase_order' AND deleted = 0) AS action_order;
