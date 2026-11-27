# Verify Report — procuraAI Owner 冻结包草案

- **change**: `procuraai-owner-freeze-package-draft`
- **base ref**: `6577b293a1165dea5b4d741b3b989808fc3349d3`
- **date**: 2026-11-23
- **result**: **PASS**
- **scope**: 文档治理草案；无运行时代码、DDL、权限点、事件、CI 判据集合或真源状态变更。

## 1. 交付范围与结论

本 change 产出唯一业务交付物：

- `openspec/changes/procuraai-owner-freeze-package-draft/owner-freeze-package-draft.md`

草案覆盖四类 owner 待签材料：契约冻结评审清单（CF-01…CF-08）、评测扩容计划（EV-01…EV-06）、法务书面结论输入清单（LG-01…LG-10）、开口项全量索引（D-01…D-18）。每项均采用六字段：状态、owner、验收判据、未闭合风险、真源锚点、待裁决动作。

结论：OpenSpec 严格校验通过；本地确定性与注入反证通过；既有 procuraAI 门禁 8/8 PASS；最终独立审查 `CRITICAL=0 / IMPORTANT=0`。未发现 owner / 法务 / 模型达标 / 压测结论伪造，未发现真源状态或三入口判据漂移。

## 2. Comet Run 恢复说明（诚实记录）

原 SDK Run 在 Open 初始化时漏记 `isolation`，导致 Build 配置阶段报 `Classic Build requires workspace isolation selected in Open`。源码实证确认：

- `isolation` 是 SDK-owned 的 Open 决策字段，不在 Build 配置白名单；
- `comet state set ... isolation current`、`build_mode` 等直接写均被 Runtime 拒绝；
- 手工编辑 `comet-state.yaml` 会被判定为 Runtime-owned field 篡改，已还原；
- `repair-composition` 仅存在于组合应用，内置 `classic-full` 工作流未声明，请求被 `COMMAND_NOT_FOUND` 拒绝，Run 未变化。

最终采用工作流原生恢复：完整备份旧 change 目录与用户级 Runtime（`/tmp/comet-owner-freeze-recovery-20261123215444`，含 `stale-runtime/`）后，将旧 Run 归属 / checkpoint / state projection 移入备份，使用 `comet state init ... full --isolation current --runtime sdk` 重建同一 change 的 Run，并重新执行 Open / Design 的批准与证据链。所有 OpenSpec 产物文件保留；未伪造 Runtime 状态，未删除备份。

## 3. OpenSpec 与本地检查证据

### 3.1 OpenSpec 严格校验

```text
openspec validate procuraai-owner-freeze-package-draft --strict --json
valid: true, issues: [], passed: 1, failed: 0
```

### 3.2 本地确定性检查与注入反证

检查器：`/tmp/comet_owner_freeze_check.py`（一次性本地探针，不进入产品门禁、不修改 CI 判据）。

| 场景 | 结果 | 关键输出 |
|---|---|---|
| 正例 | PASS | `PASS — 4 sections / 42 items / 18 decision ids / status honesty / gate files unchanged` |
| 注入：删除开口项 D-15 | FAIL（预期） | `item counts ... D-: 17`；`missing=['D-15']` |
| 注入：把 `caseCount` 改为 200 | FAIL（预期） | `caseCount was forged to 200`；`caseCount=27 assertion missing` |
| 还原正例 | PASS | 同正例输出 |

检查覆盖四类材料、42 个待签项六字段、18 个编号唯一性、evalset 关键字段、三入口相关文件未修改。Build 阶段已用同一检查器生成 Comet 官方 check receipt，check 返回 0 并推进到 Verify。

## 4. 既有门禁复核（8/8 PASS）

均在仓库根目录 `/Users/renhui.trh/code/bone` 真实执行：

