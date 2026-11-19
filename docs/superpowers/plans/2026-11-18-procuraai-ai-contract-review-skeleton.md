---
change: procuraai-ai-contract-review-skeleton
design-doc: openspec/changes/procuraai-ai-contract-review-skeleton/design.md
base-ref: 872cf440a8503db0a8e7056d356095974a9789af
baseline-head: 872cf440a
plan-revision: 1
---

<!-- comet-task-authority: openspec/changes/procuraai-ai-contract-review-skeleton/tasks.md -->

# Build Plan: procuraai-ai-contract-review-skeleton

- Design: `openspec/changes/procuraai-ai-contract-review-skeleton/design.md`
- Tasks: `openspec/changes/procuraai-ai-contract-review-skeleton/tasks.md`（16 项）
- Baseline: `872cf440a`
- Config: `autonomous / tdd / standard / subagent_dispatch=null`
- Scope: AI 合同审查 walking skeleton 首切片；复用现有 bone-ai-document/gateway/trace；不写生产库（L3）、不新增 CI 判据（A10.17）、不改主 spec。
- 红线：AI 仅辅助、人工终决、三态 fail-closed、PII 禁入、证据不回传 output 原文（D3-v2 §3）。

## 实施顺序（TDD：先红后绿）

1. 模块骨架 `bone-procurement-contract`（四层包 + 依赖）与默认关闭的能力开关；验证 reactor compile + 上下文装配测试。
   <!-- comet-task-ref:0f1e03c8-64fd-47e2-97cd-ee9b3039c885 -->
   <!-- comet-task-ref:64ba7613-63a6-4015-a704-b16b59682cdf -->
2. 领域模型与出站端口：`ContractReview` 状态机、`AiParticipation` 三态值对象、`RiskItem`、`ReviewSnapshotRef`；先写解析失败 fail-closed、UNKNOWN 不压 low 的失败测试。
   <!-- comet-task-ref:c8b67898-1e44-413a-8d84-1b2027b840e4 -->
3. `ContractParsePort` 复用 `DocumentParseApplicationService` + `ClauseSplitter`；跨页锚点完整单测 + 解析失败转人工负向。
   <!-- comet-task-ref:c193e3e8-4597-44cd-bb56-b17f972517d9 -->
4. 风险报告模型与 PII 禁回传：7 字段 payload 锁定、摘要哈希、快照引用；AI-3 注入含身份证必红。
   <!-- comet-task-ref:aaac48ef-cd12-4d5d-952d-011dbf9f40fb -->
5. HITL 确定性分层路由（低置信/高后果/命中负向 → 人工），高置信错误被人工推翻留痕。
   <!-- comet-task-ref:6f00e6b2-748b-40a8-8cd6-77c92428e82d -->
6. `ContractReviewPort` 接 `bone-ai-gateway`（LlmGateway + 注入防护 + 输出校验）；ai_available 节点自报缺省 UNKNOWN；PROMPT-INJ-01/02 注入必红、网关不可用降级 rules_only 转人工。
   <!-- comet-task-ref:acbfc04b-3c01-4012-9ca8-c765e98e1b53 -->
7. AI 参与披露三态映射 + 前端未知码原样显示；DISCLOSE-01/02、AI-4 负向与正向对照。
   <!-- comet-task-ref:6eaf63ba-cfce-4711-ad9d-e9e97bfb1f18 -->
8. `ContractReviewApplicationService` 四用例 + 租户 fail-closed + 持久化端口未就绪不装配。
   <!-- comet-task-ref:6e2a7416-53da-4734-aef1-49180b47ea34 -->
9. `ContractReviewController` 统一响应 + 权限点 + 契约对齐 openapi.yaml；越权 403、开关关闭路由不存在。
   <!-- comet-task-ref:526b5325-7a6f-40f6-a1d3-7294941cbaba -->
10. 消费 ContractReviewed：review_id 幂等、UNKNOWN 隔离、降级/停用/推翻入追溯链；M3-9 幂等用例。
    <!-- comet-task-ref:9e33d625-170e-4ae8-9aa9-d95c109567f8 -->
11. 权限码/菜单种子（幂等 SQL）+ 前端审查/披露入口；G32/G40 门禁。
    <!-- comet-task-ref:9ac96625-34d3-4834-91cc-b52d5cc2ac0c -->
12. 评测集对齐：testSymbol 指向真实源码、groundTruthRef 保持指向 D3-v2 真源（A16 约束不可指向源码）；check_procura_evalset.py 全套 + self-test 双绿。
    <!-- comet-task-ref:688c0204-1431-4901-912b-aed531f1821a -->
13. 六类负向探针注入必红/还原必绿（解析失败/PII/UNKNOWN 压低/版本冒充/隐藏指令/重复投递）。
    <!-- comet-task-ref:8669759c-391a-4652-8859-ce72abf7f58a -->
14. 全量回归：spotless + bone-procurement clean test + 文档族门禁 10/10&146/146 + 前端 tsc/eslint/vitest。
    <!-- comet-task-ref:0b9fe8d2-4adb-45e5-b489-74607ef81891 -->
15. 独立只读最终集成审查（Verify 阶段执行）：PASS、无 CRITICAL/IMPORTANT，报告落 docs/superpowers/reports/。
    <!-- comet-task-ref:49fc3cfa-6282-4e40-b12b-30a047491964 -->

## 完成判据

- 16/16 任务由 CLI `task-complete` 勾选。
- `mvn -o -f bone-apps/bone-procurement/pom.xml clean test` 全绿（含 ArchUnit）；前端 tsc + eslint(json errorCount=0) + vitest 全绿。
- `bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh` 主门禁 10/10 + self-test 146/146 不退化；`check_procura_evalset.py` 全套 + self-test 双绿。
- 六类负向探针注入必红、还原必绿并留痕；ContractReviewed payload 7 字段锁定、无 output/PII。
- 不改 FULL_GATE_KEYS、不写生产库、不新增主 spec；III 类 owner/地基决策项不混入。
- Verify 报告如实标注：walking skeleton 用受控夹具+降级路径验证行为，不宣称模型效果达标或生产可用。
