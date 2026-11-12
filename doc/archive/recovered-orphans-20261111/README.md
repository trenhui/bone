# 孤儿文档恢复归档（recovered-orphans-20261111）

本目录收录从 Git 悬空对象（dangling objects）中恢复、**当前工作树与任何分支均缺失**的文档快照，归为历史归档——因位于 `archive/` 下，被 `scripts/check-doc-code-symbols.py` 的 `SKIP_DIRS` 跳过漂移检查（非现行文档，引用历史类名属预期）。

## 恢复来源
- **孤儿提交 `fa57d006`**：含 11 份 synapClaim 核验 / 联调 / 复核 / 诊断报告（2026-10-25 ～ 11-05），当前 `HEAD` 与磁盘均无，仅存于该孤儿提交（及 `/tmp` 备份）。
- **悬空 blob**：`doc_id: contract.ai`（智能理赔平台详细技术方案 bone-ai 落地版）的 5 个历史版本，当前树已无此 `doc_id`。

## 当前内容（12 份，已提交 `dev`）
- 11 份报告（见下）
- `contract.ai/contract.ai-98f54cb0.md`（5 个版本中内容最全的一份，176KB）

报告清单：
1. `dfd5eded-核验报告.md`
2. `全模块前后端联调验证报告-2026-11-05.md`
3. `智能理赔方案复核报告.md`
4. `核验报告-bba6ad30-智能理赔场景端到端验证.md`
5. `系统管模块功能诊断与优化报告.md`
6. `联调与功能验证报告-20261029.md`
7. `联调报告-20261025-全模块端到端验证.md`
8. `联调报告-20261026-全模块端到端验证.md`
9. `联调报告-Round7-20261029.md`
10. `联调报告-Round8-20261030.md`
11. `联调报告-Round9-20261031.md`

## 已精简
- `contract.ai` 5 个历史版本中删除 4 份冗余草稿（`1a4e6008` / `6ccf8210` / `9d62e2a1` / `a7dcd25f`），保留最全的 `98f54cb0`。被删 4 版仍可按原哈希从 `/tmp/bone-lost-found-backup/other/` 经 `git hash-object -w` 回灌。

## 备份位置（三重兜底，均不在本仓库内）
- `/tmp/bone-lost-found-backup/`：886 个悬空对象完整副本（含已删的 737 blob 文本转储与 149 个孤儿提交）。
- `/tmp/fa57d006-snapshot/`：`fa57d006` 整个树（11 份报告原貌）。
- `/tmp/bone-recovery/`：找回的 3 份 Comet 文件 + v2 方案。

## 仍保留未动（红线）
- 137 个带改动的孤儿提交：存于 `.git/lost-found/commit/` 及 `.git/objects/`，**未删未改**（仅删除 `.git/lost-found/other/` 这 737 个无用悬空 blob 的文本转储，真实对象仍在 `.git/objects/`，不受影响）。

## 提交记录（路径式、仅文档、零删除）
- `0be607624` 初救回 16 份（路径 `doc/recovered-orphans-20261111/`）
- `f1d140113` 移入 `doc/archive/` 跳过 `doc-code-symbols` 扫描
- `0586d079f` 精简 `contract.ai` 冗余版本（保留 `98f54cb0`）
- （本 README 提交）

## 门禁现况
- `doc-code-symbols`：移入 `archive/` 后已跳过，✅ 通过。
- `domain 分组布局`（`check-domain-model-layout.py`）：当前实测 ✅ 通过；门禁日志曾出现的 `Bad.java` 系该检查自测探针夹具（仓库内不存在），非真违规。
- `gate-liveness`（`check-gate-liveness.py`）：当前实测 ✅ 通过（62/64 已接线，3 个 CI-only、2 个孤儿豁免均已在 allowlist 登记）。
- 种子 `[G40]`「已过期」：`seed-bootstrap.py` 比的是「活库行数 ≠ 种子清单行数」的**数据一致性**检查，需数据库环境，属环境 / DB 层面既有问题，非本次文档变更引入，未改动。
