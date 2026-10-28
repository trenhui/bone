-- =============================================================================
-- 0069_seed_expense_runtime_platform_tenant.sql
--
-- 目的（2026-10-28）：让**平台租户 0** 也有可用的「费用报销」运行时实体。
--
-- 为什么必须（不是凑数）
-- -----------------
-- · 报销菜单用 /metadata#/entities/{实体ID}/data 指向实体自己的运行时页面（RuntimeDataManagement
--   嵌入模式），实体必须与**登录租户同租户**：运行时 API 按 JWT / X-Tenant-Id 解析实体，
--   租户不匹配即 404/空集 ⇒ 菜单点了打不开。
-- · 平台管理员（admin，tenantId=0）登录后，IAM `menus/current` 下发的是**租户 0**的菜单
--   （MenuApplicationService#current 按上下文租户过滤；实测带 ?tenantId=1001 也不会切成 1001 的菜单）。
--   只给 DEMO 租户 1001 播菜单 ⇒ 平台管理员看不到「报销系统」。0064（智能理赔）、
--   0067（工作流设计器）同样是 0 + 1001 双租户播的，同一原因。
-- · 0037 只播了 tenant 1001。本脚本按 **code 幂等**把 0037 的定义补齐到 tenant 0，不手写第二份定义。
--
-- 与 0037 的对应关系
-- -----------------
--   expense_reimbursement        报销单（头）      ← 0037:9210 的定义
--   expense_category             费用类别（主数据） ← 0037:9220
--   expense_reimbursement_item   报销明细（子表）   ← 0037:9230
-- · 物理表 **共用**（t_expense_reimbursement / _item / t_expense_category）：
--   runtime 按 tenant_id 列分区，同表多实体是既有形态。
-- · 租户 0 若已存在同名 code 的历史行（如 2026-10-10 由 L0 模板 9150 实例化后被软删的报销单），
--   本脚本**复用该行**（uk_meta_e_code = tenant_id+code 不允许并存两行），复议启用并补齐
--   L1 字段；同时退役其被 L1 取代的遗留 `category`(ENUM) 字段 —— 0037 头注释已声明该演进。
--
-- 前置：0037_runtime_expense_l1_relations.sql 已执行（提供 tenant 1001 模板 + 物理表）。
--       缺失 ⇒ 本脚本 SIGNAL 失败，绝不静默插 0 行。
--
-- 幂等：可重复执行（实体/字段按 uk upsert，关系按主键 upsert，类别按 uk upsert）。
--
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0069_...sql
-- =============================================================================

USE bone;

-- ---------- 阶段 0：前置护栏 ----------
DROP PROCEDURE IF EXISTS sp_guard_0069;
DELIMITER $$
CREATE PROCEDURE sp_guard_0069()
BEGIN
    DECLARE v_tpl INT DEFAULT 0;
    SELECT COUNT(*) INTO v_tpl FROM meta_entity
     WHERE tenant_id = 1001 AND id IN (9210, 9220, 9230) AND deleted = 0;
    IF v_tpl <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0069 前置缺失：0037 的 tenant 1001 报销实体模板不存在，请先执行 0037';
    END IF;
END$$
DELIMITER ;
CALL sp_guard_0069();
DROP PROCEDURE IF EXISTS sp_guard_0069;

-- ---------- 阶段 1：清理历史半成品（实体不存在的字段 / 悬空关系） ----------
-- 说明：只清理「父实体已不存在」的孤儿行，不动任何有效数据。
DELETE f FROM meta_field f
 WHERE NOT EXISTS (SELECT 1 FROM meta_entity e WHERE e.id = f.entity_id);

DELETE r FROM meta_entity_relation r
 WHERE NOT EXISTS (SELECT 1 FROM meta_entity e WHERE e.id = r.source_entity_id)
    OR NOT EXISTS (SELECT 1 FROM meta_entity e WHERE e.id = r.target_entity_id);

