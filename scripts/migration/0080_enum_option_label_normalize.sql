-- =============================================================================
-- 0080_enum_option_label_normalize.sql
--
-- 目的：把**早期「字符串数组」形态**的 ENUM 选项归一为 `[{code,label}]` 对象数组。
--
-- 背景（缺陷实证，2026-10-31 UI 取证）：
--   `费用报销` 运行时数据页的状态列显示的是 `APPROVED / SUBMITTED / REJECTED` 这类**裸枚举码**。
--   根因不是样式，而是数据形态：`meta_field.enum_values` 里躺着 `["DRAFT","SUBMITTED",...]`，
--   渲染层按 `{code,label}` 取中文标签（`parseEnumValues` 已兼容字符串数组，但只能拿
--   `label = code` 兜底）——业务用户看到的就是英文码。
--
--   全库同类字段共 7 个（321 个 ENUM 中 308 个已是对象数组，占 96%），说明对象数组才是平台口径，
--   这 7 个是早期建模产物：
--     · status × 2            （9210 / 764059967975063552 两个「费用报销」实体）
--     · customerLevel / supplierLevel / qualificationStatus / employmentType / memberLevel
--                             （客户 / 供应商 / 员工 / 渠道买家 等 Master 实体）
--
-- 口径与依据：
--   · **只修展示形态，不改真源**。客户等级等 4 个字段在 `sys_dict_*` 已有同名码表，
--     按《11. 枚举选项源统一设计方案》§4，理想终态是 `enum_source = dict` 绑定以获得
--     运营可维护性；但那属于**建模侧治理决策**（改真源），不是本次展示缺陷修复的范围。
--     本脚本把字典里的既有中文标签**抄进快照**，保持 `enum_source` 仍为空（inline）；
--     后续有需要再由实施者在界面设计器改为 dict 绑定，届时 resolve 会覆盖此处快照。
--     故本脚本对 `enum_source` 非空的字段**一律跳过**（那是字典解析的产物，人工覆盖会与
--     下一次 refresh 打架）。
--   · 标签措辞对齐平台既有占多数的 16 个 `status` 字段（草稿/已提交/已审批/已驳回/已打款），
--     不另造词——同一状态在 16 个实体里显示同一个中文，是列表可扫读的前提。
--   · 优先「字段级」标签，其次「全局」标签，都没有则 `label = code`（不劣于现状，
--     且转成对象数组后至少能走标准 Tag 着色与后续补齐路径）。
--
-- 幂等：仅处理 `enum_values[0]` 为 STRING 的行；第二次执行命中 0 行。
-- ⚠ L3 待审批：元数据字段内容变更，须架构师审批后由 DBA/CI 执行。
-- =============================================================================

USE bone;

