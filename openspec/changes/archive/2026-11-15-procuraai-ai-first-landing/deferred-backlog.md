# Deferred Backlog — procuraAI AI-first 落地（未启动任务，独立 WBS）

> 本文件承载 `procuraai-ai-first-landing` change 在 2026-11-14 重定界时迁出的 **23 个未启动任务**（原 27 项中的组 1/2/4/5/6/7/8）。这些任务按原 `tasks.md` 纪律本就"未启动、属 owner/L3 程序级工作"，不属本次 MDM 首交付线，由 owner 排期独立交付。本文件不构成 Comet change 的任务清单，仅供追踪。

## 1. 就绪门（DoR：R-1~R-6，owner/架构师/各域负责人）
- 1.1 架构评审冻结 HLD+LLD+D3-v2+OpenAPI/AsyncAPI 版本戳，登记资产表（R-1）
- 1.2 `t_proc_*` 字段级 DDL 草案 + 架构师 L3 审批（AI 只出草案，不落库）（R-2）
- 1.3 模型选型决策 + 评测集扩到 ≥200（含对抗/偏见集）+ 分任务通过线冻结（R-3）
- 1.4 采购域数据分级目录 + 标注规范 + 训练/验证/冻结集三分离 + override 回流状态机（R-4）
- 1.5 跨域 ACL 对接（Finance InvoiceMatched/PaymentRequested、Item/Inventory/Warehouse 只读端口）+ Pact 契约（R-5）
- 1.6 NFR 容量/SLO/告警数值推算并冻结（R-6）

## 2. bone-ai 复用 spike（timeboxed ≤5 人日，工程+算法）
- 2.1 `bone-ai-document` 解析 + `ClauseSplitter` 切分验证（跨页不截断）
- 2.2 `bone-ai-orchestration` + `bone-ai-gateway` 审查编排与降级
- 2.3 是否/何时引入 `procuraai-runtime` 决策（D2 决策树，禁降通过线）

## 4. AI 合同审查 walking skeleton（工程）
- 4.1 合同上传 + 解析端点（解析失败 fail-closed）
- 4.2 风险标注与审查报告（条款锚点、跨页不截断、严重度、依据、置信度、不回传 PII）
- 4.3 HITL 复核 + 置信度分层路由（有意义监督三条件）
- 4.4 审查留痕 + AI 参与披露（三态）+ 日志留存可配置 + 降级/停用/推翻入追溯链

## 5. AI 决策治理（工程）
- 5.1 能力三态 fail-closed + 具名责任人 + 一键停用/降级开关与演练
- 5.2 人工最终决策权 + 推翻留痕 + override 回流台账
- 5.3 分任务评测门禁（precision/recall 并重、severity-weighted、关键负向=0、冻结集隔离、门禁自检）+ drift 告警

## 6. 存量 bone-app-purchase 收敛（工程）
- 6.1 付款/审批 handler 加 `procura.purchase-*-handler.enabled` 开关（缺省 false + fail-closed 三态）
- 6.2 PO 真源=新 Java 聚合，存量 handler 收敛为 legacy 演示 + 无双写验证

## 7. 门禁与负向探针（工程）
- 7.1 N1–N6 六条负向探针逐条转红并留痕
- 7.2 `check_procura_alignment.py` 全套 + `--self-test` 双绿 + CI blocking 记录

## 8. 集成验收（工程+owner）
- 8.1 walking skeleton 端到端脚本化演示（6 断言 + 5 负向 + 留痕导出）
- 8.2 跨域 Mock ACL 契约化联调（staleness fail-closed）

## 关联说明
- 原 3.5（Vendor 闸门接 WorkflowStatusPort + 数据质量）亦属部分交付、数据对接留待 owner，一并列入本 backlog 跟踪。
- 生产 JDBC adapter、bone-workflow-engine CR adapter、真实消费侧、Steward 具名署名、ADR 正式条目等同属 L3/owner。
