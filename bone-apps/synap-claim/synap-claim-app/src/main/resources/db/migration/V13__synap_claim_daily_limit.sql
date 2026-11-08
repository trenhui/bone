-- synap-claim 第十三批 DDL 变更：日赔付限额（Q-05）落地
--
-- 一、t_adj_liability 新增 daily_limit / daily_limit_basis
--
-- 补的是什么洞：V1 业务规则 §7.4 明确标〔缺〕——「配置要素里存在两档（按同日赔案累计 / 按同日发票累计），
--   但主流程九步与流程图里都没有日限额判定」，属「配了不生效 ⇒ 超赔」的 P0（Q-05）。
--   落地前该配置在代码里连列都没有：LimitSet.dailyLimit 生产路径恒为 0，唯一"判定点"是
--   LiabilityRule.validate() 里一条「配了就拒绝装载」的兜底——那是把缺口变成不可用，不是修好。
--
-- 两列语义：
--   daily_limit       ：日赔付限额（0 = 不启用）。同一天内该被保人该责任的赔付累计不得超过此值；
--   daily_limit_basis ：日窗口口径。SAME_DAY_INVOICE = 按同日发票累计（窗口取发票日期）；
--                       SAME_DAY_CLAIM = 按同日赔案累计（窗口取赔案受理日）；
--                       NONE（默认/未配）= 不启用日限额，此时 daily_limit 必须为 0（装载期校验）。
--                       两列必须「同配或同不配」：只配金额不配口径则窗口判不出来，只配口径不配金额
--                       则该口径永远不生效——两种都是「配了不生效」，装载期一律拒绝。
--
-- 为什么 NOT NULL DEFAULT：责任配置是版本化实体，已生效版本不可改（§4.1），新增列必须让历史行与新建行
--   都有确定值（0 / NONE = 不启用），否则 SELECT 会读到 NULL、INSERT 会因非空无默认在运行期 1364。
--   与 liability_deduct / liability_limit / invoice_deduct 同口径（DECIMAL(12,2) NOT NULL DEFAULT 0）。
--
-- 二、t_quota_transaction 新增 biz_date
--
-- 补的是什么洞：日限额要按「同一业务日已赔多少」截断，而额度流水（append-only，本系统的事实表）
--   此前只有 occurred_at（落账时刻），没有业务日。理赔实务里两者不是同一天——9 月的发票 10 月才赔，
--   按落账日累计等于把"集中补赔"当成"同日多次就诊"，日限额会误伤。
--
-- 语义：biz_date 是该笔金额所属的**业务日**（发票日期 / 赔案受理日），由占额调用方按责任配置的
--   daily_limit_basis 传入。历史行为 NULL（不参与日累计，语义为"未知业务日"），因此不影响既有账。
--
-- 为什么留在流水表而不另立「按日账本」表：流水是事实、账户是投影（§11.7 不变量 1）。日用量是
--   「同一 biz_date 上的 DAILY 流水净额」，是一条可随时从事实重算的投影；另立一张按日余额表会变成
--   第四本需要与流水对账的账（"四本账对不上"正是这类系统最难查的资金事故）。

ALTER TABLE t_adj_liability
  ADD COLUMN daily_limit DECIMAL(12,2) NOT NULL DEFAULT 0
    COMMENT '日赔付限额(Q-05)：同一业务日累计上限；0=不启用'
    AFTER adjust_deduct,
  ADD COLUMN daily_limit_basis VARCHAR(24) NOT NULL DEFAULT 'NONE'
    COMMENT '日窗口口径(Q-05)：NONE/SAME_DAY_INVOICE/SAME_DAY_CLAIM'
    AFTER daily_limit;

ALTER TABLE t_quota_transaction
  ADD COLUMN biz_date DATE DEFAULT NULL
    COMMENT '业务日(发票日期/赔案受理日)：日限额按此累计；NULL=不参与日累计'
    AFTER occurred_at;
