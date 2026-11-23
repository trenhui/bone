# 验收报告 — procuraai-contract-reviewed-event-chain（Build 阶段）

- 日期：2026-11-23
- change：`procuraai-contract-reviewed-event-chain`
- 基线：`5c2076848`（fetch 后 `origin/dev=879a53528`，远程新提交触及 claim/前端/synapClaim，**未触及三入口文件**，无冲突）
- 结论：**PASS**（0 CRITICAL / 0 IMPORTANT）

## 1. 交付范围与诚实边界

把 `ContractReviewed` 从进程内 `ConcurrentHashMap` 直连升级为持久化
Outbox → Inbox → DLQ 幂等消费闭环；结论与去重态落库、重启可恢复；三类负向探针机器可验证；
按 A10.17 / D-11 四入口一致流程接入三入口门禁。

**边界（未越界）**：本地持久化 + 显式驱动模拟投递；**不接生产 MQ 凭证、不执行生产 DB**；
不改 `ContractReviewedEvent` 7 字段、不改 evalset 状态、不冻结契约、不伪造签名/法务/评测转正。
生产 MQ producer/listener 仍属 L3，报告中所有"投递"均指显式驱动，不宣称生产闭环。

## 2. TDD 真实 RED → GREEN 证据

| 组 | RED 证据 | GREEN 证据 |
|---|---|---|
| 1.1 DDL | `ProcuraAiGovernanceDdlCoverageTest` 改为 24 表/9 新表后：`Expected size: 24 but was: 21` + 3 failures/1 error | 三表入库后 `Tests run: 5, Failures: 0` |
| 1.2 持久化 | `ContractReviewedEventStoreAdapterTest` 首跑编译失败（端口/实体/适配器不存在） | `Tests run: 6, Failures: 0` |
| 2.1–2.4 事件闭环 | `ContractReviewedEventChainTest` 首跑编译失败；实现后首轮 2 个断言失败（驱动未投递、replay 缺 Outbox 源） | 补显式驱动投递与 Outbox 发布后 `Tests run: 104, Failures: 0` |

负向探针均有独立断言且验证"状态不变 / 不落库 / 留痕"：
- 重复投递只消费一次：`ContractReviewedEventChainTest.java:74`（DUPLICATE + `inboxCount==1` + 重投后零新增追溯）
- 跨租户拒绝且留痕：`ContractReviewedEventChainTest.java:92`（REJECTED + Inbox 无记录 + `CROSS_TENANT_REJECTED` 含 review_id/双方租户）
- 失败入 DLQ 可重放：`ContractReviewedEventChainTest.java:112`（FAILED + 无 Inbox 行 + DLQ 含原因/outboxRef）与 `:145`（重放成功 + `resolved=true` + 二次重放不重复）

## 3. 改动文件

**新增（Java）**：`domain/gateway/ContractReviewedEventStore.java`、
`domain/model/review/{ContractReviewedConclusion,ContractReviewedOutboxMessage,ContractReviewedDlqEntry}.java`、
`infrastructure/{ContractReviewedOutboxEntity,ContractReviewedInboxEntity,ContractReviewedDlqEntity,
ContractReviewedOutboxSdkRepository,ContractReviewedInboxSdkRepository,ContractReviewedDlqSdkRepository,
ContractReviewedEventStoreAdapter}.java`、
`application/{ContractReviewedEventPublisher,ContractReviewedEventDriver}.java`

**修改（Java）**：`ContractReviewedConsumer.java`（内存 Map → 持久化 Inbox + 租户锚定）、
`ContractReviewApplicationService.java`（override 改为事务性发布）；
4 个既有测试随构造器迁移；新增 3 个测试类（事件闭环 / 语义保持 / 仓储）。

**DDL / 迁移**：`bone-init.sql` 新增 3 表；`scripts/migration/0141_procura_contract_review_event_chain.sql`
（order=147，executableCount 146→147，已精确 `git add`，未 commit）。