-- ---------- 阶段 2：实体（tenant 0，按 code upsert；module_id 置 NULL：平台实体不挂租户建模模块） ----------
INSERT INTO meta_entity
    (id, tenant_id, name, code, display_name, description, table_name, type, delivery_mode, status,
     module_id, scope, template_id, is_builtin, icon, sort_order, created_by, updated_by,
     created_at, updated_at, deleted, version)
SELECT
    e.id + 30, 0, e.name, e.code, e.display_name, e.description, e.table_name, e.type,
    e.delivery_mode, e.status, NULL, e.scope, e.template_id, e.is_builtin, e.icon, e.sort_order,
    e.created_by, e.updated_by, NOW(3), NOW(3), 0, 0
FROM meta_entity e
WHERE e.tenant_id = 1001 AND e.id IN (9210, 9220, 9230) AND e.deleted = 0
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), description = VALUES(description),
    table_name = VALUES(table_name), delivery_mode = VALUES(delivery_mode), status = VALUES(status),
    deleted = 0, updated_at = NOW(3);

-- ---------- 阶段 3：实体映射（模板 → 平台租户实际行） ----------
DROP TABLE IF EXISTS tmp_map_0069;
CREATE TABLE tmp_map_0069 (
    tpl_id   BIGINT NOT NULL PRIMARY KEY,
    cur_id   BIGINT NOT NULL,
    code     VARCHAR(200) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_map_0069 (tpl_id, cur_id, code)
SELECT tpl.id, cur.id, tpl.code
  FROM meta_entity tpl
  JOIN meta_entity cur ON cur.tenant_id = 0 AND cur.code = tpl.code AND cur.deleted = 0
 WHERE tpl.tenant_id = 1001 AND tpl.id IN (9210, 9220, 9230) AND tpl.deleted = 0;

-- ---------- 阶段 4：字段（按 (实体, code) upsert：历史 L0 字段同步升级为 L1 定义） ----------
INSERT INTO meta_field
    (id, tenant_id, entity_id, module_id, name, code, display_name, type, length, numeric_precision,
     is_required, is_unique, is_pk, is_indexed, default_value, enum_values, validation_rules, comment,
     data_classification, is_pii, sensitivity_level, data_steward, business_term, source_system,
     physical_column, sort_order, created_by, updated_by, created_at, updated_at, deleted, version)
SELECT
    f.id + 300, 0, m.cur_id, NULL, f.name, f.code, f.display_name, f.type, f.length,
    f.numeric_precision, f.is_required, f.is_unique, f.is_pk, f.is_indexed, f.default_value,
    f.enum_values, f.validation_rules, f.comment, f.data_classification, f.is_pii,
    f.sensitivity_level, f.data_steward, f.business_term, f.source_system, f.physical_column,
    f.sort_order, f.created_by, f.updated_by, NOW(3), NOW(3), 0, 0
FROM meta_field f
JOIN tmp_map_0069 m ON m.tpl_id = f.entity_id
WHERE f.deleted = 0
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), type = VALUES(type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique),
    default_value = VALUES(default_value), enum_values = VALUES(enum_values),
    validation_rules = VALUES(validation_rules), physical_column = VALUES(physical_column),
    sort_order = VALUES(sort_order), deleted = 0, updated_at = NOW(3);

-- ---------- 阶段 5：退役被 L1 取代的遗留字段（tenant 0 历史 L0 行上的 category ENUM） ----------
-- 0037 已把「消费类别」从 ENUM 码表演进为 REFERENCE → expense_category，残留 ENUM 列会让运行时
-- 列表出现两个「类别」，故软删（保留行以便追溯，不物理删除）。
UPDATE meta_field f
  JOIN tmp_map_0069 m ON m.cur_id = f.entity_id
   SET f.deleted = 1, f.updated_at = NOW(3)
 WHERE m.code = 'expense_reimbursement'
   AND f.code = 'category'
   AND UPPER(f.type) = 'ENUM'
   AND f.deleted = 0;

-- ---------- 阶段 6：建模期关系（按主键 upsert，实体/字段 id 现查） ----------
INSERT INTO meta_entity_relation
    (id, tenant_id, name, source_entity_id, target_entity_id, type, source_field_id, target_field_id,
     foreign_key_field, is_required, cascade_type, created_by, updated_by, created_at, updated_at, deleted, version)
