# Verify: synapclaim-liability-rule-json-dual-read

> 验证 M2 ③ **3a 双读比对**（按键回落 + 默认关闭的读开关 + 双轨差异只记录）与**剩余可落地 L3 项收口**。
> 主证据为一次**新鲜跑**的聚焦用例 20/20、全模块回归 1382/1382，一次**不改仓库源码的等价变异探针**（5 场景 3 差异），
> 以及 Comet 检查收据（`exitCode=0`、输入快照前后一致）与两项台账结论的**机检复核**。

## 结论

**本 change 声明的交付全部落地并通过验证；范围守据成立（零 DDL / 零迁移 / 零回填 / 零金额变更）。**

读侧确实做到**按键回落**而不是整列判断：稀疏行上缺的键回落旧标量，A 类键两侧皆空则 fail-closed 报键名；
读开关默认 `false`，关闭时**连解析都不发生**（用「喂非法 JSON 也不抛」钉住），故发布后行为与 ② 逐笔一致；
`engine_version` 与 PITest 两项按「消费方为零 / 制品缺失」**不落实体、不接线**，只留可机检的触发条件与复核命令 —— 没有制造假绿门禁。

## 已验证

| 项 | 状态 | 证据 |
|----|------|------|
| V1 交付物与范围 | ✅ | `git show --stat 11daad68a` = 17 文件 / +1081 −29（3 个 domain 值对象 + 1 个 application 开关 + 2 个测试类 + 装配点 + 文档）；区间 `e3c940b9..HEAD` 对 `scripts/migration`、`bone-init.sql`、`sql/` **零改动** ⇒ 无 DDL、无迁移、无存量回填 |
| V2 读开关默认关闭 | ✅ | `LiabilityRuleReadGate:39` `${bone.claim.liability.rule-json-read.enabled:false}`；`attach()` 首行 `if (!enabled \|\| liability == null) return;` ⇒ 关闭态不进入解码 |
| V3 关闭态「连解析都不发生」 | ✅ | 用例 `disabledGateNeverParses`：给 `liability_rule_json` 塞 `{这不是 JSON`，`new LiabilityRuleReadGate(false).attach(...)` **不抛**，`getRuleView()` 为 `null`、`getRuleReadMode()` 为 `LEGACY_ONLY` —— 用行为钉住，不靠注释承诺 |
| V4 按键回落（禁止整列判断） | ✅ | 用例 `singleKeyFallsBackPerKey`：JSON 只含 `pay_percent=90` 时，`liability_deduct` 取旧标量 **200**、`liability_limit` 取旧标量 **5000**（若整列判断，这两键会是 0） |
| V5 双轨只记录、不改金额 | ✅ | 用例 `dualReadRecordsDiffButReturnsLegacyRule`：注入 `json=90` / `scalar=80` 后差异条目 `legacyValue=80`、`jsonValue=90`，返回的 `LiabilityRule` 仍取旧路径；application 侧只 `logDiffs` + 计数，无异常路径 |
| V6 数值等价而非逐字等价 | ✅ | 用例 `decimalScaleDifferenceIsNotADiff`：`90.00`（MySQL 规范化为 `90.0`）与 `90` 判为相等，不产生差异条目 |
| V7 **新鲜等价变异探针** | ✅ | 仓库外探针 `/tmp/mutant_probe/MutantProbe.java` 用同一批输入同跑真实实现与「整列判断」变异体，**5 场景 3 处差异**：稀疏行 `deduct` 200 → **0**、稀疏行 `limit` 5000 → **0**、A 类双空 `REJECT` → **0**（静默少赔）；而常态行（两侧同值 90）与历史行（80）两实现**一致** ⇒ 证明「常态行看不出问题、只有稀疏行暴露」，故稀疏行反例是唯一有效判据 |
| V8 聚焦用例新鲜跑 | ✅ | `mvn -o -pl … -am test -Dtest=LiabilityRuleJsonDualReadTest,LiabilityRuleJsonReadSideTest` → **20/20**（10 + 10），Failures 0 / Errors 0 |
| V9 全模块回归 | ✅ | `mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test` → **1382 用例 / 0 失败 / 0 错误 / 7 跳过**，BUILD SUCCESS（含 `ArchitectureTest` 28 项：分层、仓储命名、domain 零框架依赖） |
| V10 门禁实跑 | ✅ | ① 直跑 `./scripts/check.sh` → RC=0，末行「✅ 本地自检通过！」，实测 **422 秒 / 59 步**；② Comet Build 检查收据 `exitCode=0`、`inputBefore == inputAfter`（`25ec990c…`）、`contentHash 5ffe844c…`、`tier=full`、`changedDuringExecution=[]` |
| V11 `engine_version` 收口可机检 | ✅ | 现场复核 `grep -rn 'engine_version\|engineVersion' --include=*.java bone-apps/synap-claim` → **0 处** ⇒ 台账 O-11 记「不落实体、不加列」，并写明触发条件（首个读取点出现时同批入库）与复核命令 |
| V12 PITest 收口可机检 | ✅ | 现场复核 `ls ~/.m2/repository/org/pitest` → **无制品**；`grep -rn pitest scripts/check.sh pom.xml` → **0 命中** ⇒ 台账 O-02 记「不接线」，触发物=路由开关生产启用评审单；未把它写成生效门禁 |
| V13 文档同步 | ✅ | §9.1.1 键分类表前置措辞改为「② 与 ③ 3a 已落地（读开关默认关闭），③ 3b / ④ 未开工」；§13 新增 **O-11**；**O-06 按裁定收窄且原文保留**（含 2026-11-09 更正、理由、复核命令）；诊断报告 §14.1 两行状态由「未做」改为「有触发条件的阻塞」 |

