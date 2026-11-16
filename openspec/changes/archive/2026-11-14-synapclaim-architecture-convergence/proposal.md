## Why

D1/D3/D4 拍板后的父聚合迁移、M5a 流程配置真实消费、P36/P37 文档↔代码双向一致性已经落地，但 `doc/ai-product/synapClaim/04-design/方案质量诊断报告.md` §7/§8 登记的九项 Phase B / 目标态工作仍分散在设计稿、空壳模块与文档里，形成「文档写目标态、代码跑现状」的持续漂移。本 change 一次性规划并收口这些剩余项，把可落地项变成真实代码与可验证事实，把不能由 AI 执行的项变成可交付的人工窗口输入。

## What Changes

- 决策执行链路收敛到单一可验证口径：以代码现状（5 节点 / 4 类 `node_type`）为真源补齐 Stage → FlowFireService → Trigger → Condition 流程层与轨迹响应契约；把落地版 §7.3 里不自洽的「7 步链路 / 6 行节点表」改写为「现状 + 待评审扩展阶段」分层登记，同步总体技术方案 §0 事实基线。
- `synap-claim-sdk` 填入对外契约，`adjustment-engine` 迁入理算引擎并按报销 / 定额 / 津贴三策略显式拆分，附双轨对拍用例保证金额结果与既有 `AdjustmentEngine` 逐案一致。
- 配置面补 M5b 脚本沙箱：复用 `bone-engine` 的 `ExtensionSandbox` 边界，限制宿主资源访问、超时与步数，越界 fail-closed 且不写业务状态。
- Controller 出参全面显式响应 DTO 化并集中到 `adapter/web/assembler/`，消除 `ApiResponse<List<DecisionTraceNode>>` 一类领域对象直出（HC-003）。
- D4 的 20 个平铺子域按 7 限界上下文收敛包路径，保持 `quota` 归 Funds 的既有裁决不变。
- `AI技术方案.md` 瘦身：版本历史与实施日志外置，正文只保留规格与决策，避免与落地版重复口径。
- 文档族同步：诊断报告 §7/§8、整合历史、文档地图、`docs.manifest.json` / `AI-INDEX.md`、错误码登记与 zh-CN / en-US 文案。
- 全量验证后全量提交并 PUSH 到 `dev`。
- **BREAKING**：轨迹查询端点改为显式响应 DTO，不再直出领域对象；跳过 AI 评估仍无条件转人工，`FLOW_CONFIG_AI_SKIPPED` 语义保持不变。

## Capabilities

### New Capabilities

- `claim/decision-graph-orchestration`: 7 步决策链路、Stage/Fire/Trigger/Condition 流程层、M5b 脚本沙箱的受控执行边界与 fail-closed 行为。
- `claim/adjustment-strategy-parity`: 理算引擎三策略化、双轨金额等价、SDK / 引擎 / app 三模块依赖边界。
- `claim/web-response-contract`: Controller 统一响应 DTO 与 Assembler 单一真源，禁止领域对象直出。

### Modified Capabilities

无。本次新增三项行为契约；D4 包路径收敛、AI 技术方案瘦身、文档口径同步属于结构与文档治理，不改变既有 capability 的需求。

## Impact

- 代码：`bone-apps/synap-claim/` 三模块（app / sdk / adjustment-engine）、`adapter/web/`、`application/`、`domain/model/`、新增对拍与沙箱测试。
- 文档：`doc/ai-product/synapClaim/` 文档族、`doc/architecture/Bone-错误码登记.md`、`bone-frontend/packages/shared-utils/src/i18n/locales/`、`scripts/` 门禁脚本。
- 门禁：`mvn spotless:apply test`、`./scripts/check.sh`、synapClaim 文档机检 `check_all.py` 与 `_check_*.py`、错误码/i18n 机检必须全绿。
- 数据：M2 责任要素 DDL 本轮**不落地**。诊断报告 §7 明确登记其为 L4 禁止项且 R1–R8 未拍板；本轮只产出表设计与待拍板清单，不写 `scripts/migration/`。
- 交付：工作区全量提交（含并发会话已暂存文件）并 PUSH 到 `dev`，提交后按 `git branch --contains` 核对落点。