**门禁 / 契约**：`tools/check_procura_persistence.py`（6 表/9 端口 → 9 表/10 端口 + 唯一键）、
新增 `tools/check_procura_event_chain.py`、`tools/check_procura_alignment.py`（追加 2 gate key + 3 注入反证）、
`.github/workflows/ci.yml` / `scripts/check.sh` / `doc/ai-product/procuraAI/scripts/check-procura-ci.sh` 三入口同步、
`00-总纲与统一约定.md` 附录 B 备注 + 重生成 `asyncapi.yaml`、`doc/ai-product/procuraAI/README.md` 清单同步。

## 4. 测试结果

- 采购全量：`mvn -o -f bone-apps/bone-procurement/pom.xml test` → **BUILD SUCCESS**；
  surefire 汇总 **458 tests / 0 failures / 0 errors / 0 skipped**（零跳过）。
- 前端：`tsc --noEmit` 通过；vitest **15 files / 107 tests passed**；ESLint JSON `errors=0 warnings=0`。
- 事件闭环专项：`ContractReviewedEventChainTest` 6 例、`ContractReviewedEventStoreAdapterTest` 6 例、
  `ContractReviewedSemanticPreservationTest` 2 例全绿；架构门禁 13 例全绿（E-5.4 / 租户收敛未退化）。

## 5. 门禁与注入反证

| 判据 | 结果 |
|---|---|
| `check_procura_alignment.py` | 10/10 PASS；`--self-test` **157/157** 转红且命中预期 key，含新增三入口删判据反证 |
| `check_procura_persistence.py` | PASS 9 表/双唯一键/10 端口；`--self-test` ALL PASS（含删事件闭环表/唯一键/端口实现） |
| `check_procura_event_chain.py`（新） | PASS 3 表/必备列/唯一键/4 探针；`--self-test` ALL PASS（10 条负向 + 正例） |
| A10.17 三入口一致 | ci.yml 10 个 ProcuraAI blocking 步骤 / 本地入口 11 判据 / check.sh 6 判据子集，集合精确匹配 FULL/FAST |
| `generate_asyncapi.py --check` | PASS，29 事件零漂移；ContractReviewed 通道说明已含 Outbox/Inbox/DLQ，payload 7 字段未动 |
| `check_procura_evalset.py` | RESULT: PASS；`--self-test` 69/69（脚本无 `--check` 参数，按真源口径调用） |
| `check_procura_frontmatter.py` | PASS 33 份 |
| `check-migration-manifest.py` | PASS 154 文件 / 147 可执行脚本 / order 1..147 连续 |
| `check-ddl-required-columns.py --check` | PASS 新增表 0 违规 |
| A10.16 黑名单 | 新增文案未触黑名单精确串；alignment 主门禁已复核 |

## 6. 唯一最终独立审查

逐文件核对 diff（18 个修改文件 + 20 个新增文件，均与本 change 相关；
`synapclaim-*` 两个在途 change 未触碰），并复核：租户锚定不取事件自报、事件 7 字段未改、
SDK `@Column` 齐全且审计列只用 `java.util.Date`、`repo.save` 仅在 infrastructure Adapter、
三入口判据集合与 `--self-test` 注入反证一致、README 清单由 `--sync-manifest` 真源同步。
**结论：0 CRITICAL / 0 IMPORTANT / 0 阻塞项。**

## 7. tasks 勾选

`openspec/changes/.../tasks.md` **14/14 `[x]`**，经
`comet state task-complete ... --expect 7693df60...` 逐项写入，revision 保持稳定哈希未变。
（用户口径称 13 项，但 tasks.md 权威清单实际 14 项，按权威全部勾选。）

## 8. 遗留与范围外

- 生产 MQ producer/listener、真实死信重放调度属 L3；本轮只交付本地持久化 + 显式驱动。
- 跨租户拒绝为终态（写留痕、不入 DLQ）；如未来需要跨租户告警再单独立项。
- 未执行任何 `git commit` / `git push`，仅精确 `git add` 了迁移脚本以满足 manifest M11；交付由主 agent 统一处理。
- 远程 `origin/dev` 已有新提交，主 agent 收敛时按既有 rebase 纪律处理（本轮改动与其零文件重叠）。
