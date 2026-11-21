-- ============================================================
-- 0135_synap_claim_factor_matrix.sql
--
-- 一句话：新建 t_claim_factor_matrix——把理算责任的「因子笛卡尔积矩阵」
--         从原理赔的 valueList 范式落成新系统可版本化、可合并的配置事实。
--
-- 背景（为什么必须加）
-- ----------------------------------------------------------------
-- 原型 claim-rule-config.html 的三层责任矩阵 + 因子笛卡尔积（医院等级×医保身份×
-- 金额区间）需要一张表存每格的值；当前 LiabilityView 全是标量 BigDecimal 字段，
-- 无法表达"不同因子组合不同赔付比例/免赔"。原理赔 AdjustmetDetail.valueList
-- 已验证该范式（key=因子值连接串，value=格值）。
--
-- 形态（为什么这样设计）
-- ----------------------------------------------------------------
--   dimension_set：因子有序组合码，如 HOSPITAL_LEVEL|MEDICAL_INSURANCE；
--   row_key：行键，如 HOSPITAL_LEVEL=一级;MEDICAL_INSURANCE=社保；
--   col_key：列值（单列矩阵为 DEFAULT）；
--   cell_value：格值（比例/免赔/限额，DECIMAL(12,4)）；
--   version：版本号，同一 (tenant, liability, dimension_set, row_key, col_key)
--            多版本并存，已发布版本不可改（对齐责任版本化纪律）。
-- 唯一键覆盖全维度含 version，写入期按 row_key/col_key merge 保留已填值，
-- 不做全量清空（原型"结构变更保留已填值"）。
--
-- 必备列：tenant_id / 审计列 / deleted / version（HC-008）。
--
-- 终态断言（fail-closed）
-- ----------------------------------------------------------------
-- information_schema 强断言表存在且必备列齐全。
-- ============================================================

CREATE TABLE IF NOT EXISTS t_claim_factor_matrix (
  id              BIGINT        NOT NULL COMMENT '主键(分布式ID)',
  tenant_id       BIGINT        NOT NULL COMMENT '租户ID',
  liability_id    BIGINT        NOT NULL COMMENT '责任ID(t_adj_liability.id)',
  liability_uuid  VARCHAR(64)   NOT NULL COMMENT '责任UUID(各版本共享)',
  dimension_set   VARCHAR(64)   NOT NULL COMMENT '因子有序组合码(HOSPITAL_LEVEL|MEDICAL_INSURANCE)',
  row_key         VARCHAR(255)  NOT NULL COMMENT '行键(HOSPITAL_LEVEL=一级;MEDICAL_INSURANCE=社保)',
  col_key         VARCHAR(64)   NOT NULL DEFAULT 'DEFAULT' COMMENT '列值(单列矩阵=DEFAULT)',
  cell_value      DECIMAL(12,4) NOT NULL COMMENT '格值(赔付比例/免赔/限额)',
  version         INT           NOT NULL DEFAULT 1 COMMENT '矩阵版本号(已发布不可改)',
  status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
  created_by      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '创建人',
  created_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_by      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '更新人',
  updated_at      TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT 'SDK软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_factor_matrix_cell (tenant_id, liability_id, dimension_set, row_key, col_key, version),
  KEY idx_factor_matrix_lookup (tenant_id, liability_uuid, dimension_set, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '理算责任因子矩阵(版本化,按row_key/col_key合并保留已填值)';

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0135$$
CREATE PROCEDURE sp_verify_0135()
BEGIN
    DECLARE v_missing INT DEFAULT 0;
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_factor_matrix'
       AND column_name IN ('tenant_id', 'created_by', 'created_at', 'updated_by', 'updated_at', 'deleted', 'version');
    IF v_missing <> 7 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0135 terminal assert failed: t_claim_factor_matrix required columns missing';
    END IF;
END$$
DELIMITER ;
CALL sp_verify_0135();
DROP PROCEDURE IF EXISTS sp_verify_0135;
