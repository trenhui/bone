## Why

上一轮前后端 E2E 已能真实渲染 AI 治理面板，但循环复核暴露三处**编码可修、且不属于外部数据/审批**的缺口：

1. **证据窗口分裂**：E2E 产物用固定窗口（`nodes=88 / aiParticipated=0`），治理面板用滚动 7 天窗口（同日实测 `aiParticipated=8 / rate≈8.79%`）。同一指标两个值，复核器与面板互不引用对方的窗口，无法判断差异来自数据还是口径。
2. **局部假绿风险**：`_ai_native_readiness.py` 的 `RUNTIME_MODEL_COVERAGE` 只看 `nodes/participationRate/aiParticipated` 三个数。而 `AiModelProvenance` 用黑名单判定（模型标识非空且非 `local-stub`/`mock*` 即 `PROVIDER`），在 `PROVIDER_CONFIGURED=AI_GATEWAY_BASE_URL_MISSING` 未解除时也可能留下这类节点。只要窗口换成有参与节点的区间，该判据就会单独翻绿。
3. **已写能力零消费**：`GET /api/v1/claim-ops/ai-provider-probe` 有 Controller/Service/Adapter 但前端 0 引用；后端 `CalibrationReport.buckets/slices` 已真实计算但前端类型与页面未消费；`/ai-governance` 是 hidden 路由，业务页面无任何进入按钮，唯一入口是 E2E 里手改 `location.hash`。

同时 `04-design/BONE-AI能力依赖图谱.md` 与 `00-governance/01-文档治理规约.md` 的窗口天数、端点数口径已滞后于代码。

## What Changes

- `RUNTIME_MODEL_COVERAGE` 达标判定叠加 `PROVIDER_CONFIGURED` 双条件；新增「仅伪造参与率不得翻绿」负向探针与机检单测。
- 把 E2E 产物与治理面板的证据窗口统一为**UTC 日界 30 天单一真源**：前端 `governanceWindow()` 与 E2E `EVIDENCE_WINDOW` 用同一算法，新增「窗口同源」断言；复核器输出格式与 C 组三条正则保持不变。
- 核赔工作台 `PageHeader.extra` 增加 `/ai-governance` 深链按钮；E2E 用真实点击替换直接改 hash，并保留 negative 自检。
- 治理页接入 provider 连通性探活：**手动按钮触发**，默认不自动请求（端点每次真实消耗一次 provider 用量），带防假绿判词映射与「未点击时零探活请求」断言。
- 治理页渲染校准 `buckets`/`slices` 明细；`NO_EVIDENCE` 等无证据态不画曲线、不编造数值。
- 同步两份文档口径（窗口天数、第 6 个治理端点、判据说明）；补齐 vitest 负向用例、E2E 断言、Python 机检单测。

明确不做（非目标）：

- 不把 7 个 `BLOCKED` 外部条件写成达标，不改 `l3Claimable` 的 `false` 结论。
- 不改 `baseline-metrics.json` 的 `declared` 状态、不改 V15 的 `DEFAULT 'NONE'`、不改 `application.yml` 默认值、不改 go-live 分册的 `draft` 状态——这些需要真实基线数据/目录主数据/provider 密钥/内审转正。
- 不改决策 DAG 拓扑、不新增节点、不改任何 DDL/迁移、不改 `scripts/check.sh` 或 CI（L3 需架构师审批）。
- 不改 `AiRuntimeHealthApplicationService.aggregate` 的统计口径：运行期健康度端点保持客观观测值，「是否算模型上场」的达标判定放在复核器侧。
- 不新增模型清单端点：`AiModelCard` 是纯领域内核，尚无真实模型卡数据源，凭空生成即为造假。

## Capabilities

### New Capabilities

- `ai-native-readiness-criteria`: L3 就绪度复核器对运行期模型覆盖的达标判定，必须同时满足 provider 已配置与真实参与证据；含负向探针可证伪性。
- `evidence-window-contract`: E2E 运行期证据与治理面板使用同一 UTC 日界窗口，窗口字符串可跨端逐字对账。
- `ai-governance-console`: AI 治理只读面板的可达性、provider 连通性探活的手动触发纪律、校准分桶/切片明细的诚实渲染（无证据不编造）。

### Modified Capabilities

无。本 change 不修改既有 OpenSpec 规格需求，只新增上述三项能力的行为契约。

## Impact

- 前端：`bone-frontend/apps/bone-claim-app/src/services/aiGovernance.ts`、同目录 `aiGovernance.test.ts`、`pages/AiGovernancePage.tsx`、`pages/ReviewWorkbenchPage.tsx`。
- 测试：`bone-frontend/e2e/synap-claim-e2e.cjs`、`scripts/tests/test_ai_native_readiness_flywheel.py`。
- 治理：`doc/ai-product/synapClaim/tools/_ai_native_readiness.py`、同目录文档两份。
- 后端：无 Java 生产代码改动；新增消费的 `ai-provider-probe` 端点已存在且只读。
- 数据/权限/迁移：无变更。
