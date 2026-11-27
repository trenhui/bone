# procuraAI 就绪度加固与前后端联调

## Why

上一轮 `procuraai-production-hardening` 已把 9 个端口切到 SDK 持久化并通过门禁，但采购 AI 仍是"工程骨架可达、能力不可用"：默认配置下建议态服务强依赖条件 Bean 存在装配断点；未配置 provider 时 `MockLlmClient` 回显可被误读为模型参与；没有任何只读端点如实公示"AI 参与/模型配置/评测状态/总闸"；证照 OCR 人工复核方法直接抛 `UnsupportedOperationException`；前端只有 4 条路由，没有人工终决与治理页面可操作。诊断报告 `outputs/procuraAI-未实现能力诊断与优化建议-2026-11-21.md` 给出 P0-1/P0-2/P0-6/P1-3/P1-4 五项工程侧可自主完成的缺口，且这些是"让已有代码真正可达、可判定、可操作"的前置。用户已确认本轮范围为就绪度 + 前后端联调，验收强度为真实启动 + 浏览器 + E2E。

## What Changes

- 统一 AI 相关组件的装配条件：`AdvisoryController` / `AdvisoryApplicationService` 与治理 Bean 使用同一条件开关，默认配置下不出现"控制器注册了但依赖 Bean 缺失"的启动失败；补默认配置与开启配置双态启动探针。
- 合同 AI 审查显式识别 `MockLlmClient` 身份：响应含 `mock:true` 或 provider 为 mock 时，结果降级为 `UNKNOWN` 三态并强制人工终决，不允许进入 `AVAILABLE`；启动日志与就绪度输出显式提示 provider 未配置。
- 新增只读端点 `GET /api/v1/procurement/ai-readiness`，从配置态、能力注册、总闸、评测状态四类事实推导 `AVAILABLE / DEGRADED / UNKNOWN`，并声明"部署门禁通过 ≠ 授标/放款许可"；未知判词显示原始码，不乐观映射。
- 实现 `reviewOcrByHuman`：具名复核人、持久化读回、幂等写入与拒绝重复/跨租户复核；替换 `UnsupportedOperationException`，补负向测试。
- 前端新增 AI 就绪度页、人工终决（HITL）工作台与治理状态页，复用现有请求封装与 `AiDisclosureCard` 风格；未知态显示原始码、取数失败显示"无法确认"。
- 前后端联调：本地真实启动后端与前端 dev server，用真实浏览器完成"打开就绪度页 → 触发合同审查 → 人工终决 → 刷新读回"路径，API 调用以数据库落库作为第二证据。
- 所有新增判据按 A10.17 纪律同步三入口（CI / check.sh / check-procura-ci.sh）与门禁常量，并配"注入必红 / 还原必绿"探针；前端 lint 以 `-f json` 的 `errorCount` 判定，不看 stylish 退出码。

## Capabilities

### New Capabilities

- `procurement/ai-readiness`: 采购 AI 运行期就绪度的可判定契约——配置/总闸/评测/能力注册四类事实如何推导受控判词、mock 身份如何降级、未知判词如何 fail-closed、以及"就绪 ≠ 放款许可"的边界。
- `procurement/human-review`: 采购高风险 AI 环节的人工终决可操作契约——OCR 复核与合同审查推翻的具名操作、幂等与跨租户拒绝、前端工作台可执行的确认/驳回与读回一致性。

### Modified Capabilities

无。本轮不修改任何已归档 spec 的需求（`production-persistence` 的租户隔离/幂等/审计契约保持不变）。

## Impact

- 后端：`bone-apps/bone-procurement` 下的 `bone-procurement-ai-advisory`、`bone-procurement-ai-governance`、`bone-procurement-contract`、`bone-procurement-qualification`、`bone-procurement-app`（装配与启动探针、readiness 端点、OCR 复核）。
- 前端：`bone-frontend/apps/bone-procurement-app`（新页面、路由、API 封装、组件测试）。
- 门禁：`tools/check_procura_alignment.py`（A10 判据/常量/负向探针）、`.github/workflows/ci.yml`、`scripts/check.sh`、`doc/ai-product/procuraAI/scripts/check-procura-ci.sh`。
- 不触碰：契约冻结（D-09/D-16）、评测转正 200 例与 `seededDraft` 状态（D-15/D-10）、Outbox/MQ 与真实生产 DDL（L3/D-05）、法务结论（D-08）、G1–G16 能力卡片、Award/Bid/Quotation/CAPA 业务聚合、旧族 8 份文档与 8 个受保护锚点。

## 验收边界

- 通过：默认配置启动无 Bean 缺失；mock 身份不返回 AVAILABLE；readiness 端点对四类事实给出受控判词且负面探针被判红；OCR 人工复核可持久化读回且重复/跨租户被拒；浏览器可完成端到端路径并有落库证据；三入口门禁全绿且注入探针必红。
- 不声称：真实模型效果达标、评测转正、契约冻结、生产 MQ/Outbox 闭环、真实生产库迁移。CI 绿只证明结构与 fail-closed 不变量，不等于 AI 可上线。
