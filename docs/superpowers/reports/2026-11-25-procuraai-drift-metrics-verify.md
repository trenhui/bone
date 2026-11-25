# Verify 报告 — procuraAI 漂移信号与治理指标只读观测（内部模拟 POC）

- change: `procuraai-drift-metrics`
- 日期: 2026-11-25
- 执行配置: `build_mode=autonomous` / `tdd_mode=tdd` / `review_mode=standard`
- 工作区: `dev`，isolation=current，base=`e6e84a86b`
- 结论: **PASS**（结构、fail-closed 不变量、门禁不退化、独立审查 0 CRITICAL/0 IMPORTANT）；**不代表模型效果达标**

## 1. 目标与边界

为 AI 决策治理补漂移信号三态判词与治理指标只读观测（金监总局 §6/§9、业界 override 率指标）。本 POC 验证的是**工程结构与 fail-closed 不变量**，不是 AI 模型效果。

**非目标（未做，也不声称）**：真实模型接入/效果达标、evalset 27→200 转正、契约/DDL 冻结、生产 MQ、生产库操作、自动改能力状态、owner/法务结论。

## 2. 交付物（file:line 证据）

| 类别 | 文件 | 要点 |
|---|---|---|
| 领域枚举 | `.../domain/model/capability/DriftVerdict.java` | 三态 UNDETERMINED/NO_ACTIONABLE_SIGNAL/REVIEW_REQUIRED；`isActionable()` 仅触阈为真 |
| 领域配置 | `.../domain/model/capability/DriftThresholds.java` | 缺省未配置；阈值 (0,1]、最小样本正数校验、NaN 拒绝 |
| 领域结果 | `.../domain/model/capability/DriftSignal.java` | 计数/比率/判词/原因码；`claimsModelHealthy()` 恒 false |
| 领域策略 | `.../domain/service/DriftSignalPolicy.java` | 纯函数：计数一致→阈值配置→样本充足→触阈；不可判定一律 UNDETERMINED |
| 应用投影 | `.../application/DriftMetricsView.java` | 只读投影，`modelHealthClaim=NONE` |
| 应用服务 | `.../application/AiGovernanceApplicationService.java` | `driftSignal(code, thresholds)`：tracePort 计样本、ledger 取推翻数，未登记 fail-closed，无副作用 |
| 配置属性 | `.../application/AiGovernanceProperties.java` | `driftThreshold` 默认 null（owner 通过线未配置）、`driftMinSampleSize=20` |
| HTTP 端点 | `.../adapter/web/controller/AiGovernanceController.java` | `GET /capabilities/{code}/drift`，`procurement:ai-governance:read`，`ApiResponse<DriftMetricsView>`；阈值只取服务端配置，不接受客户端参数 |
| 测试 | `DriftSignalTest`(7) / `DriftMetricsApplicationTest`(6) / `AiGovernanceControllerContractTest`(4) / `AiGovernanceToggleTest`(+1) | 领域裁决、应用 fail-closed、HTTP 契约、配置缺省 |

## 3. 内部模拟 POC 场景（真实计数）

应用领域服务 + 内存端口（与生产同接口）构造真实终决序列，验证三态判词：

| 场景 | 输入（真实终决） | 阈值 | 判词 | override 率 |
|---|---|---|---|---|
| 触阈需复核 | 10 次终决，3 次推翻 | 0.25 / min=5 | REVIEW_REQUIRED | 0.30（真实计数） |
| 阈值未配置 | 10 次终决，3 次推翻 | unconfigured | UNDETERMINED | null（不可判定） |
| 样本不足 | 2 次终决，1 次推翻 | 0.3 / min=5 | UNDETERMINED | null |
| 未触阈 | 10 次终决，1 次推翻 | 0.3 / min=5 | NO_ACTIONABLE_SIGNAL | 0.10 |
| 未登记能力 | 能力码 MISSING | — | 拒绝（CapabilityNotFoundException） | — |
| 边界：率==阈值 / 100% 推翻 / 零样本 / NaN 阈值 | 见 DriftSignalTest | — | 触阈或拒绝，均不声明健康 | — |

