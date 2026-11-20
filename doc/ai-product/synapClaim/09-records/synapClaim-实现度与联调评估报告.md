# synapClaim 智能理赔系统 · 实现度与联调评估报告

> 评估日期：2026-11-20 · 分支 `dev` · HEAD `5b6b81ae3` · 工作树干净
> 代码位置：`bone-apps/synap-claim` · 全程 `real-services-no-mock`
> 说明：本报告所有数字与判据均在当前 HEAD 上当场重取，未对代码/文档做任何改动。

## 一、执行摘要

**确定性主链路（案件 → 理算 → 额度 → 结算）已是能跑的生产级工程**；而设计方案中依赖真实 AI 能力、外部数据与合规签署的目标态，多数仍是 `fail-closed` / `draft` / 未开工。一句话：**代码超前于文档，工程实现超前于能力宣称。**

| 指标 | 数值 |
|---|---|
| app 主源码 Java 类 | 460 |
| Controller / 应用服务 / Repository | 38 / 40 / 48 |
| 三模块测试文件 | 219 |
| 后端主链 E2E | 7 / 7 |
| 前端全链 Playwright | 39 / 39 |
| L3 就绪度 SATISFIED | 5 / 12（当前 L0） |

## 二、评估口径与证据来源

| 证据类别 | 具体内容 | 用途 |
|---|---|---|
| 代码盘点 | 三模块包结构、类计数、关键类/方法、测试夹具 | 判定"是否真的写了" |
| Git 历史 | 提交时间线与变更文件；确认 0124–0126 迁移落地时间 | 纠正滞后索引摘要 |
| 实施记录 | `impl-log 14–20`、reviews 台账、go-live 草案正文 | 判定"是否接线 / 是否被撤销" |
| 实时运行 | 服务探针、provider 探活、就绪度复核器、前后端 E2E | 判定"是否真能跑 / 非假绿" |

诚实声明：凡依赖外部数据/签署的项目一律标注"未解锁"，不做推测性达标认定。

## 三、分层实现度

### A · 已落地且有证据

| 能力域 | 落地内容 | 关键证据 |
|---|---|---|
| DDD 四层骨架 | adapter → application → domain ← infrastructure；ArchUnit 护栏 | 460 类，38 Controller，40 ApplicationService，48 Repository |
| 理算引擎 V1 | 九步分层管线、七层控额、三策略路由（默认关） | adjustment-engine 核心类 + 变异验证记录 |
| 资金主链路 | 案件 → 理算 → 占用/冻结/释放 → 结算，四本账对齐 | impl-log 14–20 变异证据；E2E 7/7 |
| 运营能力 | 作业分配、抽检交接、申诉、结算工作台 | 迁移 0125_task / 0126_inspection_handoff；专项 E2E 8/8、17/17 |
| 前端工作台 | 受理/理算/复核/运营页面 | `bone-claim-app` 21 页；Playwright 39/39 |
| 数据库迁移 | 0123–0126 四个脚本真实存在 | `scripts/migration/0123..0126`（0124–0126 于 11-13/11-16 落地） |

### B · 部分落地 / 有开放项

| 能力域 | 现状 | 缺口 / 证据 |
|---|---|---|
| AI 自动放行 | 编排、RAG、OCR、文档解析接线完成，默认关、fail-closed | `AUTO_APPROVE` 默认关；生产金额仍走 `@Deprecated AdjustmentEngine` 回退 |
| 免赔六维 | 仅落医院等级、病种 2 维 | `LiabilityRule.java:382`：其余 4 维无配置列、依赖外部目录/区域口径 |
| 独立内核模块 | 定额/津贴内核为空壳 | V1 语料无对应规则；补规则属臆造 |
| bone-ai 六端口 | 端口齐全，部分为确定性本地适配器 | 视觉反诈仍是 `available=false` 桩（见第七节） |
| L3 就绪度 | 5 SAT / 7 BLOCKED | 成熟度 L0，`l3Claimable=false` |

### C · 仅设计 / 草案 / 未开工

| 能力域 | 状态 | 证据 |
|---|---|---|
| Go-Live 合规包 01–13 | 13 册全部 `draft`，未标 `current` | 08-go-live 目录 `role:draft` × 13 |
| G1–G16 门禁 | 多数规划，未闭环 | 08-go-live/00：任一项未闭环不得对正式客户投产 |
| 反欺诈图 / 视觉 + MRM | draft 设计 | 业界对标整改台账 §七："文档层对标升级，未改动任何代码" |
| 直付通道 / V2 外部前置 | V1.0/V2 增量，待排期 | 05-plan 迭代划分 |

