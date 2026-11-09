---
comet_change: synapclaim-liability-rule-json-dual-read
role: technical-design
canonical_spec: openspec
---

## Context

M2 ①（`0123` 加 `liability_rule_json JSON NULL`）与 ②（写侧影子 + 保存后回读断言 `scalar == project(json)`）已入库并生效于 dev 库。当前事实：`AdjLiability` 已有 `liabilityRuleJson` 字段，5 个仓储 SQL 模板同批补列；`LiabilityRuleJson` 值对象提供 `project(key)` / `assertProjectsTo(...)` / `withProjection(...)`；`LiabilityRuleJsonCodec` 位于 application 层，提供 `encode` / `decode` / `assertShadowConsistent`；三个写入口统一走 `saveWithRuleShadow`。**读侧完全未改**：`Adjustment.java:431` 与 `LiabilityConfigApplicationService.java:437` 仍把标量列直接传给 `LiabilityRule.fromLegacy(...)`。

两条硬约束来自项目规范与本域实测：① `adapter → application → domain ← infrastructure`，domain 零框架依赖，因此 domain 不能调用 application 的 codec，也不能读 Spring 配置；② MySQL 8 的 JSON 列会把 `90.00` 规范化为 `90.0` 并重排键序（② 阶段临时表实测），因此「写入字符串 == 读回字符串」不成立。

另外，方案 §9.1.1 ③ 原写「`RuleJsonCodec.resolve()`」位于 application 层；若照此实现，`fromLegacy`（domain）将反向依赖 application，直接违反分层。本设计据此更正装配归属（见 Decisions D1）。动机与非目标见 `proposal.md`，行为契约见 `specs/claim/liability-rule-json-dual-read/spec.md`。

## Goals / Non-Goals

**Goals:**

- 建立按键回落语义，使「只配部分键」与「历史行 JSON 为 NULL」都能正确复算。
- 建立默认关闭的读开关与双轨比对，产出「两条路径逐笔相等」的可复核证据，供后续 change 判定是否允许切主。
- 把剩余 L3 项由「未做」改写为「带触发条件与复核命令的可判定阻塞」。
- 全过程中不改变任何赔付金额，不新增 DDL、迁移或存量回填。

**Non-Goals（设计级边界）:**

- 不设计 3b 切主后的真源切换、灰度或回滚演练细节（属后续独立 change）。
- 不引入新的 JSON Schema 依赖、不新增数据库表、不新增消息事件或缓存。
- 不设计 B 类六个维度的写入入口与表单（消费方仍为零，记在 O-10）。
- 不设计 ④ Unlock 的超集表达（分段 / 区间 / 累计）求值。

## 候选方案与取舍（本轮 Design 评估）

**待决问题**：读侧怎么接入聚合 JSON，才能既拿到切主证据、又零行为变更、还不违反分层。

| 维度 | **A（采纳）domain 回落 + 读开关 + 双轨比对** | B：application 预解析后透传三值 | C：直接切主 + 历史行回填 |
|---|---|---|---|
| 做法 | `resolve(key, scalar)` 落 domain；`fromLegacy` 增重载；application 开关默认 `false`，打开时两条路径都算并比对 | codec 在 application 解析 JSON、算好三值再调 `fromLegacy` | 读真源改为 JSON，历史 NULL 行全量回填 |
| 分层 | ✅ domain 不依赖 application | ❌ `Adjustment.apply()` 是 domain 内部调用，无法经 application 中转；等于拆出第二条参数通道 | ✅（不涉及） |
| 装配点唯一 | ✅ 重载复用同一 `build(...)` 链 | ❌ 新旧两条参数通道 | ⚠️ 短期唯一，回填后旧列退役又是新改动 |
| 行为变更 | **零**（默认关闭） | 零（默认关闭） | **会改赔付金额**，且无差异证据 |
| 回滚 | 关开关 / 删 JSON 分支，二进制可自动回滚 | 同 A | 只能回滚到上一版新代码，语义不可逆 |
| 存量回填 | 不需要（历史 NULL 行按键回落） | 不需要 | **需要**，且历史赔案按版本复算会失真（方案 §5.2 已否决） |
| 切主证据 | ✅ 双轨逐笔相等可复核 | ⚠️ 可做，但参数通道分叉使比对口径不清 | ❌ 无 |

