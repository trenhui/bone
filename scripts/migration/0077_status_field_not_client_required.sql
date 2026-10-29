-- =============================================================================
-- 0077_status_field_not_client_required.sql
--
-- 目的：修「状态机实体建单必失败」——`status` 是状态机独占字段（运行时禁止经 POST 直写），
--       但它同时被标成 is_required=1，于是客户端无论传不传都过不去：
--         · 不传 → 400「字段「status」为必填」
--         · 传了 → 422「status 为状态机独占字段，禁止经 POST 直写」
--       结果就是费用报销单在**种子实体上根本新建不出来**（e2e 用的是动态建模实体，字段由脚本自己声明，
--       掩盖了这条真缺陷；界面上用户点「新增」即 400）。
--
-- 语义澄清：状态机实体的初始状态由**列默认值 + 状态机**决定，不属于「用户必填输入」。
--       本脚本把所有「有动作定义（即接入了状态机）」的实体的 status 字段改为非必填 +
--       补列默认 'DRAFT'（列默认已存在则 UPDATE 无副作用），让建单回到「先落 DRAFT，再由动作推进」。
--
-- 幂等：两条 UPDATE 均为条件更新，可重复执行。
-- ⚠ L3 待审批：元数据字段约束变更，须架构师审批后由 DBA/CI 执行。
-- =============================================================================

USE bone;

-- ── 1. meta_field：状态机实体的 status 不再是客户端必填 ─────────────────────
UPDATE meta_field f
  JOIN meta_entity e ON e.id = f.entity_id AND e.deleted = 0
   SET f.is_required = 0,
       f.default_value = COALESCE(NULLIF(f.default_value, ''), 'DRAFT'),
       f.updated_at = NOW(3),
       f.version = f.version + 1
 WHERE f.deleted = 0
   AND f.code = 'status'
   AND (f.is_required = 1 OR f.default_value IS NULL OR f.default_value = '')
   AND EXISTS (
       -- 显式对齐排序规则：meta_entity_action_def 是 utf8mb4_0900_ai_ci，meta_entity 是 utf8mb4_unicode_ci，
       -- 直接比较会 1267 Illegal mix of collations（本脚本首跑即踩）。
       SELECT 1 FROM meta_entity_action_def d
        WHERE d.entity_code = e.code COLLATE utf8mb4_0900_ai_ci AND d.deleted = 0
   );

-- ── 2. 已建物理表的列默认值（列默认是运行时 INSERT 真正吃的那个）────────────────
-- 只处理种子实体的固定表名；动态建模实体的表名带后缀，由平台 DDL 同步负责（那里会读 meta_field 默认值）。
UPDATE t_expense_reimbursement SET status = 'DRAFT' WHERE status IS NULL OR status = '';
ALTER TABLE t_expense_reimbursement
    MODIFY COLUMN status varchar(20) NOT NULL DEFAULT 'DRAFT'
    COMMENT '状态机（meta_entity_action_def 的 from/to_status 驱动）';

-- ── 3. 校验 ─────────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM meta_field f
     JOIN meta_entity e ON e.id = f.entity_id AND e.deleted = 0
    WHERE f.deleted = 0 AND f.code = 'status' AND f.is_required = 1
      AND EXISTS (SELECT 1 FROM meta_entity_action_def d
                   WHERE d.entity_code = e.code COLLATE utf8mb4_0900_ai_ci AND d.deleted = 0))
    AS still_client_required_status,
  (SELECT GROUP_CONCAT(CONCAT(e.code, ':', f.is_required, ':', IFNULL(f.default_value, 'NULL')) ORDER BY e.code)
     FROM meta_field f
     JOIN meta_entity e ON e.id = f.entity_id AND e.deleted = 0
    WHERE f.deleted = 0 AND f.code = 'status' AND e.code IN ('expense_reimbursement', 'purchase_order'))
    AS seed_status_fields;
