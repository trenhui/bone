-- 0043：运行时自动编号（单据号）能力
-- 背景：费用报销等场景的单据号（如报销单号）当前由测试/客户端手动填写，缺乏平台级、
--       可读、单调递增的单据号。本迁移引入实体级自动编号配置 + 序列计数器表，
--       由 JdbcRuntimeRecordService 在记录创建时按「前缀-日期-序号」注入，替换 PAY-<nanoTime> 之类的占位。
-- 策略（expand/contract）：纯追加，不改动既有列与写路径；旧实体 auto_number 为 NULL 即不触发。

-- 实体级自动编号配置（JSON）：{"field":"claim_no","prefix":"CLM","reset":"daily"}
--   field  : 承载单据号的字段编码（必须是实体的 STRING 字段）
--   prefix : 单据号前缀（如 CLM=Claim）
--   reset  : 序号重置粒度，"daily"=按天重置（键含日期），其他/缺省=全局单调递增
ALTER TABLE meta_entity
    ADD COLUMN auto_number JSON DEFAULT NULL
    COMMENT '自动编号配置 JSON（如 {"field":"claim_no","prefix":"CLM","reset":"daily"}）；非空时记录创建由平台生成单据号';

-- 运行时单据号序列计数器：以 (tenant_id:entity_code:field:date) 为键，原子自增。
-- 原子性由 INSERT ... ON DUPLICATE KEY UPDATE 的行级 next-key 锁保证（InnoDB），无需 SELECT FOR UPDATE。
CREATE TABLE IF NOT EXISTS meta_runtime_sequence (
    seq_key       VARCHAR(200) NOT NULL COMMENT '序号键（tenant:entity:field:date）',
    current_value BIGINT        NOT NULL DEFAULT 0 COMMENT '当前序号值',
    PRIMARY KEY (seq_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行时单据号序列计数器';