## 四、L3 就绪度门禁（12 项）

当场输出：**SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0，L0，`l3Claimable=false`**。

| 状态 | 判据 | 原因 | 性质 |
|---|---|---|---|
| SAT | EVAL_GATES_WIRED | ADMITTED | 代码就绪 |
| SAT | FLYWHEEL_DOWNSTREAM | ADMITTED | 代码就绪 |
| SAT | IMAGE_PARSE_WIRED | ADMITTED | 代码就绪 |
| SAT | RAG_WIRED | ADMITTED | 代码就绪 |
| SAT | ROLLOUT_ROLLBACK_DRILL | ADMITTED | 代码就绪 |
| BLOCK | PROVIDER_CONFIGURED | AI_GATEWAY_BASE_URL_MISSING | 干净 shell 无凭证；运行态注入即 REACHABLE |
| BLOCK | BASELINE_WIRED | BASELINE_DECLARED_NOT_WIRED | 缺 ≥300 条真实人工盲标 |
| BLOCK | FROZEN_EVALSET_ACTIVE | FROZEN_SET_NOT_ACTIVE | 校准集骨架 collected=0 |
| BLOCK | PERCEPTION_MODEL_DRIVEN | TEXT_LAYER_ONLY | 视觉/多模态层未驱动 |
| BLOCK | CATALOG_ADMISSION_ACTIVE | CATALOG_POLICY_DEFAULT_NONE | 授权目录数据 + 产品/架构拍板 |
| BLOCK | GOLIVE_NOT_DRAFT | GOLIVE_DRAFT_13 | 合规/法务签署（L4） |
| BLOCK | RUNTIME_MODEL_COVERAGE | PROVIDER_NOT_CONFIGURED | 运行态已覆盖 |

静态复核器在干净环境下读到"无凭证"视图，会低估运行态。当场运行期证据：`nodes=148 aiParticipated=8 participationRate=0.0541 probeVerdict=REACHABLE probeEvidence=OK`。provider 三项运行态实际成立；剩余需外部输入为 4 类（盲标 / 目录 / 合规 / 视觉层）。

## 五、联调验证结果（2026-11-20 当场重跑）

| 套件 | 结果 | 说明 |
|---|---|---|
| 后端主链 `e2e_synap_claim.py` | 7 / 7 | 案件 → 理算 → 额度 → 结算 |
| inspection-handoff | 8 / 8 | 抽检交接 |
| ai-config | 5 / 5 | AI 配置接线 |
| task-workbench | 17 / 17 | 作业工作台 |
| re-review / fatigue | 5 / 5 · 5 / 5 | 复核 / 疲劳度 |
| 前端全链 `synap-claim-e2e.cjs` | 39 / 39 | Playwright，shell :3000 真实链路 |

E2E 产物：`mode=real-services-no-mock`，`passed=true`，provider 探活 `REACHABLE`（含 requestId/tokens），参与率 0.0541 双源对账一致，防假绿负向探针全过，`chain5xx=[]`。**链路真实可信，不是假绿。**

## 六、三个必须注意的判读点

1. **实现 ≠ 生效**：新三策略路由、M2 JSON 读侧（`LiabilityRuleReadGate` 默认 false）、AI 编排/RAG/OCR、规则灰度安全门（提交 `dd80b864d`，默认不装配）全部代码就绪但默认关闭；生产金额仍走 `@Deprecated AdjustmentEngine` 回退路径。
2. **文档摘要比代码慢一拍且互相撤销**：`AI-INDEX`/`docs.manifest` 仍写"0123–0126 未开工"，实测四个文件都存在；`impl-log 15/17/20` 分别撤销 `14/16/19`。判断现状须以代码 + git log + L3/L4 正文为准。
3. **E2E passed ≠ 能力达标**：11-07 快照虽 `real-services-no-mock`，但同份里 `l3Claimable=false`、`probeVerdict=NOT_CONFIGURED`、L0——是"如实报未达标"意义上的通过。

## 七、为什么"全部实现"不能靠继续写代码完成

**唯一的真代码桩：视觉反诈 `available=false` 是资金闸的安全锁，不是遗漏。**