**采纳 A**。决定性理由不是「更规范」，而是 B 在本仓库**结构上做不到**：`Adjustment.apply()` 位于 domain，读路径不经过 application；C 已被用户在范围决策中明确排除，且方案 §5.2 已论证按版本行、按键回落，历史回填会破坏赔案复算。

**Spec Patch：无。** capability `claim/liability-rule-json-dual-read` 的 6 条需求、18 个场景已覆盖全部行为；本轮只决定实现方式，不改变任何需求。唯一文档措辞更正是 §9.1.1 ③ 的「`RuleJsonCodec.resolve()`」→ domain 归属（tasks 1.1 登记），属实现归属更正，不是验收变化。

## Decisions

### D1：回落逻辑放 domain 值对象，不放 application codec

**决定**：`LiabilityRuleJson`（domain）新增纯函数 `resolve(key, scalar)`；`LiabilityRuleJsonCodec`（application）继续只负责「字符串 ↔ 值对象」与写入期 Schema，不参与读侧装配。

**理由**：装配点 `LiabilityRule.fromLegacy` 与 `Adjustment.apply()` 都在 domain。若回落放在 application，domain 要么反向依赖 application（违反分层），要么把解析好的值一路透传（产生第二套参数通道，装配点不再唯一）。

**备选**：① 把 codec 下沉到 domain —— 会引入 Jackson 到 domain，破坏零框架依赖；② 在 application 层预先算好三个金额再调 `fromLegacy` —— 需要改所有调用点，且 `Adjustment.apply()` 是 domain 内部调用，无法经 application 中转。**均不采纳**。此决定同时更正方案 §9.1.1 ③ 中「`RuleJsonCodec.resolve()`」的措辞，文档同批改。

### D2：装配点唯一，`fromLegacy` 增重载而非新建工厂

**决定**：保留现有 12 参数 `fromLegacy(...)` 不动，新增一个接收 `LiabilityRuleJson`（可为 null）的重载；重载内部对三个 A 类键调用 `resolve(key, scalar)`，其余装配逻辑复用同一段 `build(...)` 链。

**理由**：方案 §9.1.1 ③ 明确「禁新旧两套算法」。若另建工厂，未来修一个边界条件必然漏改另一处，正是本项目反复出现的「静默错赔」形态。

**备选**：在 `fromLegacy` 原签名末尾直接加一个参数 —— 会波及两个调用点与全部既有测试夹具，且把「开关关闭」与「无 JSON」两种语义混在一个参数里。**不采纳**。

### D3：开关放 application，以值参数传入 domain

**决定**：配置 `bone.claim.liability.rule-json-read.enabled`，默认 `false`。application 读取配置后，把模式（`LEGACY_ONLY` / `DUAL_READ`）作为参数传给领域入口；domain 不读任何配置、不感知 Spring。

**理由**：domain 零框架依赖是硬约束。同时把「是否解析 JSON」的决定权留在最外层，便于单测直接注入模式，不必起容器。

**备选**：静态全局开关 —— 多租户/多实例下不可控，且测试间会串味。**不采纳**。

### D4：双轨比对差异只记录，不阻断、不改金额

**决定**：`DUAL_READ` 下两条路径都计算；无论是否相等，返回给调用方的**始终是旧标量路径的结果**。差异写入结构化日志（责任 ID、版本、差异键、两侧数值）与计数指标，不落数据库表、不抛异常。

**理由**：本 change 承诺零行为变更；差异若阻断，等于在开关打开时引入新的失败模式。不落表是为了避免再制造一个「配了不生效」的存储结构——日志与指标本身就是后续切主评审要用的证据。

**备选**：差异落 `t_dual_read_diff` 表 —— 多一张无消费方的表 + 一份 DDL，且 L3 项刚因「消费方为零」被判定不做，自相矛盾。**不采纳**。

### D5：等价判据用数值相等，不用字符串相等

**决定**：金额类用 `BigDecimal.compareTo(...) == 0`；B 类维度按「存在/缺失」与解析后的结构比较，不比原始文本。

**理由**：② 阶段 MySQL 8 临时表实测——`90.00` 会被规范化为 `90.0`、键序被重排。逐字比对必然误报，而误报会逼作者关掉整道门禁。

