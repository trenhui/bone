## Purpose

定义 L3 就绪度复核器在判定「运行期模型覆盖」时的必要条件，防止仅凭节点自报的模型标识就让判据局部翻绿。

## ADDED Requirements

### Requirement: 运行期模型覆盖须满足四条件

`RUNTIME_MODEL_COVERAGE` 判为 `SATISFIED` **MUST** 同时满足：`provider_env` 非空（等价于 `PROVIDER_CONFIGURED` 通过）、E2E 产物 `runtime.probeVerdict == REACHABLE`、`runtime.probeEvidence` 证据束自洽（`httpStatus=200` / `model` 非空 / `provenance=PROVIDER` / `promptTokens>0` / `completionTokens>0` / `requestId` 合法）、`nodesScanned > 0`、`aiParticipated > 0`、`participationRate > 0`。任一不满足即 **MUST** 为 `BLOCKED`。

- provider 未配置的优先级 **MUST** 最高，此时 `reason` **MUST** 为 `PROVIDER_NOT_CONFIGURED`。
- 判词缺失或非 `REACHABLE` **MUST** 为 `PROBE_NOT_REACHABLE`（fail-closed），不得把「没测过」当成「通了」。
- 证据束缺失或自相矛盾 **MUST** 按 `PROBE_EVIDENCE_*` 阻断，不得回落到裸判词；采集侧未产出的字段 **MUST** 在 E2E 产物里写 `null`，不得编造。
- provider 已配置但参与证据为零时，`reason` **MUST** 为 `MODEL_PARTICIPATION_ZERO`。
- `detail` **MUST** 包含 `providerConfigured=` / `probeVerdict=` / `probeEvidence=`，使判定依据可审计。
- 无 E2E 产物时 **MUST** 仍为 `RUNTIME_ONLY` / `RUNTIME_EVIDENCE_REQUIRED`，不得因 provider 条件而降级为 `BLOCKED`。

#### Scenario: provider 未配置但参与率非零

- **WHEN** `provider_env` 为空，E2E 产物为 `nodes=88, aiParticipated=44, participationRate=0.5, probeVerdict=REACHABLE` 且证据束自洽
- **THEN** 判据为 `BLOCKED`，`reason=PROVIDER_NOT_CONFIGURED`，且 `detail` 含 `providerConfigured=false`

#### Scenario: 四条件齐备

- **WHEN** `provider_env` 非空，E2E 产物为 `nodes=88, aiParticipated=44, participationRate=0.5, probeVerdict=REACHABLE` 且证据束自洽
- **THEN** 判据为 `SATISFIED`，`reason=ADMITTED`

#### Scenario: 三要素全伪造但无证据束

- **WHEN** provider 已配、判词 `REACHABLE`、参与率非零，但 `probeEvidence` 缺失
- **THEN** 判据为 `BLOCKED`，`reason=PROBE_EVIDENCE_REQUIRED`

#### Scenario: 证据束自相矛盾

- **WHEN** 证据束里 `completionTokens=0` 或 `provenance=STUB` 或 `requestId` 为占位值
- **THEN** 判据为 `BLOCKED`，`reason` 为对应的 `PROBE_EVIDENCE_*`

#### Scenario: provider 已配置但参与率为零

- **WHEN** `provider_env` 非空，E2E 产物为 `nodes=88, aiParticipated=0, participationRate=0`，判词与证据束齐备
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
