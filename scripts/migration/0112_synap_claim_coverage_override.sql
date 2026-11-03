-- ============================================================
-- 0112_synap_claim_coverage_override.sql
--
-- 一句话：生效通道补建两段式覆盖表 t_claim_coverage_override（对应参考态 V22）。
--
-- 背景
-- ----------------------------------------------------------------
-- 多保司配置面 task 3 的 DDL 此前只落在 bone-init.sql（fresh-install 路径）与
-- bone-apps/synap-claim/src/main/resources/db/migration/V22（**参考态**，无 runner，
-- 不会被自动执行）。生效通道 scripts/migration/ 缺位 ⇒ 既有活库永远建不出该表，
-- /coverage-overrides/** 全部 500。本脚本把同一份 DDL 带进生效通道，与 init 逐字一致
-- （两通道漂移由 scripts/check-ddl-duplicate-column.py 把守）。
--
-- 不变量（与七表同源，详见参考态 V22 注释）：
--   * UK(tenant_id, biz_identity_code, coverage_uuid, override_field) —— 同一维度至多一行；
--   * @Version 乐观锁并发发布/停用，不用 FOR UPDATE；
--   * 无物理删除：停用是置 status。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================

USE bone;

CREATE TABLE IF NOT EXISTS t_claim_coverage_override (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
  biz_identity_code VARCHAR(64)   NOT NULL COMMENT '业务主体编码（映射键，须为 ACTIVE 主体）',
  coverage_uuid     VARCHAR(64)   NOT NULL COMMENT '责任UUID（t_adj_liability.liability_uuid，各版本共享）',
  override_field    VARCHAR(128)  NOT NULL COMMENT '覆盖维度：LIMIT_HOSPITAL_CODES / LIMIT_DIAGNOSIS_CODES / FEE_RULE.<feeCode>',
  standard_value    TEXT          NOT NULL COMMENT '创建/发布时的标准值快照（留痕；合并永远取实时标准）',
  override_value    TEXT          NOT NULL COMMENT '覆盖值（名单=分隔串；费用规则=JSON）',
  status            VARCHAR(16)   NOT NULL DEFAULT 'DISABLED' COMMENT 'DISABLED(未生效)/ACTIVE(已发布)',
  created_by        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '创建人',
  created_at        TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_by        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '更新人',
  updated_at        TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT 'SDK 软删标记（本域恒 0，见 §6.8 偏离说明）',
  version           BIGINT        NOT NULL DEFAULT 0 COMMENT 'SDK 乐观锁版本',
  PRIMARY KEY (id),
  UNIQUE KEY uk_coverage_override (tenant_id, biz_identity_code, coverage_uuid, override_field),
  KEY idx_coverage_override_lookup (tenant_id, coverage_uuid, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '责任配置保司覆盖项（两段式）';

-- 验收：表必须存在且 UK 齐全
SELECT COUNT(*) INTO @v_table FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'bone' AND TABLE_NAME = 't_claim_coverage_override';
SELECT COUNT(*) INTO @v_uk FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = 'bone' AND TABLE_NAME = 't_claim_coverage_override'
   AND INDEX_NAME = 'uk_coverage_override';
SELECT IF(@v_table = 1 AND @v_uk >= 4, 'OK', 'FAIL: t_claim_coverage_override 建表或 UK 缺失') AS verify_0112;