**备选**：写入前把金额统一转字符串再比 —— 掩盖了真实数值差异，失去判别力。**不采纳**。

### D6：L3 两项给结论而不给半成品

**决定**：`engine_version` 在理赔模块源码中检索读取点为零 ⇒ 不落实体、不加列、不写迁移，台账写明「消费方落地时同批入库」+ 复核命令；PITest 本地依赖仓库无制品、`mvn -o` 不可解析 ⇒ 不接 pom / check.sh / allowlist，台账写明「CI 联网 + 路由开关生产启用评审前」+ 复核命令。

**理由**：前者违反项目「新增配置字段必先找消费方」的硬规则；后者只有两种结果——离线构建红，或一个从未跑过的假绿门禁。把「未做」改成「有触发条件的阻塞」才是可交付的结论。

**备选**：先加 DDL / 先把插件写进 pom 但不执行 —— 前者制造孤儿字段，后者制造假绿。**均不采纳**。

### D7：O-06 闸门按依赖交集收窄，原文与理由一并留痕

**决定**：§13 台账 O-06 的升级条件由「M2 ③ 开工时 5 个硬缺口仍未逐项验收 ⇒ 不得开工」收窄为「与 mapping 有交集的配置面改造开工前须逐项验收」；保留原文引用、更正日期与理由（已由用户裁定）。O-06 本体仍独立跟踪，不在本 change 转正。

**理由**：5 个硬缺口涉及双保单号、保司码表/映射、子表外键锚点、软删唯一键、险种层额度，与 `t_adj_liability.liability_rule_json` 的读路径无代码与数据交集。照字面停工是误报；项目已记录「误报比漏报更致命」——作者会因一次假红关掉整道门禁。更正而非豁免，是为了让闸门继续对真正有依赖的改造生效。

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| 模式参数侵入 `Adjustment.apply()` 签名，波及既有调用点与测试夹具 | 用重载 + 默认值 `LEGACY_ONLY`，既有签名保持不变；改签名后按项目惯例 `touch` 全部 Java 再跑 `mvn test` |
| 开关打开时多算一遍，读路径有额外开销 | 默认关闭；打开只在观察窗口；解析有 8 KiB 长度上限与维度深度上限（沿用 ② 的 codec 边界） |
| 只靠日志/指标，没落表 ⇒ 差异证据可能不全 | 日志含责任 ID + 版本 + 差异键 + 两侧数值，可离线聚合；不落表是有意取舍，证据充分性由观察窗口内的日志采样核对 |
| 差异被记录但没人看 ⇒ 变成新的假绿 | 验收里要求「注入差异必有一条日志」的正向钉子 + 「删掉比对实现用例转红」的负向钉子，双向变异验证 |
| 历史行 JSON 为 NULL 时 B 类键被误判 fail-closed | 专属反例用例；② 的值对象已把 B 类键定义为「缺键 = 不施加约束」，读侧沿用同一分类，不另立口径 |
| 文档更正被读成「放宽了 O-06」 | 台账保留原条文、更正日期、理由与复核命令；文档门禁校验该条仍在位 |

## Migration Plan

**无 DDL、无迁移脚本、无存量回填。** `0123` 已落的可空列继续存在；历史行保持 `liability_rule_json IS NULL`。

部署顺序：

1. 代码上线，开关保持默认 `false` ⇒ 行为与 ② 完全一致，可随时回滚到上一版二进制。
2. 在受控范围打开 `DUAL_READ`，采集差异日志；差异键与责任版本可定位。
3. 差异率为 0 且持续若干观察窗口后，才由**后续独立 change** 评审 3b 切主；本 change 不自动切换。
4. 回滚：关开关（立即回到旧路径）→ 若需彻底移除，删 JSON 分支（标量仍是真源）。二进制回滚始终可用，因为新旧代码读写同一份数据。

## Open Questions

- 3b 切主所需的「差异率阈值 / 观察窗口长度 / 灰度批次」由后续独立 change 决定。本 change 只负责产出比对能力与证据，阈值缺失不改变本 change 的规格、方案或任务拆解。
- O-06 转正的 owner 与排期沿用台账既有裁定（默认架构师承担），不在本 change 内决定。