生产态 rule 引擎返回 `available=true`、图欺诈适配器带仓储构造返回 `available=true`，**只有视觉桩恒 `available=false`**。三者逻辑与 ⇒ `capabilitiesPresent=false` ⇒ `fraudGatePassed=false` ⇒ 决策图恒走 `MANUAL_REQUIRED`。改成 `true` 会打开 `AUTO_APPROVE` 自动放款路径——L2 资金行为变更，须双人 Review / 产品签核。而且就绪复核器 12 项判据**根本不检查视觉桩**，改它不翻任何门禁，纯粹改变安全姿态。

| BLOCKED 门禁 | 缺什么 | 谁能解 |
|---|---|---|
| BASELINE_WIRED / FROZEN_EVALSET_ACTIVE | ≥300 条真实人工盲标（活库 0 条，collected=0）；伪造即触发 anti-fake-green | 算法/评测团队 |
| CATALOG_ADMISSION_ACTIVE | 授权目录数据 + 默认 NONE 改 MANDATORY（改错全量停摆） | 产品 + 架构 |
| GOLIVE_NOT_DRAFT | 13 册补正文并标 `current` | 合规/法务（L4，AI 禁止执行） |
| PERCEPTION_MODEL_DRIVEN | 视觉/多模态层驱动（与视觉桩资金闸同源） | 双签授权后接线 |

免赔六维缺的 4 维没有配置列、依赖外部目录/保单区域口径（`LiabilityRule.java:382`）；adjustment-engine 定额/津贴内核空因 V1 语料无规则——补 DDL 是 L3、编规则属臆造。强做只会制造假绿、把 L0 伪装成 L3，在受监管金融理赔里是严重风险。

## 八、业界最佳实践对标

本项目当前采用的正是受监管金融 AI 系统的通行最佳实践——这些"看似未完成"的设计本身即为刻意的安全姿态：

| 业界实践 | 本项目做法 | 为何不是缺陷 |
|---|---|---|
| Fail-closed / 默认拒绝 | 能力缺失一律 `MANUAL_REQUIRED` | 金融理赔错误代价不对称 |
| Shadow Mode 先行 | 新策略默认关、`@Deprecated` 回退兜底 | 先观察再切换 |
| 变异验证 | impl-log 多处"改一处判据必翻红" | 证明门禁有判别力 |
| 证据链 / 可追溯 | E2E 含 requestId/tokens/参与率双源对账 | 审计合规需可复现证据 |
| 四眼原则 / 双签 | L2 资金、L4 合规需人工 Review | 单方改资金闸是治理风险 |
| Anti-fake-green | 盲标不足如实报 NOT_CONFIGURED | 宁可暴露未达标 |

"全部实现并默认打开"在这些实践下是**反模式**：绕过 fail-closed、跳过 Shadow、取消双签，把 L0 伪装为 L3。真正"按最佳实践完成"，是补齐外部输入后按治理流程逐步解锁。

## 九、解锁路线与待拍板清单

| 编号 | 解锁项 | 我可承接的动作 | 前置条件 |
|---|---|---|---|
| ① | ≥300 条脱敏盲标票据 | 用 `build_active_eval_evidence.py` 跑真实评测，翻 A4/A5 | 算法/评测团队提供数据 |
| ② | 目录准入 + 视觉桩资金闸书面授权 | 按 L2 双签流程接线 CATALOG_ADMISSION / AUTO_APPROVE | 产品 + 架构 + 合规签署 |
| ③ | 评估报告 | **本报告已交付（HTML + Markdown）**，可转 PPT | 无 · 已完成 |

**待拍板（条目化，便于下一轮直接引用）**

1. **盲标数据**——是否由算法/评测团队提供 ≥300 条脱敏盲标？若是，请给数据路径与字段口径。
2. **目录准入**——是否授权把 CATALOG_POLICY 默认 NONE 改 MANDATORY？需产品 + 架构书面确认。
3. **资金闸双签**——是否启动视觉桩 `available=true` 的 L2 双签流程？需指定 Review 人与产品签核人。
4. **合规签署**——Go-Live 13 册何时进入合规/法务评审？AI 侧不代为标 `current`。
5. **交付格式**——本报告是否需要转 PPT，或补充某一层深挖（8/12 剩余 4 项、免赔六维缺口）？
