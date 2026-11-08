-- synap-claim 第十一批 DDL 变更：决策结论「消费结果」落库
--
-- 一、claim_decision_execution 新增 consumption_reason / consumed_at
--
-- 补的是什么洞：决策图产出 AUTO_APPROVE 后，是否真的「结案 + 建结算单」由
--   AutoApprovalApplicationService#applyAutoDecision 判定，它返回受控原因码
--   （APPLIED / AUTO_APPROVE_DISABLED / CAP_EXCEEDED / ALREADY_SETTLED / ...）。
--   此前这个原因码**只存在于 POST /decision/execute 的响应里**，没有任何一列承载它，
--   于是「已自动通过但资金没动」这个最容易引起客诉的状态，读侧一律看不见：
--   工作台只能靠「点开决策图 = 重新执行一次决策」把它取回来，而重新执行是写操作
--   （AUTO_APPROVE 且开关打开时会再建一张结算单），查看动作带资金副作用 —— 已改为只读
--   轨迹后该字段就彻底缺席了。
--
-- 为什么加列而不是从现有列推导：原因码取决于**执行当时**的开关与单笔上限
--   （bone.claim.auto-approve.enabled / max-amount）以及案件当时是否已结案。
--   事后用当前配置重算，得到的不是"当时为什么没放款"，而是"现在如果重跑会怎样"——
--   两者在开关被改过之后必然分叉。可推导的假象比留空更危险，故按事实落库。
--
-- 为什么允许 NULL：NULL 的语义是「本次执行没有自动通过结论可消费」（决策结果为
--   SUGGEST_REJECT / MANUAL_REQUIRED / NEED_MORE_EVIDENCE）。非 NULL 才表示"消费动作
--   被判定过"——APPLIED 是成功，其余是受控跳过。不用空串缺省，是为了让"没有消费动作"
--   与"消费了但原因未知"可区分（后者不该出现，一旦出现即数据缺陷，可被查询发现）。
--
-- 为什么 consumed_at 不叫 finished_at：finished_at 是决策图跑完的时间，consumed_at 是
--   结论被消费（或受控跳过）的时间，两者可能相差数秒（消费要读案件、算理算金额、建结算单）。
--   合并成一个时间会让「决策跑完 3 秒后才发现额度不足」这类时序问题无从还原。
--
-- 与 §9 原稿的关系：原稿的 claim_decision_execution 只有决策侧事实，没有消费侧事实
--   （消费是后来才接进来的编排）。本次属**追加列**，不改既有列语义，历史行保持 NULL。

ALTER TABLE claim_decision_execution
  ADD COLUMN consumption_reason VARCHAR(32) DEFAULT NULL
    COMMENT '自动通过结论的消费结果(APPLIED/AUTO_APPROVE_DISABLED/CAP_EXCEEDED/ALREADY_SETTLED); NULL=结论非自动通过或尚未消费'
    AFTER duration_ms,
  ADD COLUMN consumed_at DATETIME(3) DEFAULT NULL
    COMMENT '结论消费(或受控跳过)的判定时间'
    AFTER consumption_reason;
