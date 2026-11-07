---
doc_id: records.evidence.index
role: navigation
currency: current
---

# 运行期证据（E2E / 复核器快照）

> 定位：把原本只存在于本机 `outputs/`（被 `.gitignore:290` 整目录忽略、不可复现不可审计）
> 的运行期产物，按 [11-L3外部前置运行手册.md](../../07-delivery/11-L3外部前置运行手册.md) §五
> 的建议版本化为**时点取证**（role=evidence，currency=frozen）。
> 证据只回答「某时点实跑结果是什么」，不承担当前口径；现状以 L3/L4 真源与
> `tools/_ai_native_readiness.py` 实时输出为准。

## 登记清单

| 文件 | 采集时间（UTC） | 采集 HEAD | 内容与诚实判读 |
|---|---|---|---|
| `2026-11-07-claims-e2e-result.md` | 2026-11-07T03:06:38Z | d953da0fc | 前后端 E2E（real-services-no-mock）：`passed=true`、API 断言 37 条；`runtime.probeVerdict=NOT_CONFIGURED`（provider 未配的**诚实降级**，非故障）、`aiParticipated=8`、`participationRate=0.0769`、窗口为 UTC 日界 30 天单一真源；`consoleErrors` 4 条均为探活端点 503（provider 未配置的预期响应，与判词同源）。JSON 逐字快照存于 md 围栏内 |
| `2026-11-07-ai-native-readiness.txt` | 2026-11-07（同日固化） | d953da0fc | 复核器同日输出：SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0，`l3Claimable=false`，可判定档位 L0——与上表 E2E 判词互相印证。文件头部 front-matter 为登记所需元数据，非复核器输出 |

## 采集与复现纪律

1. 产物来源是**本机一次性实跑**，不可复现、不可审计成本为零的前提是「逐字快照 + 登记 HEAD」；任何修改历史证据文件的行为视为篡改取证。
2. 参与率 / 探活判词只是该窗口的观测值，不构成「模型上场」达标证据；达标判定以复核器三条件为准（provider 已配 + `probeVerdict=REACHABLE` + 参与率非零）。
3. 后续每次实跑可按同命名追加 `YYYY-MM-DD-*` 快照并在此表登记；判据翻绿（如 `RUNTIME_MODEL_COVERAGE` 转 SATISFIED）时**必须**同时登记当次证据对。
