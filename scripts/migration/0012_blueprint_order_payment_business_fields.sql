-- ============================================================
-- 0012_blueprint_order_payment_business_fields.sql
-- bone-blueprint 真实交易场景字段扩展（机制 A：领域核心字段，进聚合模型与金额不变量）。
-- 依据：doc/design/bone-blueprint-元数据字段扩展设计.md §2.1 / §2.3「机制 A」清单。
--       现有 t_order 仅 customer_id/total_amount/status，缺业务单号、订单来源、
--       运费/优惠（金额三口径）、支付完成时刻；bp_payment 缺币种与支付有效期。
-- 策略：ADR-生产数据库增量迁移策略（expand/contract）。只加可空列 + 索引，不改既有列、
--       不删列；contract 阶段不在本轮。
-- 注意：与元数据目录（bone-metadata-server）纳管的「机制 B」长尾字段互不冲突——
--       本脚本的 7 列是领域模型映射列，机制 B 的 18 列由元数据引擎发布时自动 ALTER。
-- 状态：L3 DDL —— 需架构师审批后执行。
-- ============================================================

-- ---------- 阶段 0：备份（幂等保护：已存在则跳过） ----------
CREATE TABLE IF NOT EXISTS bak_t_order_0012 AS SELECT * FROM t_order;
CREATE TABLE IF NOT EXISTS bak_bp_payment_0012 AS SELECT * FROM bp_payment;

-- ---------- 阶段 1：Expand —— t_order ----------
ALTER TABLE t_order
    ADD COLUMN order_no        VARCHAR(32)     DEFAULT NULL COMMENT '业务订单号（对外展示/客服检索键，与物理 id 分离）',
    ADD COLUMN channel_source  VARCHAR(32)     DEFAULT NULL COMMENT '订单来源渠道：APP/H5/小程序/POS',
    ADD COLUMN freight_amount  DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '运费',
    ADD COLUMN discount_amount DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '优惠总额',
    ADD COLUMN paid_time       DATETIME(3)     DEFAULT NULL COMMENT '支付完成时刻（订单生命周期时间轴刻度）';
ALTER TABLE t_order
    ADD UNIQUE KEY uk_order_tenant_order_no (tenant_id, order_no),
    ADD KEY idx_order_tenant_channel (tenant_id, channel_source);

-- ---------- 阶段 2：Expand —— bp_payment ----------
ALTER TABLE bp_payment
    ADD COLUMN currency        VARCHAR(8)      NOT NULL DEFAULT 'CNY' COMMENT '币种（ISO 4217，跨境前置）',
    ADD COLUMN pay_expire_at   DATETIME(3)     DEFAULT NULL COMMENT '支付有效期截止时刻（驱动超时关单）';

-- ---------- 阶段 3：校验（人工执行后复核） ----------
-- SELECT COUNT(*) AS order_no_null FROM t_order WHERE deleted = 0 AND order_no IS NULL;
-- SELECT COUNT(*) AS currency_missing FROM bp_payment WHERE currency IS NULL;
-- 预期：存量行 order_no 为 NULL（由应用侧生成补写或保持空），currency 全为 'CNY'（DEFAULT 生效）。
