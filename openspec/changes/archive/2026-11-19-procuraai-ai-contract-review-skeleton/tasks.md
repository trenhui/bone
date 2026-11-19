# Tasks — procuraAI 合同智能审查 walking skeleton

> 每个任务自带验证方式；按依赖顺序执行。全程 TDD：先写失败测试再实现。AI 输出仅为建议，人工终决、PII 禁入、三态 fail-closed 为不可稀释红线（D3-v2 §3）。

## 1. 模块骨架与能力开关

- [x] 1.1 在 `bone-apps/bone-procurement/pom.xml` 聚合器新增 `bone-procurement-contract` 模块（parent 指向聚合器，依赖 bone-core / bone-security / bone-web / bone-metadata-sdk / bone-ai-document / bone-ai-gateway / bone-ai-trace / spring-boot-starter-web / validation / test），建立 `com.bone.procurement.contract` 四层包骨架。验证：`mvn -o -f bone-apps/bone-procurement/pom.xml -pl bone-procurement-contract compile` 通过且模块出现在 reactor。 <!-- comet-task:0f1e03c8-64fd-47e2-97cd-ee9b3039c885 -->
- [x] 1.2 新增能力开关配置类 `bone.procurement.contract-ai-review.enabled`（默认 false），关闭时不装配审查 bean。验证：上下文测试断言关闭态不装配、开启态装配；沿用 `supplier-mdm` 已验证范式。 <!-- comet-task:64ba7613-63a6-4015-a704-b16b59682cdf -->

## 2. 端口与领域模型（D2/D4/D5）

- [x] 2.1 定义出站端口 `ContractParsePort` / `ContractReviewPort` / `ReviewTracePort` 及领域模型 `ContractReview`（状态机）、`RiskItem`、`AiParticipation`（AVAILABLE/UNAVAILABLE/UNKNOWN + 参与态 UNDETERMINED）、`ReviewSnapshotRef`。验证：端口契约单测；`AiParticipation` 覆盖 UNKNOWN/UNDETERMINED 不得压成 false 的用例（AI-4）。 <!-- comet-task:c8b67898-1e44-413a-8d84-1b2027b840e4 -->
- [x] 2.2 复用 `bone-ai-document` 的解析与切分（跨页不截断）：`ContractParsePort` 生产适配器委托 `DocumentParseApplicationService` + `ClauseSplitter`。验证：解析成功单测断言条款锚点完整；解析失败转人工负向用例先红（D3-v2 §3 fail-closed）。 <!-- comet-task:c193e3e8-4597-44cd-bb56-b17f972517d9 -->
- [x] 2.3 风险报告模型：带条款锚点 + 严重度 + 依据 + 置信度；`risk_items_hash` 摘要、快照只存 `review_snapshot_ref`；风险明细与 output 原文不进事件/库。验证：模型单测断言 payload 仅 7 字段、无 output/PII 字段；AI-3（注入含身份证）负向必红。 <!-- comet-task:aaac48ef-cd12-4d5d-952d-011dbf9f40fb -->
- [x] 2.4 HITL 置信度分层路由为确定性规则（低置信 / 高后果 / 命中负向 → 人工复核），LLM 不决定监督强度。验证：三类转人工用例各 1 例；"高置信错误被人工推翻"留痕用例通过、不被静默采纳。 <!-- comet-task:6f00e6b2-748b-40a8-8cd6-77c92428e82d -->

## 3. AI 调用、注入防护与披露（D2/D5）

- [x] 3.1 `ContractReviewPort` 生产适配器委托 `bone-ai-gateway`（`LlmGatewayApplicationService` + `HeuristicInjectionGuard` + `JsonOutputValidator`）；`ai_available` 由 LLM 节点自报，缺省 UNKNOWN fail-closed；`model_version_snapshot` 截断 128 且仅作快照。验证：注入必红探针 PROMPT-INJ-01/02（隐藏指令把 FORBIDDEN 判无风险）先红后绿；网关不可用时降级为 rules_only 且转人工、不跳过人工法审。 <!-- comet-task:acbfc04b-3c01-4012-9ca8-c765e98e1b53 -->
- [x] 3.2 AI 参与披露值对象与映射：后端返回原始码，前端对 UNKNOWN/UNDETERMINED 显示「无法确认」并保留原始码；`model_version_snapshot` 有值但 ai_available=UNKNOWN 判定「AI 未确认」（AI-4）。验证：DISCLOSE-01/02、AI-4 负向与正向对照用例全绿；`testSymbol` 源码可达。 <!-- comet-task:6eaf63ba-cfce-4711-ad9d-e9e97bfb1f18 -->

