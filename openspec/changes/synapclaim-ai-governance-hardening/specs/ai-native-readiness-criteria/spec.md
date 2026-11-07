## Purpose

定义 L3 就绪度复核器在判定「运行期模型覆盖」时的必要条件，防止仅凭节点自报的模型标识就让判据局部翻绿。

## ADDED Requirements

### Requirement: 运行期模型覆盖须满足 provider 双条件

`RUNTIME_MODEL_COVERAGE` 判为 `SATISFIED` **MUST** 同时满足：`provider_env` 非空（等价于 `PROVIDER_CONFIGURED` 通过）、E2E 产物 `nodesScanned > 0`、`aiParticipated > 0`、`participationRate > 0`。任一不满足即 **MUST** 为 `BLOCKED`。

- provider 未配置的优先级 **MUST** 高于参与率归因，此时 `reason` **MUST** 为 `PROVIDER_NOT_CONFIGURED`。
- provider 已配置但参与证据为零时，`reason` **MUST** 为 `MODEL_PARTICIPATION_ZERO`。
- `detail` **MUST** 包含 `providerConfigured=<true|false>`，使判定依据可审计。
- 无 E2E 产物时 **MUST** 仍为 `RUNTIME_ONLY` / `RUNTIME_EVIDENCE_REQUIRED`，不得因 provider 条件而降级为 `BLOCKED`。

#### Scenario: provider 未配置但参与率非零

- **WHEN** `provider_env` 为空，E2E 产物为 `nodes=88, aiParticipated=44, participationRate=0.5`
- **THEN** 判据为 `BLOCKED`，`reason=PROVIDER_NOT_CONFIGURED`，且 `detail` 含 `providerConfigured=false`

#### Scenario: provider 已配置且参与证据齐全

- **WHEN** `provider_env` 非空，E2E 产物为 `nodes=88, aiParticipated=44, participationRate=0.5`
- **THEN** 判据为 `SATISFIED`，`reason=ADMITTED`

#### Scenario: provider 已配置但参与率为零

- **WHEN** `provider_env` 非空，E2E 产物为 `nodes=88, aiParticipated=0, participationRate=0`
- **THEN** 判据为 `BLOCKED`，`reason=MODEL_PARTICIPATION_ZERO`

### Requirement: 判据必须可证伪

`--self-test` **MUST** 包含能翻转该判据的正向探针，以及「仅伪造参与率而 provider 未配不得翻绿」的负向探针；负向探针未被判红时自测整体 **MUST** 失败。

#### Scenario: 摘除 provider 条件会造假绿

- **WHEN** 把判据实现中的 provider 条件移除，并注入 `participationRate=0.5`
- **THEN** 负向探针报告 FAIL，自测退出码非零

#### Scenario: 正向探针仍有判别力

- **WHEN** 同时注入 `provider_env` 与 `participationRate=0.5`
- **THEN** 判据由 `BLOCKED` 翻转为 `SATISFIED`

### Requirement: 输出契约不漂移

复核器的 `l3Claimable = true|false`、`判据 N 项：SATISFIED x / BLOCKED y / RUNTIME_ONLY z`、`  BLOCKED <id> <reason>` 三处文本格式 **MUST** 保持不变，以维持 E2E C 组对账正则有效。

#### Scenario: C 组对账仍可解析

- **WHEN** E2E 执行 C 组并调用复核器
- **THEN** 三条正则均可匹配，且 `l3Claimable` 仍为 `false`
