# Verify 报告：procuraAI 治理态势聚合看板

- change: `procuraai-governance-posture-board`
- 日期: 2026-11-25
- 工作区: current / dev
- 结论: **PASS**。五场景符合 spec，硬红线成立；独立审查 0 CRITICAL / 3 IMPORTANT（已全部修复）/ 6 MINOR（知悉，非阻塞）。

## 验证命令与结果（实际执行）

| 项 | 命令 | 结果 |
|---|---|---|
| 后端模块 clean test | `mvn -o -f bone-apps/bone-procurement/bone-procurement-ai-governance/pom.xml spotless:apply clean test` | BUILD SUCCESS，139 例全绿（上一轮 126，本轮 +13） |
| 采购聚合测试 | `mvn -o -f bone-apps/bone-procurement/bone-procurement-app/pom.xml -Dtest=ProcuraCapabilityCoverageTest test` | 3 例全绿 |
| 采购前端 vitest | `npx vitest run apps/bone-procurement-app` | 17 文件 / 120 例全绿（审查修复后重跑） |
| 前端 tsc | `npx tsc --noEmit -p apps/bone-procurement-app/tsconfig.json` | exit 0 |
| 前端 eslint | `npx eslint src -f json`（errorCount 解析） | errorCount=0 |
| alignment | `python3 tools/check_procura_alignment.py` + `--self-test` | 10/10；自检 157/157 |
| evalset | `python3 doc/ai-product/procuraAI/scripts/check_procura_evalset.py` + `--self-test` | PASS；自检 69/69 |
| frontmatter | `.../check_procura_frontmatter.py` + `--self-test` | 33 份文档 PASS；自检 7/7 |
| persistence | `python3 tools/check_procura_persistence.py` + `--self-test` | PASS（9 表/双唯一键/10 端口）；自检 ALL PASS |
| event_chain | `python3 tools/check_procura_event_chain.py` + `--self-test` | PASS（3 表/必备列/唯一键/4 负向探针）；自检 ALL PASS |
| OpenSpec 严格校验 | `comet classic openspec -- validate <change> --strict` | valid |

## POC 五场景（实际输入/输出/证据）

| # | 场景 | 实际结果 | 证据（测试/文件） |
|---|------|----------|-------------------|
| 1 | 不可判定优先 | 一能力 drift=REVIEW_REQUIRED + 另一能力 drift=UNDETERMINED → 汇总 UNDETERMINED | `GovernancePosturePolicyTest.分项不可判定优先于其他需求的需关注`；`GovernancePostureApplicationTest.阈值未配置时汇总不可判定且只读无副作用` |
| 2 | 需关注 | 3 次推翻 + 监督触阈 → ATTENTION_REQUIRED，attentionCount≥1，监督判词 REVIEW_REQUIRED | `GovernancePostureApplicationTest.触阈时汇总需关注且明细给出监督判词` |
| 3 | 全清 | 1 条同结论终决 + 阈值配置 → ALL_CLEAR，计数 0/0 | `GovernancePostureApplicationTest.全部可判定且无信号时汇总全清` |
| 4 | 取数失败 | mock reject → 「无法确认」，无「全部正常」；readiness 失败亦显「无法确认」（审查修复后） | `GovernanceStatus.test.tsx.取数失败显示无法确认而非默认通过`、`就绪度取数失败也显示无法确认，不留空白卡片` |
| 5 | 未知码 | `SOME_NEW_VERDICT` → 页面显原始码，无「全部正常」；映射层含原始码不含「正常」 | `GovernanceStatus.test.tsx.未知汇总判词显示原始码而非正常`、`governancePostureView.test.ts.未知判词码显示原始码而非正常` |

## 硬红线核验

- **只读无副作用**：`posture()` 全链路无 `tracePort.append` / `registry.save` / `ledger.append`；测试断言调用前后 trace 计数不变（`GovernancePostureApplicationTest.阈值未配置时汇总不可判定且只读无副作用`）。
- **阈值不可注入**：`GET /posture` 无参数；阈值只取 `AiGovernanceProperties`，缺省 → UNDETERMINED（`PostureEndpointContractTest`）。
- **权限与开关**：read 权限 + 类级 `@ConditionalOnAiGovernanceEnabled`（`PostureEndpointContractTest`、`AiGovernanceControllerContractTest`）。
- **PII 禁入**：`CapabilityPostureEntry` 8 字段白名单反射守护，`modelHealthClaim` 恒 NONE（`GovernancePostureViewTest`）。
- **披露不虚构**：页面仅入口链接，无「已披露/未披露」全局结论（`GovernanceStatus.test.tsx`）。
- **人工终决不变 / 不接真模型 / 不声明效果达标**：Non-Goals，diff 未触及；modelHealthClaim=NONE。
- **总闸假绿（审查新增）**：总闸停用 + 分项全清时前端将汇总降级为需关注并提示冻结（`GovernanceStatus.test.tsx.全域总闸停用时分项全清不渲染成全部正常`）。

## 独立审查结论与处置

- 审查者：`general-purpose` 独立子代理，read-only，审查 `0825958c9..ad2d512ce` 7 提交 15 文件。
- CRITICAL：0。
- IMPORTANT 1（就绪度取数失败回归空白）→ 已修复：恢复 `readinessError` 状态 + Alert + 页面测试。
- IMPORTANT 2（总闸停用仍显绿色全清）→ 已修复：前端组合 `killSwitchEngaged` 降级汇总判词 + 提示 + 页面测试。
- IMPORTANT 3（POC 报告缺失）→ 本文件即补齐的验证/POC 报告，五场景证据见上。
- MINOR 4（准入列 boolean 不渲染）→ 已顺手修复（render 转中文）。
- MINOR 5–9：计数字符串字面量、DRY、接口轻微偏离 plan、契约测试偏薄、空清单 ALL_CLEAR 残余风险——均知悉、非阻塞，属既有设计决策或风格建议，不在本轮范围。

## OpenSpec 规格核对（openspec-verify-change）

| 维度 | 结果 |
|---|---|
| Completeness | 11/11 任务完成；3/3 需求（全 ADDED）有实现映射 |
| Correctness | 3/3 需求实现匹配 intent；9/9 场景有测试覆盖（只读端点 fail-closed 3 场景、汇总判词 3 场景、前端渲染 3 场景） |
| Coherence | design.md 方案 A 决策被遵循（模块内聚合、前端补取 readiness/kill switch、纯函数汇总、字段白名单）；无显著模式偏离 |

- CRITICAL：0；WARNING：0；SUGGESTION：0（独立审查的 MINOR 已在工作报告中记录，不属规格层）。
- 需求→代码：`AiGovernanceController.java:143`（只读端点）、`GovernancePosturePolicy.java:26`（汇总）、`GovernanceStatus.tsx:16`（前端渲染）。
- 结论：All checks passed. Ready for archive.

## 复审

IMPORTANT 1/2 修复后已重跑采购前端 120 例（含新增 2 例）+ tsc + eslint，全绿；后端未受前端改动影响，139 例结论有效。判定 READY。

## 已知环境基线（非本轮引入）

全仓 `npx vitest run`（不限定 app）扫描 `node_modules.broken/` 第三方副本及 claim/metadata/extension 模块共 139 失败，采购域零失败；与本轮无关，属仓库既有假红基线，本轮以采购 app 范围 vitest 为权威判据。
