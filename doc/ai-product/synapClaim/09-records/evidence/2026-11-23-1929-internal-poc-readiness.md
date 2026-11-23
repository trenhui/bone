---
doc_id: records.evidence.readiness-internal-poc-rerun-202611231929
role: evidence
currency: frozen
---

# 2026-11-23 19:29 内部模拟 POC 复跑 · 复核器输出快照

> 采集 HEAD b24f9c5d130385f99f1eeab6f3ceaff21c6d954c · 运行前提：注入 `BONE_AI_GATEWAY_BASE_URL=http://127.0.0.1:18081/v1`、
> `BONE_CLAIM_DOCUMENT_PARSE_MODE=llm`（内部模拟 provider），消费本次实测产物
> `outputs/claims-e2e-result.internal-poc.json`（新 requestId `poc-a07a49c8…`）。
>
> 本次为同日复跑，首次 `2026-11-23-internal-poc-readiness.md`（requestId `poc-28c64ce5…`）原文保留。
> **下方为复核器逐字输出**：SATISFIED 10 / BLOCKED 2 / L2 / `l3Claimable=false`，
> 红线 2 项（CATALOG_ADMISSION / GOLIVE）仍 BLOCKED。
> `--self-test` 在剥离 provider 变量的干净环境全 PASS。

```
synapClaim · AI 原生 L3 就绪度复核（静态事实 + 前后端 E2E 运行期证据）
判据 12 项：SATISFIED 10 / BLOCKED 2 / RUNTIME_ONLY 0
当前可判定成熟度：L2（AI 承担部分主负载，仍待飞轮与 MRM）
l3Claimable = false
------------------------------------------------------------------------
  SATISFIED     PROVIDER_CONFIGURED          ADMITTED
  SATISFIED     BASELINE_WIRED               ADMITTED
  SATISFIED     FROZEN_EVALSET_ACTIVE        ADMITTED
  SATISFIED     EVAL_GATES_WIRED             ADMITTED
  SATISFIED     PERCEPTION_MODEL_DRIVEN      ADMITTED
  BLOCKED       CATALOG_ADMISSION_ACTIVE     CATALOG_POLICY_DEFAULT_NONE
  BLOCKED       GOLIVE_NOT_DRAFT             GOLIVE_DRAFT_13
  SATISFIED     FLYWHEEL_DOWNSTREAM          ADMITTED
  SATISFIED     IMAGE_PARSE_WIRED            ADMITTED
  SATISFIED     RAG_WIRED                    ADMITTED
  SATISFIED     ROLLOUT_ROLLBACK_DRILL       ADMITTED
  SATISFIED     RUNTIME_MODEL_COVERAGE       ADMITTED
------------------------------------------------------------------------
运行期证据（前后端 E2E 实跑）：nodes=148 aiParticipated=8 participationRate=0.0541 probeVerdict=REACHABLE probeEvidence=OK
说明：无 E2E 产物时该判据为 RUNTIME_ONLY 并阻断；provider 未配、探活非 REACHABLE、证据束缺失/自相矛盾或参与率为 0 均判 BLOCKED。
```
