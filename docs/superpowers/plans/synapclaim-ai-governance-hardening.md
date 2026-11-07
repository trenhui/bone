---
comet_change: synapclaim-ai-governance-hardening
role: implementation-plan
canonical_spec: openspec
---

# synapClaim AI 治理收口实施计划

设计真源：`docs/superpowers/design/synapclaim-ai-governance-hardening.md`；任务真源：`openspec/changes/synapclaim-ai-governance-hardening/tasks.md`。

执行顺序遵循依赖：先收紧判据（D1），再统一窗口（D2），其后是三个前端缺口（D3/D4/D5），最后文档同步与全套验证。D2 会改变 E2E 产物参与率，D1 必须先落地，否则 `RUNTIME_MODEL_COVERAGE` 假绿。

## Step 1 — 收紧 L3 判据

- `doc/ai-product/synapClaim/tools/_ai_native_readiness.py`：`RUNTIME_MODEL_COVERAGE` 叠加 `provider_env` 双条件；三态 reason；detail 附 `providerConfigured=`。
- 同文件 `--self-test`：参与率正向探针改为「provider 已配 + rate=0.5」才翻转；新增「仅伪造参与率而 provider 未配不得翻绿」负向探针，失败使自测整体非零。
- `scripts/tests/test_ai_native_readiness_flywheel.py`：补两条表征测试（双条件正例 / 仅伪造参与率反例），保留既有 `test_self_test_probes_all_flip`。
- 第 10 轮增量：再补 `runtime.probeVerdict == REACHABLE` 第三条件（§11.7「配了 ≠ 通了」）；E2E 复用同一次手动探活响应把判词写入产物，不新增请求；`--self-test` 与表征测试补「探活失败 / 判词缺失 / 判词伪造 / 摘除条件」负向钉子。
- 验证：`python3 tools/_ai_native_readiness.py --self-test`、`python3 -m unittest -v scripts.tests.test_ai_native_readiness_flywheel`（项目用 unittest，非 pytest）。

## Step 2 — 证据窗口单一真源

- `bone-frontend/apps/bone-claim-app/src/services/aiGovernance.ts`：`governanceWindow()` 改为 UTC 日界 30 天；`AiGovernancePage.tsx` 去掉显式 `7`。
- `aiGovernance.test.ts`：窗口断言改 30 天；新增「同日多次调用恒定」「长度恰 30 天」负向用例。
- `bone-frontend/e2e/synap-claim-e2e.cjs`：顶部 `EVIDENCE_WINDOW` 同算法；A 组 health 与 `result.runtime` 同源；B 组新增窗口逐字对账断言；C 组三条正则不动。
- 验证：`npm test`、`tsc --noEmit`、E2E 实跑。

## Step 3 — hidden 路由深链入口

- `ReviewWorkbenchPage.tsx`：`PageHeader.extra` 加「AI 治理」按钮 `navigate('/ai-governance')`。
- E2E：真实点击进入，保留改 hash 作为回退负向自检；跑 `claim-single-nav-probe.mjs` 确认菜单双向全等不受影响。

## Step 4 — provider 探活手动接入

- `aiGovernance.ts`：`ProviderProbeReport`、`providerProbe()`、`probeMeta()`（受控判词 + 未知码降级 + 失败态「未测过」）。
- `AiGovernancePage.tsx`：独立 state + 按钮触发；默认加载与「刷新」不触发。
- `aiGovernance.test.ts`：`probeMeta` 负向用例。
- E2E：`page.on('request')` 统计，进入页面 0 次、点击后 1 次。

## Step 5 — 校准曲线明细

- `aiGovernance.ts`：`CalibrationReport` 补 `buckets`/`slices`；`calibrationEvidenceMeta()`。
- `AiGovernancePage.tsx`：仅当证据态允许且有明细时渲染分桶/切片表格；`null` 显示占位符。
- `aiGovernance.test.ts`：空证据与禁止态负向用例；E2E 断言 `NO_EVIDENCE` 时无分桶表格。

## Step 6 — 文档口径同步

- `00-governance/01-文档治理规约.md`：治理端点 5→6。
- `04-design/BONE-AI能力依赖图谱.md` §9.1 第 5 条：改为 UTC 日界 30 天单一真源，补 D1 判据说明。
- 跑六道文档机检（含 `_check_ai_native_calibration.py --self-test`）。

## Step 7 — 全套验证与如实收口

- `./scripts/check.sh`、`mvn -o -pl bone-apps/synap-claim test`、E2E、vitest、tsc、Python 机检、文档机检。
- 复核 `_ai_native_readiness.py`：`l3Claimable=false`、7 项 BLOCKED 保持；E2E 产物如实记录新窗口与参与率。
- 提交按路径式隔离并发会话在途文件；不把 BLOCKED 改小。

## Step 8 — 第 11 轮增量（成熟度档位 + 终态对账 + 文档族收尾）

- `_ai_native_readiness.py`：`current_level()` 受 `RUNTIME_MODEL_COVERAGE` 约束，未达 SATISFIED 回落 L0（带 reason）；脚本说明「刻意不做」补第五条。
- `test_ai_native_readiness_flywheel.py`：新增 `MaturityLevelTest` 7 例，含合规正例 L2 与「退化成配置态」源码变异。
- `synap-claim-e2e.cjs`：最终产物落盘后再跑一次复核器做终态对账；补 `probeVerdictCapturedAt` / `finalReadinessSnapshot`；端点计数文案改为「默认加载 5 个，第 6 个手动触发」。
- 文档族：`README.md` / `00-文档地图.md` 改 `00`–`11`，manifest `aiRouting` 补两条 guide 路由，治理规约全套清单补 `--check`，重跑生成器。
- 验证：54 例 unittest、E2E 37/37、六道机检 + `_gen_ai_index.py --check`、`check.sh`、Maven clean test。

## Step 9 — 第 12 轮增量（自检档位探针 + 文档族一致性收口）

- `_ai_native_readiness.py`：`self_test()` 增第四类「成熟度档位」3 条探针，`main()` 输出改「四类探针」；docstring 说明为何不能只靠 pre-commit 单测。
- `test_ai_native_readiness_flywheel.py`：增 1 例「退化 `current_level` 令 `--self-test` 判红」变异测试。
- `docs.manifest.json`：登记已入库的 `review.round10` + `aiRouting` 路由；重跑 `_gen_ai_index.py`。
- 治理规约 / 能力依赖图谱 / 运行手册：统一为「四类探针、条数以脚本为准」，去掉硬编码计数。
- 验证：55 例 unittest、六道机检 + `_gen_ai_index.py --check`、E2E、`check.sh`、Maven clean test；锁释放后按路径式暂存。

## 验收判据

每项改动都有可证伪的负向用例；改任一映射为恒绿、摘除 provider 条件、伪造参与率、窗口漂移，都必须有测试判红。`l3Claimable` 保持 `false`。