-- ── 1. 标签字典（普通表而非临时表）─────────────────────────────────────────────
-- 为什么不是 TEMPORARY：MySQL 8 的临时表在同一语句中被**两次引用**即报
-- `ERROR 1137 Can't reopen table`（本脚本首跑即踩），而下面的 JOIN 需要「字段级覆写」
-- 与「全局词表」两次连接。普通表可多次引用，用完即 DROP。
DROP TABLE IF EXISTS mig_0080_enum_label;
CREATE TABLE mig_0080_enum_label (
  field_code VARCHAR(64) NULL,
  code       VARCHAR(64) NOT NULL,
  label      VARCHAR(64) NOT NULL,
  KEY k_code (code),
  KEY k_field (field_code)
-- 排序规则必须显式对齐 `meta_field`（utf8mb4_unicode_ci）：库里同时存在 0900_ai_ci 与 unicode_ci，
-- 默认继承 0900_ai_ci 会让下面的 JOIN 报 1267 Illegal mix of collations（本脚本第二跑踩到）。
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 全局词表：与平台 16 个既有 status 字段的措辞保持一致
INSERT INTO mig_0080_enum_label (field_code, code, label) VALUES
  (NULL, 'DRAFT',     '草稿'),
  (NULL, 'SUBMITTED', '已提交'),
  (NULL, 'APPROVED',  '已审批'),
  (NULL, 'REJECTED',  '已驳回'),
  (NULL, 'PAID',      '已打款');

-- 字段级覆写：取 `sys_dict_item` 中同名码表的中文标签。
-- 字典条目按租户覆盖（现网仅 1001 一套），这里按 code 取一条代表值；实际生效措辞以字典界面为准。
INSERT INTO mig_0080_enum_label (field_code, code, label)
SELECT 'customerLevel', code, MAX(label) FROM sys_dict_item
 WHERE type_code = 'customer_level' AND deleted = 0 AND status = 1 GROUP BY code;
INSERT INTO mig_0080_enum_label (field_code, code, label)
SELECT 'supplierLevel', code, MAX(label) FROM sys_dict_item
 WHERE type_code = 'supplier_level' AND deleted = 0 AND status = 1 GROUP BY code;
INSERT INTO mig_0080_enum_label (field_code, code, label)
SELECT 'employmentType', code, MAX(label) FROM sys_dict_item
 WHERE type_code = 'employment_type' AND deleted = 0 AND status = 1 GROUP BY code;

-- 字典未覆盖的两个字段（会员等级 / 资质状态）：按业务惯例补齐
INSERT INTO mig_0080_enum_label (field_code, code, label) VALUES
  ('memberLevel', 'NORMAL',   '普通会员'),
  ('memberLevel', 'SILVER',   '白银会员'),
  ('memberLevel', 'GOLD',     '黄金会员'),
  ('memberLevel', 'PLATINUM', '铂金会员'),
  ('qualificationStatus', 'VALID',   '有效'),
  ('qualificationStatus', 'PENDING', '待审'),
  ('qualificationStatus', 'EXPIRED', '已过期');

-- ── 2. 归一并回写（保持原有顺序：GROUP_CONCAT ... ORDER BY 序号）────────────────
SET SESSION group_concat_max_len = 65535;

UPDATE meta_field f
  JOIN (
    SELECT
      f2.id AS fid,
      -- 用 GROUP_CONCAT 而非 JSON_ARRAYAGG：后者在 MySQL 8 不支持 ORDER BY，
      -- 选项顺序会随执行计划漂移（等级类枚举一旦乱序，用户读到的是错误的强弱关系）。
      CAST(CONCAT('[', GROUP_CONCAT(
             JSON_OBJECT('code', jt.v, 'label', COALESCE(ds.label, dg.label, jt.v))
             ORDER BY jt.ord SEPARATOR ','
           ), ']') AS JSON) AS new_values
      FROM meta_field f2
      JOIN JSON_TABLE(
             f2.enum_values, '$[*]' COLUMNS (ord FOR ORDINALITY, v VARCHAR(64) PATH '$')
           ) jt
      LEFT JOIN mig_0080_enum_label ds
             ON ds.field_code = f2.code COLLATE utf8mb4_unicode_ci
            AND ds.code = jt.v COLLATE utf8mb4_unicode_ci
      LEFT JOIN mig_0080_enum_label dg
             ON dg.field_code IS NULL
            AND dg.code = jt.v COLLATE utf8mb4_unicode_ci
     WHERE f2.deleted = 0
       AND f2.type = 'ENUM'
       AND (f2.enum_source IS NULL OR f2.enum_source = '')
       AND f2.enum_values IS NOT NULL
       AND JSON_VALID(f2.enum_values)
       AND JSON_TYPE(f2.enum_values) = 'ARRAY'
       AND JSON_TYPE(JSON_EXTRACT(f2.enum_values, '$[0]')) = 'STRING'
     GROUP BY f2.id
  ) x ON x.fid = f.id
   SET f.enum_values = x.new_values,
       f.updated_at  = NOW(3),
       f.version     = f.version + 1;

-- ── 3. 校验：剩余「字符串数组」必须为 0；打印归一结果 ─────────────────────────
SELECT
  (SELECT COUNT(*) FROM meta_field
    WHERE deleted = 0 AND type = 'ENUM' AND enum_values IS NOT NULL
      AND JSON_VALID(enum_values)
      AND JSON_TYPE(JSON_EXTRACT(enum_values, '$[0]')) = 'STRING') AS remaining_string_arrays,
  (SELECT COUNT(*) FROM meta_field
    WHERE deleted = 0 AND type = 'ENUM' AND enum_values IS NOT NULL
      AND JSON_VALID(enum_values)
      AND JSON_TYPE(JSON_EXTRACT(enum_values, '$[0]')) = 'OBJECT') AS object_arrays;

SELECT f.entity_id, e.code AS entity_code, f.code AS field_code, f.enum_values
  FROM meta_field f
  JOIN meta_entity e ON e.id = f.entity_id
 WHERE f.deleted = 0
   AND f.code IN ('status', 'customerLevel', 'supplierLevel', 'qualificationStatus',
                  'employmentType', 'memberLevel')
   AND f.entity_id IN (9210, 210001, 210002, 210006, 210008, 764059967975063552)
 ORDER BY f.entity_id, f.code;

DROP TABLE IF EXISTS mig_0080_enum_label;
