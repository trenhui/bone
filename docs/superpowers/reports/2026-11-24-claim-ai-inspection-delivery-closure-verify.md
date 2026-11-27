# Verify 报告：claim-ai-inspection-delivery-closure

- **Change**: `claim-ai-inspection-delivery-closure`
- **类型**: Classic full（交付闭环收口，零 delta spec / 零生产代码 / 零 DDL）
- **基线**: `b2d0342fdaf4c33405d411dba61802a4f7f0a614`（其 `scripts/e2e/impact-map.json` 已与基线字节一致）
- **日期**: 2026-11-24
- **结论**: **PASS**

## 1. 目标与范围

旧 Run `claim-ai-inspection-full-coverage` 的业务实现、全量测试、OpenSpec 归档与主 spec 同步均已完成并推送，但 `complete-delivery` 因交付范围被误夹带的 `scripts/e2e/impact-map.json` 无法闭合。本 change 在干净基线上新建独立 Run，以官方 Guard 全程验证取得 `done` 终态，并把旧 Run 偏差作为可审计事实保留。

**非目标**：不回填旧 Run outcome、不标旧 Run completed、不改主 spec、不改生产代码、不伪造收据、不改写已推送历史。

## 2. 根因取证（源码实证）

| 证据 | 来源 |
| --- | --- |
| SDK 逐提交 `diff-tree --no-renames --name-only` 取 `baseCommit..HEAD` 路径并集，注释明确禁止「先夹带再撤销」 | `@rpamis/comet` 0.5.2 `classic-sdk-application.js:340-348` |
| `allowed = implementationFiles ∪ archiveFiles ∪ stateFiles`，`offendingPaths` 为差集 | 同文件 `430-438` |
| verify 收据 `deliveryInputs` 仅 7 个 Java/SQL 实现文件，不含 impact-map.json | 旧 Run 归档 `comet-state.yaml:1872-1879` |
| `repair-delivery` 明确拒绝「基线已追踪文件」 | `classic-sdk-delivery-repair.js:82-101` |
| `reopen-verification` 要求未归档且活动目录存在；`repair-archive` 要求 `archive.execute=failed` | `classic-cli-help.js:280-296`、`classic-sdk-revision-application.js:126-144` |

结论：四个官方恢复通道均不适用；新 Run 收口是唯一既满足 SDK 不变量、又不伪造/不改历史、且有成功先例（`procuraai-archive-delivery-closure`）的路径。

## 3. 构建与检查结果（全绿 / 如实登记）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| OpenSpec 严格校验 | `comet classic openspec validate ... --strict` | valid；artifact 闭包 proposal/specs(skipped)/design/tasks 全部 complete |
| synap-claim 全量测试 | `mvn -pl bone-apps/synap-claim/synap-claim-app -am test` | **BUILD SUCCESS**；`Tests run: 1674, Failures: 0, Errors: 0, Skipped: 8`（净通过 1666） |
| Build Guard | `comet guard claim-ai-inspection-delivery-closure build --apply -- <mvn ...>` | `ALL CHECKS PASSED — phase=verify`，RC=0 |
| 独立只读最终集成审查（两轮） | Explore 子代理独立审查 | 首轮 FAIL（1 IMPORTANT + 2 WARNING）→ 逐条修复 → 复审 **PASS**，无 CRITICAL / IMPORTANT；无新发现问题 |

### Verify 重跑与 flaky test 取证（如实登记）

首次 guard verify 的 mvn 全量命令因 `-am` 连带执行了与本 change 无关的 `bone-ai-rag` 并发时序测试
`RetrieveConcurrencyTest.vector_path_runs_concurrently_with_bm25_scoring`（报「检索总耗时 422ms 接近顺序执行，双路未并发」）而 exit=1；同一测试单独重跑 `mvn -pl bone-ai/bone-ai-rag -Dtest=RetrieveConcurrencyTest test` 为 `Tests run: 1, Failures: 0` + `BUILD SUCCESS`（1.519s），确认为负载时序波动导致的 flaky test，非真实缺陷、非本 change 引入（本 change 零生产代码改动）。SDK 按 verify 失败正规回退 Build，随后使用同一已验证命令重跑 verify。最终判据以重跑后 guard verify 的真实收据为准。

