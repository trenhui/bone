-- V25: 报案 / 赔案拆分（1:1 → 1:0..N，2026-11-22 落地）。
--
-- 为什么：按 JR/T 0212-2020 等权威口径，报案（FNOL，事故告知事件）与赔案（赔付处理单元）
-- 是两个聚合，一次告知可对应 0..N 笔赔付（分案 / 补赔 / 一次事故多张发票分期提交）。
-- 旧模型把两者压成一张表，物理层就排除了「同一起事故追加第二笔赔案」的可能。
--
-- 结构：t_claim_report 承担事故维度（保单 / 计划 / 被保人 / 出险时间 / 计划版本），
-- t_claim_case 承担赔付维度，通过 report_id 单向引用报案（D1：不用 @Cascade 子集合，
-- 赔案有自己的状态机与生命周期）。
--
-- 编号：报案号 report_no 与赔案号 claim_no 共用 BizNoCodec 内核（雪花 ID 6 轮 Feistel 双射
-- → Crockford Base32(13) → Luhn mod 32 校验位），仅前缀不同（RP / CL）。前缀不参与校验位
-- 计算，故存量 CL→RP 前缀改写后校验位依然合法，回填可为纯 SQL。
--
-- 与 scripts/migration/0139_synap_claim_report_case_split.sql 同源：那里是「在途库增量 +
-- 脏数据探测 + 终态 SIGNAL 断言」，这里是模块参考态的全新装形态，两处列定义必须一字不差。
-- 本表为 synap-claim 模块内 Flyway 迁移，不在平台迁移清单 scripts/migration 内。

CREATE TABLE t_claim_report (
  id              BIGINT        NOT NULL COMMENT '主键(分布式ID)',
  report_no       VARCHAR(64)   NOT NULL COMMENT '报案号(RP+yyyyMMdd+13位Base32+校验位, 24字符, 全局唯一)',
  policy_no       VARCHAR(32)   NOT NULL COMMENT '保单号(事故归属, 追加赔案时继承)',
  plan_id         BIGINT        NOT NULL COMMENT '计划ID(理赔口径基线)',
  insured_id_hash CHAR(64)      NOT NULL COMMENT '假名化被保人(服务端 HMAC 计算, 不落明文)',
  claim_type      VARCHAR(32)   NOT NULL DEFAULT 'OUTPATIENT' COMMENT '险种(事故级属性, 继承给每个赔案)',
  out_insure_time DATETIME(3)   NOT NULL COMMENT '出险时间(事故告知事实)',
  -- plan_version 放在报案而不是赔案：计划版本在「受理」这一刻锁定。赔案各存一份会让
  -- 同一事故的多个赔案锁到不同版本，复算时分不清哪个才是当时的合同状态。
  plan_version    VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '受理时锁定的计划版本(全案复算确定性)',
  source_channel  VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '渠道',
  source_system   VARCHAR(32)   NOT NULL DEFAULT 'self' COMMENT '来源系统(self/pk_tpa)',
  -- 报案状态只表达「能否再追加赔案」，不表达赔付进度：进度是派生量（由同案赔案聚合），
  -- 落成初始态会让两套状态机互相漂移。
  status          VARCHAR(32)   NOT NULL DEFAULT 'REPORTED' COMMENT '报案状态(REPORTED可追加/CLOSED已关闭)',
  tenant_id       BIGINT        NOT NULL COMMENT '租户',
  created_by      BIGINT        DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by      BIGINT        DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)   NOT NULL COMMENT '更新时间',
  version         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本(SDK @Version 维护)',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  -- 与 t_claim_case.uk_claim_no 同口径：编号全局唯一（源自全局雪花 ID），而非租户内唯一
  UNIQUE KEY uk_report_no (report_no),
  KEY idx_tenant_report_status (tenant_id, status, created_at),
  KEY idx_tenant_report_policy (tenant_id, policy_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报案主表(FNOL, 1:0..N 的 1 侧)';

-- 赔案侧新增归属列。幂等写法，与生效通道 0139 §4 一字不差：
-- V1__synap_claim_init.sql 已在建表时声明 report_id（1:N 拆分后并入），
-- 若在途库由 0139 加过列则此处跳过，全新装则 V1 已建、同样跳过——
-- 无条件 ADD 会在两种环境都报 ERROR 1060（门禁 D1 判红的就是它）。
SET @has_report_id_col = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case' AND column_name = 'report_id'
);
SET @add_report_id = IF(
  @has_report_id_col = 0,
  'ALTER TABLE t_claim_case ADD COLUMN report_id BIGINT NULL COMMENT ''所属报案ID(t_claim_report.id, 1:0..N 的 N 侧)'' AFTER claim_no',
  'DO 0'
);
PREPARE add_report_id_stmt FROM @add_report_id;
EXECUTE add_report_id_stmt;
DEALLOCATE PREPARE add_report_id_stmt;

-- 「按报案查同案赔案」是读侧主路径（详情页同案清单、追加赔案前置校验）。
-- 同样幂等：与 0139 §8 同源，重跑不报 ERROR 1061（重复索引名）。
SET @has_report_idx = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
    AND index_name = 'idx_tenant_report'
);
SET @add_report_idx = IF(
  @has_report_idx = 0,
  'ALTER TABLE t_claim_case ADD KEY idx_tenant_report (tenant_id, report_id)',
  'DO 0'
);
PREPARE add_report_idx_stmt FROM @add_report_idx;
EXECUTE add_report_idx_stmt;
DEALLOCATE PREPARE add_report_idx_stmt;

-- batch_no 不再参与任何编号生成：报案号与赔案号各由自己的雪花 ID 生成，
-- batch_no 只剩 OSS 归档路径前缀与复制赔案分组两个用途。
ALTER TABLE t_claim_case
  MODIFY COLUMN batch_no VARCHAR(64) NOT NULL DEFAULT ''
    COMMENT '批次号(OSS 路径前缀 + 复制赔案分组, 不参与编号生成)';
