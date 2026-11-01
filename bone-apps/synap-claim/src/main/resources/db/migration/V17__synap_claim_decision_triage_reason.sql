-- synap-claim 第十七批 DDL 变更：决策分诊原因码落库
--
-- 注意：本通道（模块内 Flyway V1..V16）是一条从未被执行的通道（无 flyway-core 依赖、
-- 无 spring.flyway.* 配置、manifest 无条目），仅保留语义原文。本变更的**生效落地**
-- 在 scripts/migration/0091_synap_claim_decision_triage_reason.sql（动态 DDL 幂等 +
-- bone-init.sql 建表块 + 文档真源 §9.5 三处同步）。
--
-- 一、claim_decision_execution 新增 triage_reason
--
-- 补的是什么洞：决策结论（decision_result）只说"落点"（AUTO_APPROVE /
--   MANUAL_REQUIRED / ...），分诊原因码（DecisionTriage.Outcome.reason）才说
--   "为什么"（RISK_HIT / AMOUNT_OVER_LIMIT / AI_CONFIDENCE_LOW / ...）。
--   此前原因码只存在于 POST /decision/execute 的响应里，读侧一律看不见：
--   ① 快捷赔付风险闸只能拦"建议拒赔"（SUGGEST_REJECT），拦不住"风控命中"
--      （RISK_HIT / FRAUD_GATE_NOT_PASSED）——内鬼/被盗账号对风控命中案件仍可
--      一键放款，这是资金分级授权的破口；
--   ② 核赔工作台/处置归因无法从库内证明"该案曾被风控拦截"，只能翻决策响应日志。
--
-- 为什么加列而不是从现有列推导：与 consumption_reason（V11）同一模式——原因码
--   取决于**执行当时**的证据/AI/风控事实，事后用当前配置与数据重算推不出来，
--   必须落库而非推导。可推导的假象比留空更危险。
--
-- 为什么允许 NULL：NULL = 异常中止未经分诊（fail() 路径，执行器只 complete 不
--   markTriageReason）或历史行无原因可记。快捷赔付风险闸对 NULL 不误拦——
--   闸只对"已知判否"（RISK_HIT / FRAUD_GATE_NOT_PASSED）与显式 SUGGEST_REJECT
--   生效，不做猜测式拒绝。历史数据不回填。
--
-- 与实体/执行器的接线：DecisionExecution @Column(triage_reason) +
--   markTriageReason（首写为准、空码拒绝）；DecisionGraphExecutor 在
--   execution.complete(...) 后、persist 前回填 outcome.reason()。
--   SDK 的 INSERT 列清单由实体 @Column 元数据生成 ⇒ 实体与 DDL 必须同批落地
--   （本通道不生效，故实体与 0091 必须同提交）。

ALTER TABLE claim_decision_execution
  ADD COLUMN triage_reason VARCHAR(32) DEFAULT NULL
  COMMENT '分诊受控原因码(RISK_HIT/AMOUNT_OVER_LIMIT/AI_CONFIDENCE_LOW/OCR_CONFIDENCE_LOW/FRAUD_GATE_NOT_PASSED/EVIDENCE_MISSING等); NULL=异常中止未经分诊或历史行无原因'
  AFTER duration_ms;