证据：`DriftMetricsApplicationTest` 6 例 + `DriftSignalTest` 7 例全绿；`sampleSize`/`overrideCount` 断言来自 ledger/trace 真实计数，非伪造。

## 4. fail-closed 负向探针

| 探针 | 期望 | 结果 |
|---|---|---|
| 阈值 <=0 / >1 / NaN | 抛 IllegalArgumentException | PASS |
| 最小样本量 <=0 | 抛 IllegalArgumentException | PASS |
| 推翻数 > 样本总数 / 任一为负 | 抛 IllegalArgumentException，不产生判词 | PASS |
| 零样本 | UNDETERMINED（样本不足） | PASS |
| 任何判词 `claimsModelHealthy()` | 恒 false | PASS（领域与应用测试均断言） |
| 端点 `modelHealthClaim` | 恒 `NONE` | PASS |
| 多次读同一能力 | 追溯链条目数不变、能力状态不变 | PASS |
| 未触阈判词 | reason 明确"不构成模型背书" | PASS（`DRIFT_BELOW_THRESHOLD_NOT_MODEL_ENDORSEMENT`） |
| 阈值缺省（配置层） | `driftThreshold=null` → 端点 UNDETERMINED | PASS（`AiGovernanceToggleTest.漂移阈值默认未配置failClosed`） |

## 5. 独立审查（standard）与修复

独立 reviewer 对最终 diff 审查结论：**初评 0 CRITICAL / 1 IMPORTANT / 4 SUGGESTION**。

- **IMPORTANT [I1]（已修复）**：初版端点用 `@RequestParam` 接受客户端阈值，具 read 权限者可自设 owner 通过线、绕过缺省 UNDETERMINED。**修复**：阈值改为服务端配置属性 `AiGovernanceProperties.driftThreshold`（默认 null），端点不接受任何阈值 query 参数；补 `AiGovernanceToggleTest` 断言缺省未配置。复审确认：`AiGovernanceController.drift(code)` 仅一个 String 参数、无 `@RequestParam`，配置缺省 fail-closed。
- SUGGESTION（按需采纳，均非 fail-open）：非法参数 500→400 的错误语义（本模块无全局异常处理器，属更大范围改造，不在本 change）；判定顺序文档措辞同步（代码注释已写实际顺序）；边界探针已补（率==阈值/100% 推翻/零样本/NaN）。

**修复后结论：0 CRITICAL / 0 IMPORTANT → PASS。**

## 6. 门禁验证结果

| 检查 | 结果 |
|---|---|
| ai-governance 模块 `mvn test`（含 ArchitectureTest 13 例） | **106 例 / 0 失败 / BUILD SUCCESS** |
| 领域 RED→GREEN | RED（符号不存在）→ GREEN（7/7）；应用 6/6 |
| ArchUnit：domain 独立 / application 不依赖 infra / 端点权限 / bean 唯一 | 全绿（ArchitectureTest 13/13） |
| 采购聚合 `mvn test`（10 模块） | **全部 SUCCESS / EXIT=0**（约 3 min） |
| build guard `--apply`（真实 mvn test 收据） | ALL CHECKS PASSED → phase=verify |
| alignment 门禁 | 10/10 PASS |
| evalset 门禁 | RESULT: PASS（仍 `seededDraft`，未改状态） |
| frontmatter 门禁 | 33 份文档 PASS |
| persistence A10.29 | PASS（9 表 / 双唯一键 / 10 端口） |
| event-chain 门禁 | PASS（3 表 / 4 负向探针 / 端口持久化） |

## 7. 诚实边界

- CI / 门禁绿仅证明**文档族一致 + fail-closed 不变量成立 + 结构完整**，不等于 AI 能力可上线。
- 阈值缺省 UNDETERMINED 是刻意的诚实行为，非缺陷；真实通过线属 owner（D-10 未闭合），本 change 未设定，且不接受客户端注入。
- 样本数来自追溯链事件计数；历史终决若未全部留痕会低估样本，结果偏保守（倾向 UNDETERMINED），不会误报触阈。
- 未改 DDL / `bone-init.sql`、未改 A10.17 三入口判据集合、未改 evalset 状态、未接真实模型、未执行生产库操作、未新增权限码。