### Accepted Deviation（非阻断，预存红项）

| 检查 | 实测 | 说明 |
| --- | --- | --- |
| SynapClaim 文档族门禁 `python3 doc/ai-product/synapClaim/tools/check_all.py` | 18/21 PASS，3 项失败 | 3 项均为本 change 前已存在的预存红项：`_check_manifest` 2 份历史未登记文档、`_check_ruleset_alignment` A9 过时声明、`_ai_native_readiness.py --self-test`。与本 change 无关，判据为「无新增红项」；强行修绿会引入无关文档改动、重蹈 impact-map.json 夹带覆辙。已在 design D4 与 plan 登记。 |

## 4. 独立审查问题闭环

| 编号 | 级别 | 问题 | 修复 |
| --- | --- | --- | --- |
| I-1 | IMPORTANT | tasks.md 2.2 判据 `GATE_RC=0` 与 3 项预存红项事实冲突 | 2.2 判据改为「无新增红项 + 预存红项登记」；同步 proposal/design 口径；design 走 revise-design 重新批准 |
| W-1 | WARNING | design 引用的 SDK 源码不在工作树内，无法独立复核 | design 新增「源码取证锚点」小节，固化路径/版本 0.5.2/关键片段/复核方式 |
| W-2 | WARNING | .openspec.yaml 注释简化 skip_specs 因果 | 精确区分「skip_specs 只让 specs 合法 skipped」与「零 delta 使 findSpecUpdates 为空才不写主 spec」 |

复审结论 PASS，3 项全部修复，无新发现。

## 5. 交付范围声明

- 基线 `b2d0342fd` 到交付 HEAD 的每个提交均为连续单父提交，且只含本 change 的 proposal/design/tasks/brainstorm/plan/report/walkthrough/.comet 收据与归档产物。
- 交付提交精确 `git add`（绝不 `git add -A`），归档阶段以 `BONE_DATA_IMPACT=off` 关闭 impact-map 自动写回。
- 逐提交核对 `diff-tree --name-only` 不含 `scripts/e2e/impact-map.json`；`git diff --quiet <base> HEAD -- scripts/e2e/impact-map.json` 为真。
- 主 spec `openspec/specs/` 无新增 capability；旧归档目录未改动。

## 6. 验收场景核对

| 场景 | 证据 |
| --- | --- |
| 新 Run 走完整 Guard 流程取得 done | Open/Design/Build Guard 全 PASS；本 Verify 通过后进入 Archive/Delivery |
| 交付范围洁净 | 三方 SHA 一致、ahead/behind 0/0、impact-map 与基线一致（见 §5） |
| 测试不退化 | mvn 1674/0/8 BUILD SUCCESS；文档门禁无新增红项 |
| 偏差可审计 | 旧 Run 归档与偏差事实保留，未回填、未重放、未标 completed |
| 红线守护 | 无生产代码/DDL/资金路径改动、无主 spec 变更、无伪造收据、无改写历史 |

## 7. 结论

本 change 在干净基线上以官方 Guard 全程验证完成了交付闭环收口：Open/Design/Build 全 PASS，主判据 mvn 全量测试全绿，独立审查复审 PASS，交付范围洁净且不含误夹带的 impact-map.json，旧 Run 偏差作为可审计事实保留。**Verify 结论 PASS**，可进入 Archive 与 Delivery。

后续（Workflow follow-up）：`propose-archive(push) → guard archive --apply` 完成 OpenSpec 归档；精确暂存交付提交并推送 `origin` 双远程；`complete-delivery --commit <sha>` 取得 `done` 终态。若期间出现新并行提交污染交付范围，保持 Run 未闭合并如实登记，不绕过 Guard、不改写历史。
