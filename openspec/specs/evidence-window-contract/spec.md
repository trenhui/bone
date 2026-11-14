# evidence-window-contract Specification

## Purpose
把 E2E 运行期证据与治理面板的统计窗口收敛为同一份 UTC 日界定义，消除「同一指标两个值」不可判因的口径分裂。

## Requirements

### Requirement: 单一 UTC 日界窗口定义

`governanceWindow()` **MUST** 以 UTC 00:00 为日界，返回 `[to-30天, to]`，其中 `to` 为下一个 UTC 日界（覆盖当日全天）；窗口长度 **MUST** 恰为 30 天且严格小于端点的 31 天上限。E2E 的 `EVIDENCE_WINDOW` **MUST** 使用同一算法，二者产出的 `from`/`to` 字符串 **MUST** 逐字相同。

- 同一 UTC 日内多次调用 **MUST** 返回完全相同的字符串，不受调用时刻的毫秒差影响。
- **MUST NOT** 使用「调用时刻 - N 天」的滚动毫秒窗口：不同端相差数秒就会跨过决策轨迹节点导致分母变化。

#### Scenario: 同日多次调用恒定

- **WHEN** 在同一 UTC 日内相隔数秒调用两次 `governanceWindow()`
- **THEN** 两次的 `from` 与 `to` 完全相等

#### Scenario: 窗口长度合规

- **WHEN** 计算 `Date.parse(to) - Date.parse(from)`
- **THEN** 恰为 30 天，且 `from`/`to` 均为 ISO-8601 UTC 字符串

### Requirement: 前后端窗口逐字对账

治理面板 **MUST** 把当前统计窗口以 `统计窗口：<from> ~ <to>` 的文本渲染出来；E2E **MUST** 解析该文本，用其 `from`/`to` 重新请求 `ai-runtime-health` 并重算参与率期望值，同时断言面板声明窗口与 `EVIDENCE_WINDOW` 逐字一致。

- 面板参与率展示 **MUST** 等于同窗口 API 独立重算的期望标签（`无可计算样本` / `模型未参与` / `xx.xx%`）。
- 面板 **MUST NOT** 出现 `AI 已上场`、`100.00%` 等宣称文本。

#### Scenario: 窗口漂移必须对账失败

- **WHEN** 面板声明的 `from`/`to` 与 `EVIDENCE_WINDOW` 不一致
- **THEN** E2E 的「证据窗口同源」断言判红

#### Scenario: 参与率跨语言对账

- **WHEN** 同窗口 API 返回 `participationRate=0.0879`
- **THEN** 面板展示 `8.79%`，且期望标签断言通过

### Requirement: 复核器证据字段保持兼容

E2E 产物 `runtime` 段中复核器读取的 `nodesScanned`、`aiParticipated`、`participationRate`、`capabilityMissing` 语义 **MUST** 保持不变；新增窗口字段只作元数据，**MUST NOT** 改变任何判据的输入面。

#### Scenario: L3 结论不被窗口统一翻绿

- **WHEN** 统一窗口后 E2E 产物参与率变为非零
- **THEN** `l3Claimable` 仍为 `false`，`RUNTIME_MODEL_COVERAGE` 因 provider 未配置仍为 `BLOCKED`
