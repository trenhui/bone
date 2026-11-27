# Tasks

## 1. 持久化表与领域端口

- [x] 1.1 在 `bone-init.sql` 新增 `t_proc_contract_outbox` / `t_proc_contract_inbox` / `t_proc_contract_dlq` 三表（`tenant_id + review_id` 唯一约束、四类审计列、无 PII 列），登记 migration manifest；用 `check-ddl-required-columns.py --check` 与 DDL 对账测试验证零违规。 <!-- comet-task:e7cd1fc3-40d5-426c-838b-87057609b99c -->
- [x] 1.2 在 contract 域新增事件存储端口（Outbox 发布/待投递扫描、Inbox 条件插入去重、DLQ 写入/重放查询）并在 infrastructure 用 SDK `@EnableSqlRepositories` 实现（`@Column` 齐全、只用 `AbstractEntity` 的 `java.util.Date` 审计列）；用仓储单测验证 tenant 锚定定位、唯一键冲突与持久化读回。 <!-- comet-task:fbb6d9b9-7d52-40b3-bdf6-d790a90d8eed -->

## 2. 事务性发布与幂等消费

- [x] 2.1 把审查结论落库与 Outbox 插入纳入同一事务，新增待投递扫描驱动（显式/定时，不接生产 MQ）；用"发布前崩溃重启仍可投递"与"结论失败无孤立 Outbox"两组测试验证不丢事件、事务一致。 <!-- comet-task:9184ffe3-2213-4564-a5f2-8c0e5359069a -->
- [x] 2.2 把 `ContractReviewedConsumer` 从内存 Map 迁移到持久化 Inbox，以 `tenant_id + review_id` 条件插入判定幂等；用重复投递、重启后重投两组测试验证只产生一条结论、无第二条追溯记录。 <!-- comet-task:f9861496-ee46-46fd-af46-ceb059ba93d7 -->
- [x] 2.3 消费时以消费方 `TenantContext` 为准校验事件租户，不一致即拒绝并写拒绝留痕；用跨租户错投负向测试验证不落库、留痕含 review_id 与原因。 <!-- comet-task:f122d3d3-ecf7-4eba-93b9-7c67262809f8 -->
- [x] 2.4 消费失败写 DLQ（review_id + 失败原因 + payload 引用）且不留半条结论，提供按 `review_id` 重放入口；用失败入 DLQ、重放成功、重复重放不重复三组测试验证。 <!-- comet-task:2d4cb2a4-628e-45c5-ae2d-723b0eabee9a -->

## 3. 语义保持与契约同步

- [x] 3.1 保持 ContractReviewed 7 字段锁定、PII 禁回传、UNKNOWN 隔离转人工、AI 披露三态、人工推翻快照不可变；用现有 ContractReviewedEvent 测试 + 两条新增语义测试（UNKNOWN 持久化链路仍隔离、人工推翻快照不可变）验证无回归。 <!-- comet-task:c34d2cf5-9431-4dc8-a1fe-40e868665d5d -->
- [x] 3.2 同步 `asyncapi.yaml` 中 ContractReviewed 通道说明为 Outbox 发布 / Inbox 幂等消费 / DLQ 失败隔离，不改 payload 7 字段；用 `generate_asyncapi.py --check` 验证零漂移。 <!-- comet-task:118e3268-363e-443a-a0fb-508e8b3520a0 -->

## 4. 三入口门禁接入（L3）

- [x] 4.1 扩展 persistence 门禁守护新 3 表与必备列/唯一键，断言从 6 表/9 端口更新为新值；用 `check_procura_persistence.py` 与"删表必红/还原必绿"注入验证。 <!-- comet-task:dd1ebd3f-87d8-4404-b14c-043bf3870bb7 -->
- [x] 4.2 新增 event-chain 独立门禁脚本，守护三表 DDL + 幂等/跨租户/DLQ 三类负向探针源码可达；配 `--self-test` 注入反证（删探针必红、正例必绿）。 <!-- comet-task:2c3bbd51-190f-403d-afb9-bea7a5c2389b -->
- [x] 4.3 按 A10.17 / D-11 四入口一致流程在 `GATE_KEYS` 追加 `eventChainGate` + `eventChainProbe`，同步 `ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` + 门禁常量；用 `check_procura_alignment.py`（含 `--self-test`）验证三入口判据集合一致、注入删判据必红、还原必绿，措辞避开 A10.16 黑名单。 <!-- comet-task:a07d1216-74be-4bff-86ca-303bff93d5a1 -->

## 5. 集成验收

- [x] 5.1 运行 contract 模块与采购全量 Maven 测试，验证无失败/无跳过回归，所有写操作有持久化读回或拒绝后状态不变断言。 <!-- comet-task:d6953ea3-d15a-439e-8d19-a27a0530dadf -->
- [x] 5.2 运行前端 tsc / vitest / ESLint JSON 门禁、procuraAI 对齐 / evalset / AsyncAPI / frontmatter / 迁移清单，验证三入口改动未触发契约漂移。 <!-- comet-task:6a903bb9-915d-4edd-9f60-32b90bc6725d -->
- [x] 5.3 执行唯一最终集成审查，修复全部 CRITICAL/IMPORTANT，生成验收报告 PASS（含三类负向探针与三入口注入证据）。 <!-- comet-task:47df5deb-39a8-44f3-b3f4-6e744e68d867 -->

## Workflow follow-up

- 完成 Comet Verify 的唯一最终集成审查后再归档。
- 归档交付方式（local/push/pr）由用户在 Archive 阶段明确授权；未授权前不提交、不推送。
