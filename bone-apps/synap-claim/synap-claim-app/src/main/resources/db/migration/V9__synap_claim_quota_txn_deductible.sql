-- synap-claim 第九批 DDL 变更：额度账户/流水枚举与注释对齐（⑩ 跨案累计落地）
--
-- 一、t_quota_transaction.txn_type 新增取值 DEDUCTIBLE
--   年度免赔的消抵与回退必须落流水（否则无法按 idempotency_key 判重、撤件时无据可退），
--   而原注释只列了 SOFT/HARD/PAID/RELEASE，DBA 与对账脚本会据此认为不存在该取值。
--   方向仍复用既有 OCCUPY/ROLLBACK：消抵记 OCCUPY，回退记 ROLLBACK，与额度占用的方向语义一致。
--
-- 二、t_quota_account.quota_year 注释据实修正
--   原注释写"按保单年度"，但当前写侧（QuotaApplicationService）与读侧（AccumulationPort 适配器）
--   都用 Year.now() 自然年。文档宣称 X 而代码做了 Y 是最危险的一类不一致，此处先让注释与实现一致；
--   切换到保单年度需保单快照提供生效日，作为独立改动推进（读写两侧必须同批切换，否则两本账立刻对不上）。

ALTER TABLE t_quota_transaction
  MODIFY COLUMN txn_type VARCHAR(16) NOT NULL COMMENT 'SOFT/HARD/PAID/RELEASE/DEDUCTIBLE';

ALTER TABLE t_quota_account
  MODIFY COLUMN quota_year INT NOT NULL
    COMMENT '额度年度(当前实现=自然年；保单年度口径待保单快照提供生效日后与读写两侧同批切换)';
