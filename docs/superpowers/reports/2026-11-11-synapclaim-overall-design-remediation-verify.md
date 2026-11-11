# Verify 报告 · synapclaim-overall-design-remediation

> 阶段：Classic / tweak / SDK Run，phase=verify，verifyMode=light，reviewMode=off，产物语言 zh-CN。
> 日期：2026-11-11。本报告只记录真实执行过的检查与可复核的文件证据。

## 1. 范围与目标一致性

- 目标（proposal）：把 2026-11-11 独立诊断中「合理且可自主执行的文档侧整改」全部回写
  `04-design/总体技术方案.md`；两个已知 P0 资金缺陷只登记为 L3 交人项，不改代码 / DDL / CI。
- 实际改动（`git diff --stat`）：`doc/ai-product/synapClaim/04-design/总体技术方案.md`
  —— 22 处锚点替换，+30 / −20 行。另有本 change 的 OpenSpec 产物
  （`proposal.md` / `design.md` / `tasks.md` / `.openspec.yaml` / `comet-state.yaml`）与本报告。
  **无 Java / SQL / 前端 / DDL / 迁移 / pom / `scripts/check.sh` / CI 改动。**
- 非目标核对：未新建设计文档（D2）；未改 §4 归属表、基线行实测数字、键分类表唯一真源、
  §11.1 质量属性场景等 REQUIRED 锚点；共享树上 README / recovered-orphans / assets 等
  并行会话改动原样保留，未纳入本 change。

## 2. 任务完成与验收判据

- `tasks.md` **9/9 `[x]`**；`comet state tasks --assign-ids` 后 9 个稳定 ID、`needsIds=false`；
  `openspec instructions apply` 返回 `state: all_done`（total 9 / complete 9 / remaining 0）。
- `openspec status`：`proposal/design/tasks = done`，`specs = skipped`
  （纯文档 change，`.openspec.yaml` 的 `skip_specs: true` 合法），`isPlanningComplete=true`。
- 逐条判据回读（Python 脚本每处 `assert count(old)==1`，22 处全部唯一匹配、单次写回）：
  新增标记 `O-12` / `O-13` / `R11` / `M2.5` / `当前态快照` / `至多一个无条件实现` /
  `保留并原样写回` / `准入门槛` / `docs.manifest.json` 全部在位。
- 禁用串清零：「10 条登记」「文档总数保持 63」「文档数保持 63」「每一个有且仅有一个」
  「每个 Port 恰一个适配器」均 **0 命中**；
  `grep -cE '^\| \*\*O-[0-9]+'` = **13**（O-01..O-13 连续，新增两行均含「触发」）。

## 3. 真实检查与证据

| 检查 | 命令 | 结果 |
|---|---|---|
| 文档门禁全套 | `python3 doc/ai-product/synapClaim/tools/check_all.py` | **19/19 PASS**，末行「【OK】19 步全绿」 |
| 数字 / 语义判据自证 | `python3 doc/ai-product/synapClaim/tools/_check_numbers.py --self-test` | 全绿：4 正例 / 3 反例、oracle CI 5 探针、架构基线 8 探针、台账 5 探针、语义护栏 9 探针 |
| 交叉引用 | `python3 doc/ai-product/synapClaim/tools/_check_xref.py` | OK（§ 引用可解析、无遗留 synapClaim § 引用） |
| Build 检查（Runtime 真实执行） | `comet guard <change> build --apply -- python3 doc/ai-product/synapClaim/tools/check_all.py` | 两次真实执行均 exitCode 0；`inputBefore == inputAfter`、`changedDuringExecution=[]`、tier=full |

Runtime Build 收据：`.comet/runtime/classic-sdk-checks/9c11…/c1116ac6-84db-4e85-8892-80877ec497c9.log`，
contentHash `89e60450905ad906cf4a62aff05a016b7ecf4f8e6164cc239f7155d333d3997b`
（已用 `shasum -a 256` 与日志文件逐字对账一致）。

