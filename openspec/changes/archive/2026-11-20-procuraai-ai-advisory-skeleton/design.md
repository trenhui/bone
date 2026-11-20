# 设计：procuraAI 业务 AI 建议骨架（批次5）+ backlog 固化

## 架构决策

- **不新建待冻结端口**：源码实证 `ProcurementAiOrchestrationPort` 不存在，D3-v2 契约待 owner 冻结。本批复用已落地的 governance 应用服务（`AiGovernanceApplicationService.decide()` + `SafetyApplicationService.globalAllowsAi()`）作为裁决来源，避免越过 owner 门或制造第二真源。
- **纯工程骨架**：模块不注入 `LlmGatewayApplicationService`，不产生模型输出。`AdvisoryResponse` 只携带能力码、三态、双标识、证据引用、披露文案；`evidenceRef` 是 URI 引用，不回传 output 原文（禁 PII）。
- **fail-closed 汇总**：能力未登记 / 停用 / 降级 / 总闸 ENGAGED 一律 `aiAvailable=UNKNOWN` + `degradedTo=rules_only`；只有治理裁决显式 allowed 且总闸解除才 `AVAILABLE`。`advisoryOnly` 恒 true。
- **契约与门禁**：2 端点登记 openapi.yaml + A10 双向豁免（注入必红/还原必绿已验证）；权限码 0131（960 号段）；清单 order 135 连续。

## owner 边界（本批明确不做）

evalset 27→200（D-15）、D3-v2 八码契约冻结（D-16）、真模型接入与效果声明（D-17）、六卷 §6 端点对齐——全部在 DECISION-REGISTER 保持 Proposed，标注 owner 与前置依赖，不工程实现、不伪造通过线或 Kappa。

## 测试与负向

- 8 能力码齐全 + 未知码 fail-closed；三态不压平（UNKNOWN/DEGRADED 的 `aiActuallyParticipated()` 恒 false）。
- kill switch 关闭 / 治理裁决拒绝 / 未知能力码三种路径均断言 UNKNOWN + rules_only（反向证明非 fail-open）。
- 双标识缺一 `canRelease()` 为 false；advisoryOnly 恒 true。
- A10 豁免负向探针：删除 advice 端点豁免 → A10 转红，还原转绿。

## 门禁

spotless + advisory 17 例 + governance 82 例 clean test 全绿；A10 对齐 10/10；迁移清单 142 文件 / 135 可执行 / order 连续。
