# AI 文档区（`doc/ai-product`）

> 本目录收 **AI 产品与 AI 能力的方案与交付物**。2026-10-14 自 `doc/design/` 迁入归位。

## 归类原则（三条，按序过滤）

1. **主题判定**：文档主体是 AI（LLM / 智能体 / AI 能力 / AI 产品），AI 是主题而非点缀；
2. **归属判定**：只收"AI 产品/能力的**方案与交付物**"；教 AI 怎么干活、或被工具链引用的**工程规范/流程**留在工程域（见下"不入本目录"）；
3. **可迁性判定**：路径被工具链钉死、或拆散会破坏成套系列的，不迁（路径纪律优先）。

## 目录索引（单入口 + 双分区）

| 路径 | 说明 |
|------|------|
| [platform/](./platform/) | **AI 能力中台分区**（平台能力资产的方案与设计，随批次生长） |
| ├ [BONE-AI能力中台整体方案.md](./platform/BONE-AI能力中台整体方案.md) | **AI 能力中台唯一方案真源**（正式版：能力同构论证 + 模块架构（6 模块 + 评测挂流水线）+ 框架/许可定案 + 落地计划） |
| ├ [BONE-AI能力中台详细技术方案.md](./platform/BONE-AI能力中台详细技术方案.md) | **详设**：模块详设/契约与 DDL（拟定）/非功能阈值初值/第 0-1 批 WBS/平台 API 面 |
| └ [BONE-AI依赖与许可SBOM清单.md](./platform/BONE-AI依赖与许可SBOM清单.md) | 法务送审版 SBOM：依赖许可总表 + 禁用负表 + 5 项待确认点（POC 签前冻结选型） |
| [procuraAI/](./procuraAI/) | 采购 AI 专题包（PRD / 技术方案 / POC / 接口契约 / DEMO + assets） |
| [synapClaim/](./synapClaim/) | ClaimSphere 智能理赔专题包（PRD / 设计 / MVP / delivery 交付包 / DEMO） |
| [synapEquity/](./synapEquity/) | Synap Equity 估值专题包（产品方案 v0.18 / 方法学 v0.2 / 合规 v0.2 / 数据源 v0.2 / 技术 v0.2 / 评测 v0.2 / 开发 Backlog v0.2 / DEMO） |

**分区语义**：`platform/` 收 AI 能力的**方案与设计**（含将来的 bone-ai 模块设计、治理面/评测/OTel 对齐等，**替代**"详设散进 `doc/design/modules/`"的做法，modules/ 继续是元数据平台模块域，两不相扰）；三个产品目录是**成品快照**（PRD/DEMO/交付包，趋稳）。产品包是 platform 中台论证的输入旁证，中台是产品的能力上游。

## 不入本目录（含理由）

| 文档 | 不入理由 |
|------|----------|
| `doc/design/AI-夜间设计复核工作流方案.md` | 路径被 `scripts/prompts/01-global-anchor.md` 与 `_global-contracts.yaml` 夜间锚定契约钉死；性质是工作流程，非 AI 产品 |
| `doc/agenticx/` | Bone Agentic Engineering 工程宪法（与 CLAUDE.md/AGENTS.md/doc/agents 成套），工程规范 ≠ AI 产品 |
| `doc/agents/06-AI协作与编码准则.md` | 01–06 编号系列成套，属工程规范域 |
| `design/modules/`、`lowcode/`、`architecture/` 等 | BONE 平台本体设计，AI 只是章节点缀 |