> 过程记录：SDK Run 的 build check 首次出现「领取 Action 后回执未提交」
> （`CLASSIC_ACTION_OUTCOME_UNKNOWN`）。按 `comet state check <change> build --recover --details`
> 取 `recordOutcomeRequest` 回填原结果，再在同一连续执行中取新 evidenceWait 模板提交
> `record-evidence`，两次均 `status: succeeded` 后 phase 进入 verify。
> 分开执行会因共享树输入快照变化触发 `EVIDENCE_REJECTED`，故两步必须连续。
> 这不改变检查结论：检查本身从未失败。

## 4. 逻辑自洽复核（本次整改的核心）

| 诊断项 | 修法 | 回读结论 |
|---|---|---|
| 里程碑 A 判据不可达 | ② 写侧双写 + ③ 3a 键级回落移入 A；③ 3b / ④ 留 B | §13.1 A 现含二者（均已落地、零行为变更）+ M2.5，「A 类键由引擎按 JSON 取值算钱」判据可达；B 只保留超集解锁；A 判据不再引用 B 交付物 |
| 两个 P0 资金缺陷无归处 | 新增 O-12 / O-13，设为 A 的准入门槛 | 两行各含现状、动作、触发、升级规则、L3 审批级别；A 判据写明修复须在 M2.5 窗口之前完成 |
| M2.5 无工程定义 | §9.1 补行、§11 补 R11、O-10 补指针 | 前置 / 验收 / 风险 / 回滚齐备，R11 回退动作为「关闭特性开关逐笔回退旧金额」 |
| §4.1 规约 2 判据失真 | 改为「至多一个无条件实现 + 至多一个 `@Primary` + 排除匿名类」 | 与《方案质量诊断报告》§14 实测一致；§2 读图要点与 §3 架构图同步 20 文件 / 23 接口口径 |
| ④ 读兼容层与三格矩阵 | 补「未知键保留并回写」；三格自动化验证列为 ④ 硬前置 | 依据 AWS Builders' Library 序列化实践与回滚安全纪律 |
| G3 / 数字 / 文档形态 | 一期口径拆分、计数改指针、§0 加当前态快照 | 目标与验收一致；不再写死条数与文档总数；评审入口与过程批注分离 |

## 5. 未执行项与已接受偏差

| 项 | 状态 | 说明 |
|---|---|---|
| O-12 / O-13 的代码修复 | **范围外 · 交人** | L3 资金行为变更，需架构师评审凭据；本次只登记台账与准入门槛，验收判据见《方案质量诊断报告》§14.1 |
| PITest 门禁接线、`engine_version` 落 DDL、跨发版 golden corpus | **范围外 · 交人** | L3/L4（CI 脚本 / 生产库迁移禁止 AI 执行），沿用既有 O-02 / O-11 触发条件 |
| 独立最终集成审查 | **按配置跳过** | 本 change `review_mode: off`（tweak 预设），按 sdk-methods §Verify 记录跳过原因，不以实现者自评冒充独立审查 |
| `verification-before-completion` Skill | **宿主不可用** | Skill 工具返回 `Can not find skill`；按宿主能力核对协议说明缺口，改用 Comet 自身 Verify 协议 + 直接真实机检代替，不宣称已加载 |
| `openspec-verify-change` 完整规格验证 | **不适用** | `skip_specs: true`，无 delta spec；按 light 级别核对 proposal / design / tasks 一致性（见 §2） |
| 新增 CI 语义判据（防 A 判据回退） | **范围外 · 交人** | 改 `_check_numbers.py` 属 L3；design.md 已登记变异验证口径，本轮未接线 |

## 6. 结论

**9/9 任务完成、8 类整改全部落地、19 步文档门禁与全部自证探针全绿，验证通过。**
逻辑自洽性缺陷（里程碑 A 判据不可达）已消除；两个已知 P0 资金缺陷已纳入台账并设为
准入门槛；数字与口径漂移已清零。未执行项均为范围外的 L3/L4 交人事项，
不构成本次验证的失败项。