## 判读要点

- **V7 是本轮最关键的证据，而且它替换了一个更弱的做法**。原计划是在仓库里改一行源码跑变异再还原，
  但 Comet 在 Verify 阶段拦截源码编辑（要求独立评审）——这恰好是对的：Verify 期间动被测代码会让「证据」与「产物」互相污染。
  改用**同 classpath 的外置探针**后，被测源码哈希全程为 `2483330b…`（前后一致、`git status` 为空），
  同时把「真实实现与变异体在哪些输入上分叉」直接打印出来，判别力比「跑一遍看到红」更明确。
- **V3 与 V2 是两条不同的保证**：V2 只证明开关默认值，V3 才证明「关闭态不会有解析副作用」。
  只看默认值的话，一个「先解析再判断开关」的实现也能骗过 V2，但会在生产上把脏数据变成 500。
- **V6 不是洁癖**：MySQL 8 的 JSON 列会把 `90.00` 规范化成 `90.0` 并重排键序，
  用字符串逐字比对会在**每一行**上误报差异，差异率指标立刻失效 —— 那正好会毁掉 3b 切主的唯一判据。
- **V11/V12 的价值在「不做什么」**：两处都现场跑过复核命令，证明「消费方为零」和「制品缺失」是**事实**而非推断，
  因此不落实体、不接门禁是正确决策；若接了，得到的只会是一个从未跑过的假绿门禁。

## 环境侧修复（与业务无关，但门禁可运行的前提）

验证过程中暴露出两个**与本 change 无关**的环境缺陷，已单独提交 `bd0792843` 并留痕：

1. `.claude/skills/{code-localization,code-review,editing,test-gen}/latest` 四根符号链接**在 HEAD 中即悬空**
   （目标写成 `<skill>/<version>`，相对自身目录解析多一层）。Comet 检查输入快照拒绝任何符号链接
   （`Check input is a symbolic link`），使 Build 门禁无法启动；官方替代方案（`.comet/check-policy.json` 逐命令声明 `files`）
   在 6429 项输入、glob 不支持取反的前提下不可表达，故移除冗余指针。
2. `scripts/check.sh` 全量实测 422 秒 > 缺省 `check_timeout_ms: 300000` ⇒ 必然 `exit=124` 且不产生可用证据。
   已在 `.comet/config.yaml` 配置 `classic.check_timeout_ms: 900000`。

## 遗留与后续（不在本次范围）

- **3b 读侧切主**：需先积累双轨差异率（本 change 只提供记录通道与计数），再按 §4.1.3「双轨比对全绿」判据决策。
- **④ Unlock / Retire**、生产启用、真实压测实测回填：属 L4，需架构师评审凭据，本次未触碰。
- **PITest 接线**：触发物=路由开关生产启用评审单；本地离线制品缺失（V12 已复核）。
- **`engine_version` 落实体与 DDL**：触发条件=首个读取点出现（V11 已复核为 0 处）。
- **O-06 的 5 个 mapping 硬缺口**：与 `liability_rule_json` 读路径无交集，按收窄后的闸门独立跟踪，不阻塞本 change。
