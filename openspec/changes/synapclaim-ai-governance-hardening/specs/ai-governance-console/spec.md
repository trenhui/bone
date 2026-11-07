## Purpose

补齐 AI 治理只读面板的三处已写能力缺口：业务页面可达性、provider 连通性探活的手动纪律、校准明细的诚实渲染。

## ADDED Requirements

### Requirement: hidden 路由必须由业务页面深链可达

核赔工作台 `PageHeader.extra` **MUST** 提供进入 `/ai-governance` 的按钮；E2E **MUST** 通过真实点击该按钮进入治理页，不再以直接修改 `location.hash` 作为唯一入口。hidden 路由 **MUST** 不进 IAM 菜单，`claim-single-nav-probe.mjs` 的可见路由双向全等 **MUST** 不受影响。

#### Scenario: 真实点击进入

- **WHEN** 在核赔工作台点击「AI 治理」按钮
- **THEN** URL hash 变为 `#/ai-governance` 且 `#subapp-viewport` 渲染出治理页内容

#### Scenario: 菜单不新增条目

- **WHEN** 运行单导航探针
- **THEN** `/ai-governance` 不被计入可见路由，菜单顺序比对不判红

### Requirement: provider 探活只能手动触发

`GET /api/v1/claim-ops/ai-provider-probe` 每次真实消耗一次 provider 用量。页面默认加载与「刷新」都 **MUST NOT** 调用它，只有用户点击探活按钮才发起一次请求；结果 **MUST** 展示受控判词与处置动作，未知判词 **MUST** 显示原始码并告警。

- `verdict=NOT_CONFIGURED` **MUST** 显示「未配置真实 provider」，不得显示为「连通」。
- `verdict=REACHABLE` 只表示本次调用可达，**MUST NOT** 写成「模型判断准确」。
- 失败/未点击时 **MUST** 显示「未测过」，不得乐观映射成 0 或成功。

#### Scenario: 页面加载零探活请求

- **WHEN** 打开治理页且未点击探活按钮
- **THEN** 浏览器网络记录中 `ai-provider-probe` 请求数为 0

#### Scenario: 点击后展示真实判词

- **WHEN** 点击探活按钮且后端返回 `NOT_CONFIGURED`
- **THEN** 页面显示「未配置真实 provider」及对应处置动作，不显示「连通」

#### Scenario: 未知判词不乐观映射

- **WHEN** 后端返回字典外的判词 `SOME_NEW_VERDICT`
- **THEN** 页面显示原始码并置于警示态

### Requirement: 校准明细诚实渲染

前端 `CalibrationReport` 契约 **MUST** 包含 `buckets` 与 `slices`；页面 **MUST** 以只读表格展示分桶与切片明细。无证据时 **MUST NOT** 编造曲线、**MUST NOT** 把 `null` 写成 0。

- 后端 `verdict` 为 `NO_EVIDENCE` / `UNTRUSTED_EVIDENCE` 等禁止态时，**MUST NOT** 渲染分桶/切片表格，只显示对应判词与说明。
- 分桶 `meanConfidence` / `accuracy` / `gap` 与切片 `brierScore` / `accuracy` 的 `null` **MUST** 显示占位符。

#### Scenario: 无盲标样本不画曲线

- **WHEN** 校准报表 `verdict=NO_EVIDENCE` 且 `buckets=[]`、`slices=[]`
- **THEN** 页面不出现分桶/切片表格，显示「无盲标证据」及其说明

#### Scenario: 有样本时渲染明细

- **WHEN** 校准报表返回 2 个分桶与 1 个切片
- **THEN** 页面渲染出对应行，数值与后端一致

### Requirement: 展示层防假绿映射可证伪

`gatesMeta`、`metricText`、`participationMeta`、`verdictMeta`、`stageMeta`、`drillMeta`、`probeMeta`、`calibrationEvidenceMeta` 的负向用例 **MUST** 覆盖：`false` 不得显示达标、`null` 不得显示 0、未知受控码不得乐观映射、参与率 0 不得写成 AI 已上场。

#### Scenario: 映射被改成恒绿会判红

- **WHEN** 把任一映射函数改为恒返回「已达标」
- **THEN** 对应 vitest 负向用例失败
