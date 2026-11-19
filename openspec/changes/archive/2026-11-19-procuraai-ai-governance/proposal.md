# 提案：procuraAI AI 决策治理（批次1）

## Why

合同审查 walking skeleton 已落地（2026-11-19 归档），但 `deferred-backlog.md` 组 5「AI 决策治理」仍是零实现：能力三态 fail-closed、具名责任人、一键停用/降级开关与演练、人工最终决策权 + override 回流台账、分任务评测门禁全部缺失。监管要求（金监总局指导意见、EU AI Act Art.14、NIST AI RMF、ISO 42001 Annex A.6.2.4）明确：AI 仅辅助、人工最终决策、具名责任人、可停用、override 可追溯并回流评测——这是任何选商/评标/审查类 AI 上线的前置治理闸门。

当前各 AI 能力（M2 合同审查等）各自内嵌部分治理逻辑，缺少统一的横切能力账本与准入裁决点，存在「未登记能力被调用」「停用态能力仍推理」「override 未回流评测」的风险。本批次把治理抽成独立限界上下文，提供统一 fail-closed 裁决入口与人工终决留痕，为后续 M1/M5/M7/G15 等能力提供可复用的治理锚点。

## What Changes

- **新增横切治理模块 `bone-procurement-ai-governance`**：DDD 四层（adapter/application/domain/infrastructure），独立能力账本聚合 `AiCapability`，不与合同审查上下文耦合。
- **能力三态 fail-closed + 具名责任人**：能力登记缺省 `UNKNOWN`（不放行），需具名责任人显式启用；`AVAILABLE/DISABLED/DEGRADED/UNKNOWN/UNDETERMINED` 五态状态机；`UNKNOWN/UNDETERMINED` 不可判定态不得压成可用。
- **一键停用/降级与停机演练**：`disable/degrade/enable/undetermined` 操作人取登录态、不接受自报；每次变更入中央追溯链，可审计、可演练。
- **准入裁决统一入口**：`decide(code)` 对未登记/停用/降级/不可判定一律拒绝，仅 `AVAILABLE` 放行且恒带「AI 仅辅助」语义（reasonCode=`ALLOWED_ADVISORY_ONLY`）。
- **人工终决 + override 回流台账**：AI 结论与人工终决分字段存储，复核人取登录态、强制耗时与理由；结论不一致识别为 override 并触发 `evaluationFeedbackRaised` 评测增补标记；`countOverrides` 作为 drift 告警输入。
- **能力开关与权限边界**：`bone.procurement.ai-governance.enabled`（默认 false），关闭时端点不注册；8 个 REST 端点读写权限分离，权限种子幂等登记。
- **可达性验证**：openapi.yaml 登记 8 端点 + 门禁豁免（源码先行，LLD §6 待对齐后移除豁免），本批次不新增 CI 判据集合、不改 FULL_GATE_KEYS。

**非目标（明确不做）**：F1–F4 地基层全量（数据集 ≥200 / 标注规范 / 漂移闭环）、评测脚本判据改动、分任务效果门禁数值冻结（D-10 owner）、kill switch 全域联动到所有 AI 能力（本批仅治理模块自身开关 + 能力级停用）、生产持久化 DDL 落库（L3，用受控内存实现跑通）、真实 bone-ai-trace 接线（L3）、自动结案/放款/授标（红线禁止）、法务法源结论（D-08）、CI 供应链加固（D-13）。上述按既有纪律单独立项，不伪造完成态。

## Capabilities

### New Capabilities

- `procurement/ai-decision-governance`: procuraAI 决策治理的可观察行为——能力三态 fail-closed 准入裁决、具名责任人、一键停用/降级与追溯、人工终决与 override 回流台账、能力开关与权限边界。

### Modified Capabilities

- （无。本次不改动现有 spec 级需求；合同审查 walking skeleton 保持独立，治理模块通过统一裁决入口供其后续接入，不改其 spec。）