SELECT
    9601, 0, '报销单-明细行',
    (SELECT id FROM meta_entity WHERE tenant_id = 0 AND code = 'expense_reimbursement' AND deleted = 0 LIMIT 1),
    (SELECT id FROM meta_entity WHERE tenant_id = 0 AND code = 'expense_reimbursement_item' AND deleted = 0 LIMIT 1),
    'OneToMany',
    (SELECT f.id FROM meta_field f
       JOIN meta_entity e ON e.id = f.entity_id AND e.tenant_id = 0 AND e.code = 'expense_reimbursement'
      WHERE f.code = 'items' AND f.deleted = 0 LIMIT 1),
    NULL, 'claim_id', 0, 'SOFT_DELETE', 1, 1, NOW(3), NOW(3), 0, 0
ON DUPLICATE KEY UPDATE
    source_entity_id = VALUES(source_entity_id), target_entity_id = VALUES(target_entity_id),
    source_field_id = VALUES(source_field_id), foreign_key_field = VALUES(foreign_key_field),
    deleted = 0, updated_at = NOW(3);

INSERT INTO meta_entity_relation
    (id, tenant_id, name, source_entity_id, target_entity_id, type, source_field_id, target_field_id,
     foreign_key_field, is_required, cascade_type, created_by, updated_by, created_at, updated_at, deleted, version)
SELECT
    9602, 0, '明细行-类别',
    (SELECT id FROM meta_entity WHERE tenant_id = 0 AND code = 'expense_reimbursement_item' AND deleted = 0 LIMIT 1),
    (SELECT id FROM meta_entity WHERE tenant_id = 0 AND code = 'expense_category' AND deleted = 0 LIMIT 1),
    'ManyToOne',
    (SELECT f.id FROM meta_field f
       JOIN meta_entity e ON e.id = f.entity_id AND e.tenant_id = 0 AND e.code = 'expense_reimbursement_item'
      WHERE f.code = 'category_id' AND f.deleted = 0 LIMIT 1),
    NULL, 'category_id', 0, NULL, 1, 1, NOW(3), NOW(3), 0, 0
ON DUPLICATE KEY UPDATE
    source_entity_id = VALUES(source_entity_id), target_entity_id = VALUES(target_entity_id),
    source_field_id = VALUES(source_field_id), foreign_key_field = VALUES(foreign_key_field),
    deleted = 0, updated_at = NOW(3);

INSERT INTO meta_entity_relation
    (id, tenant_id, name, source_entity_id, target_entity_id, type, source_field_id, target_field_id,
     foreign_key_field, is_required, cascade_type, created_by, updated_by, created_at, updated_at, deleted, version)
SELECT
    9603, 0, '报销单-类别',
    (SELECT id FROM meta_entity WHERE tenant_id = 0 AND code = 'expense_reimbursement' AND deleted = 0 LIMIT 1),
    (SELECT id FROM meta_entity WHERE tenant_id = 0 AND code = 'expense_category' AND deleted = 0 LIMIT 1),
    'ManyToOne',
    (SELECT f.id FROM meta_field f
       JOIN meta_entity e ON e.id = f.entity_id AND e.tenant_id = 0 AND e.code = 'expense_reimbursement'
      WHERE f.code = 'category_id' AND f.deleted = 0 LIMIT 1),
    NULL, 'category_id', 0, NULL, 1, 1, NOW(3), NOW(3), 0, 0
ON DUPLICATE KEY UPDATE
    source_entity_id = VALUES(source_entity_id), target_entity_id = VALUES(target_entity_id),
    source_field_id = VALUES(source_field_id), foreign_key_field = VALUES(foreign_key_field),
    deleted = 0, updated_at = NOW(3);

-- ---------- 阶段 7：类别主数据（tenant 0，与物理表共用） ----------
INSERT INTO t_expense_category
    (id, tenant_id, code, name, enabled, sort_order, version, created_at, updated_at, deleted)
