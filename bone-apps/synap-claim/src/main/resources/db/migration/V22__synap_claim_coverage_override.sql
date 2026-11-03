-- V22: 两段式覆盖（责任配置的保司差异化）
--
-- 背景（多保司配置面 task 3）：标准配置是默认真源（t_adj_liability / t_adj_liability_fee_rule），
-- 各业务主体可对指定维度做覆盖。覆盖项按「主体×责任×字段」一行，发布前 DISABLED、发布后 ACTIVE。
-- 合并语义永远实时取标准（快照列只是留痕），停用单条即回落标准——回滚不需要删行。
--
-- 不变量（与七表同源）：
--   * UK(tenant_id, biz_identity_code, coverage_uuid, override_field) —— 同一维度至多一行，覆盖值更新走 UPDATE 不插新行；
--   * @Version 乐观锁并发发布/停用，不用 FOR UPDATE；
--   * 无物理删除：停用是置 status，回放链靠行内快照。
CREATE TABLE t_claim_coverage_override (
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