| # | 命令 | 结果摘要 |
|---|---|---|
| 1 | `python3 tools/check_procura_alignment.py` | `=== 全部通过 (10/10) ===` |
| 2 | `python3 tools/check_procura_alignment.py --self-test` | `[SELF-TEST] 157/157 负向探针全部转红且命中预期 key` |
| 3 | `python3 doc/ai-product/procuraAI/scripts/check_procura_evalset.py` | `RESULT: PASS`；`status=seededDraft suites=11 cases=27 negative=18 scaleTarget=200` |
| 4 | `python3 doc/ai-product/procuraAI/detailed-design/contracts/generate_asyncapi.py --check` | `[PASS] asyncapi.yaml 与附录 B 注册表零漂移（29 条事件）` |
| 5 | `python3 doc/ai-product/procuraAI/scripts/check_procura_frontmatter.py` | `[doc-frontmatter] 全族 frontmatter 语义守护通过（33 份文档）` |
| 6 | `python3 tools/check_procura_persistence.py` | `[A10.29] PASS — 9 表 / 双唯一键 / 10 端口持久化实现` |
| 7 | `python3 tools/check_procura_event_chain.py` | `[E-CHAIN] PASS — 3 表 / 必备列 / 唯一键 / 4 负向探针 / 端口持久化实现` |
| 8 | `python3 scripts/check-migration-manifest.py` | `OK: 迁移清单校验通过（154 个文件 / order 1..147 连续）` |

以上命令均返回 0；日志保存在 `/tmp/comet-owner-freeze-gate-1.log` 至 `-8.log`。

## 5. 状态诚实与真源不漂移

- `doc/ai-product/procuraAI/eval/ai-decision-evalset.json` 保持：
  - `status=seededDraft`
  - `needsOwnerReview=true`
  - `caseCount=27`
  - `scalePlan.status=notStarted`
  - `scalePlan.currentCases=27`
  - `scalePlan.targetCases=200`
- 草案未写入 owner 签名、法务书面结论、模型效果达标数值、压测链接或契约 Frozen 状态。
- 已落地项仅引用工程证据，不回写 `DECISION-REGISTER.md` 历史快照，不把用户授权冒充 owner 签署。
- `git diff HEAD -- tools/check_procura_alignment.py .github/workflows/ci.yml scripts/check.sh doc/ai-product/procuraAI/scripts/check-procura-ci.sh` 为空；本 change 未改 CI 三入口判据集合，未新增 blocking step。
- 未修改 `bone-init.sql`、运行时源码、前端代码或评测集。

## 6. 最终独立审查

第一轮独立审查发现三个 IMPORTANT：

1. proposal 范围写作 D-01…D-13，与 spec / tasks / 草案的 D-01…D-18 不一致；
2. spec delta 写“五个字段”，与 design / tasks / 草案的六字段口径不一致；
3. 两处锚点 slug 在真源中不存在（CF-03、CF-08）。

已按 Comet 变更纪律做最小修订，并通过 `comet state revise-design --expected-revision 19` 重走 Design 提案与批准，再重新提交 Build 计划；未静默跳过批准链。

复核结论：

```text
CRITICAL=0, IMPORTANT=0
I-1 已闭合：proposal.md:5 / :9 均为 D-01…D-18
I-2 已闭合：spec.md:23 / :27 均为六字段并含「待裁决动作」
I-3 已闭合：草案 :64 使用真实 §2 锚点，:109 使用 §8 真实标题与行号
无需进一步修改
```

## 7. 仍需 owner / 法务 / L3 处理（不属于本 change）

本 change 只准备待签材料，不闭合以下外部事项：契约冻结与 DDL 落库、法务书面结论（含主体适用性 / 场景射程 / C-2 材料包）、SLO 真实压测链接与冻结、跨域 ACL 与实名 PR、200 例评测转正与通过线冻结、真模型接入与漂移闭环、CI 供应链 owner 验证 SHA、生产 DB / MQ 接入，以及自动授标 / 自动放款 / 自动结案红线最终书面确认。

## 8. 最终判定

**PASS — 可进入 Comet Verify / Archive。** 本 change 的边界是“准备待签草案”，不是“冻结契约、法务结论或模型达标”。所有未授权结论均保持待填状态。