## 4. 应用服务、Controller 与契约对齐

- [x] 4.1 语义化 `ContractReviewApplicationService`：上传审查、查询报告、人工推翻、披露查询四个用例；租户从登录态取值不接自报，租户不一致 fail-closed；生产持久化端口未就绪时能力不注册。验证：应用服务测试覆盖四用例 + 跨租户拒绝 + 端口未就绪不装配。 <!-- comet-task:6e2a7416-53da-4734-aef1-49180b47ea34 -->
- [x] 4.2 `ContractReviewController`：`ApiResponse<T>` / `PageResult<T>` 响应；写操作 `procurement:contract:review:write`、读 `procurement:contract:review:read`；契约对齐 `contracts/openapi.yaml`（本次不新增 AsyncAPI 事件）。验证：HTTP 契约测试锁定 path / method / 响应字段；越权 403；开关关闭路由不存在。 <!-- comet-task:526b5325-7a6f-40f6-a1d3-7294941cbaba -->
- [x] 4.3 消费 `ContractReviewed`（INBOUND，`domain.procurement.contract_reviewed.v1`）：以 `review_id` 幂等，同一 id 只挂 1 条结论；`risk_level=UNKNOWN` 隔离告警转人工；降级 / 停用 / 推翻入追溯链，快照不可变。验证：M3-9 重复投递幂等用例、UNKNOWN 隔离用例、追溯链可回溯用例全绿。 <!-- comet-task:9e33d625-170e-4ae8-9aa9-d95c109567f8 -->
- [x] 4.4 权限码与菜单种子（幂等 SQL，绑超管）与前端入口（复用 `bone-procurement-app`）：审查/披露最小页面与路由。验证：G40 权限码门禁、G32 菜单路由一致性探针通过；SQL 重复执行无重复行。 <!-- comet-task:9ac96625-34d3-4834-91cc-b52d5cc2ac0c -->

## 5. 门禁、评测对齐与验收

- [x] 5.1 评测集对齐：`eval/ai-decision-evalset.json` 中 CONTRACT / DISCLOSE / PROMPT-INJ / AI-3/4/5 负向用例的 `testSymbol` 指向本 change 真实源码；`groundTruthRef` 保持指向 D3-v2 真源文档锚点（A16 约束不可指向源码 .java）；不扩容（仍 seededDraft）。验证：`check_procura_evalset.py` A14/A16/A17 与全套通过、`--self-test` 双绿。 <!-- comet-task:688c0204-1431-4901-912b-aed531f1821a -->
- [x] 5.2 负向探针注入必红 / 还原必绿：至少覆盖解析失败不产结论、PII 回传拦截、UNKNOWN 压低、版本快照冒充参与、隐藏指令注入、重复投递幂等六类，逐条记录命令与两次结果。验证：六类探针转红、还原后复绿，记录入 Verify 证据。 <!-- comet-task:8669759c-391a-4652-8859-ce72abf7f58a -->
- [x] 5.3 全量回归：`spotless:apply` 后 `mvn -o -f bone-apps/bone-procurement/pom.xml clean test` 全绿（含 ArchUnit 分层）；文档族门禁 `check-procura-ci.sh` 主门禁 10/10 + 自检 146/146 不退化；前端 tsc + eslint(json) + vitest 全绿（如纳入前端入口）。验证：三条命令均 RC=0，输出数字与真源一致。 <!-- comet-task:0b9fe8d2-4adb-45e5-b489-74607ef81891 -->
- [x] 5.4 独立只读最终集成审查（Verify 阶段）：结论 PASS、无 CRITICAL / IMPORTANT，产物落在 `docs/superpowers/reports/` 单层 zh-CN `.md`。验证：审查报告存在且结论 PASS；`comet guard ... verify` 通过。 <!-- comet-task:49fc3cfa-6282-4e40-b12b-30a047491964 -->

## Workflow follow-up

- Verify 通过后由 `/comet-archive` 完成归档与交付方式确认（local / push / PR）；本次规划不预设交付动作。
- 地基层 F1–F4、组 5 决策治理、法务射程结论、模型选型、评测集扩容 ≥200、`t_proc_*` 新表 DDL（L3）由 owner 另立 change；本 change 的 Walking skeleton 不声称模型效果达标或生产可用。