SELECT c.id + 10, 0, c.code, c.name, c.enabled, c.sort_order, 0, NOW(3), NOW(3), 0
FROM t_expense_category c
WHERE c.tenant_id = 1001 AND c.deleted = 0 AND c.id BETWEEN 101 AND 200
ON DUPLICATE KEY UPDATE
    name = VALUES(name), enabled = VALUES(enabled), sort_order = VALUES(sort_order),
    deleted = 0, updated_at = NOW(3);

DROP TABLE IF EXISTS tmp_map_0069;

-- ---------- 阶段 8：校验（SIGNAL 防「影响 0 行却 EXIT 0」的假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0069;
DELIMITER $$
CREATE PROCEDURE sp_verify_0069()
BEGIN
    DECLARE v_ent   INT DEFAULT 0;
    DECLARE v_pub   INT DEFAULT 0;
    DECLARE v_fld   INT DEFAULT 0;
    DECLARE v_relfmt INT DEFAULT 0;
    DECLARE v_cat   INT DEFAULT 0;
    DECLARE v_legacy INT DEFAULT 0;

    SELECT COUNT(*) INTO v_ent FROM meta_entity
     WHERE tenant_id = 0 AND deleted = 0
       AND code IN ('expense_reimbursement','expense_category','expense_reimbursement_item');
    SELECT COUNT(*) INTO v_pub FROM meta_entity
     WHERE tenant_id = 0 AND deleted = 0 AND delivery_mode = 1 AND status = 1
       AND code IN ('expense_reimbursement','expense_category','expense_reimbursement_item');
    IF v_ent <> 3 OR v_pub <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0069 校验失败：平台租户 0 的报销实体不足 3 个或未处于 RUNTIME+PUBLISHED';
    END IF;

    -- 关键字段必须是 L1 形态（REFERENCE / CHILD_TABLE / ATTACHMENT），否则运行时关系引擎没接线
    SELECT COUNT(*) INTO v_fld
      FROM meta_field f
      JOIN meta_entity e ON e.id = f.entity_id AND e.tenant_id = 0 AND e.deleted = 0
     WHERE f.deleted = 0
       AND e.code IN ('expense_reimbursement','expense_category','expense_reimbursement_item')
       AND ((e.code = 'expense_reimbursement' AND f.code = 'category_id' AND UPPER(f.type) = 'REFERENCE')
         OR (e.code = 'expense_reimbursement' AND f.code = 'items'        AND UPPER(f.type) = 'CHILD_TABLE')
         OR (e.code = 'expense_reimbursement' AND f.code = 'attachment'   AND UPPER(f.type) = 'ATTACHMENT'));
    IF v_fld <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0069 校验失败：报销单的 REFERENCE/CHILD_TABLE/ATTACHMENT 字段未就位';
    END IF;

    SELECT COUNT(*) INTO v_relfmt FROM meta_entity_relation
     WHERE tenant_id = 0 AND id IN (9601, 9602, 9603) AND deleted = 0;
    IF v_relfmt <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0069 校验失败：平台租户 0 的报销关系声明不足 3 条';
    END IF;

    SELECT COUNT(*) INTO v_cat FROM t_expense_category WHERE tenant_id = 0 AND deleted = 0;
    IF v_cat < 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0069 校验失败：平台租户 0 的报销类别主数据少于 5 条';
    END IF;

    SELECT COUNT(*) INTO v_legacy
      FROM meta_field f
      JOIN meta_entity e ON e.id = f.entity_id AND e.tenant_id = 0 AND e.code = 'expense_reimbursement'
     WHERE f.code = 'category' AND UPPER(f.type) = 'ENUM' AND f.deleted = 0;
    IF v_legacy <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0069 校验失败：遗留 category(ENUM) 字段未退役（运行时会出现两个「类别」）';
    END IF;

    SELECT v_ent AS entities, v_pub AS runtime_published, v_fld AS l1_fields,
           v_relfmt AS relations, v_cat AS categories, v_legacy AS legacy_enum_fields;
END$$
DELIMITER ;

CALL sp_verify_0069();
DROP PROCEDURE IF EXISTS sp_verify_0069;
