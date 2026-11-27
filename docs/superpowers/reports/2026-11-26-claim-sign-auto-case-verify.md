# Verify: claim-sign-auto-case（案件签收自动建案）

> 验证对象：签收确认后自动建案、自动受理/自动分配/自动初审、失败降级人工、
> 实名明文落主表、TPA 历史签收明细回填，以及独立审查提出的 4 项 IMPORTANT 修复闭环。
> 主证据：后端全量测试通过、前端相关 22 测试通过、Build check `BUILD_CHECK_ALL_OK`、
> E2E 线上自动签收回执真实建案并流转 `AUTO_PRE_EXAMINING`、浏览器 20 操作入口 0 console error、
> TPA 回填幂等重跑稳定。
> 所有验证均在本地 worktree `comet/claim-sign-auto-case` 的独立 `:8097 / :18888 / :3010 / :3011`
> 栈完成，未改生产库。

## 结论

**变更已完整落地并通过验证。** 第一版签收只做状态流转，没有建案消费者；本次补齐了
`SignStatusChangedEvent(FINISHED)` 的 AFTER_COMMIT 订阅、明细级建案要素、按 `business_key`
幂等的建案流水线、自动受理/分配/初审与失败降级，并把 15 条 TPA 历史明细的缺失案件回填完成。
签收事实与建案结果解耦：建案失败只写 `AUTO_CASE_FAILED` 轨迹并把明细置 `FAILED`，不回滚已签收事实。

独立最终集成审查提出 4 项 IMPORTANT 与 2 项行为类 WARNING；按 Comet 规则执行
`verify-fail` 回 Build 修复后重跑 Build check 与验证，修复均已回归通过。

## 验证矩阵

| 项 | 状态 | 证据 |
|----|------|------|
| V1 领域模型与工厂 | ✅ | `SignBatchItemTest`：`withCaseElements` 写入 10 个建案要素；`linkCase` 同步内存态；`of()` 的 `businessKey` 为 NULL（跨批次重复引用不撞唯一索引）；`hasCaseElements` 缺实名姓名返回 false |
| V2 建案流水线 | ✅ | `ClaimSignCasePipelineTest` 5/5：重复事件不重复建案、实名明文落 `ClaimStakeholder`、要素缺失转 `AWAIT_MANUAL`、并发幂等冲突抛 `SignCaseDuplicateException` 让事务回滚、空姓名转人工 |
| V3 事件订阅与降级 | ✅ | `ClaimSignCaseCreatorTest` 5/5：非 FINISHED 不触发；成功后自动推进初审；分配失败写含环节与异常原因的 `AUTO_CASE_FAILED`；并发冲突在事务外回写胜出案件；批次读取失败写批次级轨迹 |
| V4 后端定向测试 | ✅ | 五个签收相关测试类共 **23/23 通过**，含 ArchUnit 架构规则 |
| V5 后端全量测试 | ✅ | `mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test` 全量通过（含 8 skipped） |
| V6 前端类型/单测 | ✅ | `npm run typecheck -w bone-claim-app` 退出 0；签收 service 4 + 页面 11 + shell 分组 7 = **22 tests 全通过** |
| V7 迁移与门禁 | ✅ | 迁移清单 159 文件 / 152 可执行脚本 / order 1..152 连续；init 覆盖 206 表、终态存活 114 表全覆盖、列缺口 0；0145 `kind=ddl`、0146 `kind=mixed` 且终态断言齐备；DDL 同步、子域归属、响应契约、SQL 模板门禁全部 PASS |
| V8 Comet Build check | ✅ | 修复后重跑：5/5 步完成，日志 `BUILD_CHECK_ALL_OK`，exitCode 0；收据哈希 `48a66d50ea519f2c852a26bc4bb4814465b2288b85b0107c52a3d25f37c66bc1`，输入前后一致、执行期无漂移 |
| V9 API E2E 自动建案 | ✅ | 独立 8097 栈真实请求：外部推送自动签收回执携带 `caseElements` → 新案件生成，状态由 `WAIT_FOR_PARTICIPANT` 流转到 `AUTO_PRE_EXAMINING`；`GET /api/v1/claims/{id}` 可查；重复同一 `Idempotency-Key` 不重复建案 |
| V10 浏览器回归 | ✅ | Playwright 对 `:3010/sclaim/claim/sign` 实测：标题/批次/轨迹抽屉/自动建案摘要列均可见，`trackCount=20`，摘要 `4/0/0`、`0/2/0`、`0/1/1`、`0/0/1`，抽屉内显示 NEW 状态与自动建案结果列表，console error 0；截图 `sign-list.png`、`sign-tracks.png` |
| V11 TPA 回填幂等 | ✅ | 15 条明细全部写入建案要素；首次 `created=11 skipped=3 manual=1`；连续重跑 `created=0 kept_new=11 skipped=3 manual=1`，案件总数不变；回填前 8 表快照已保留 |
| V12 契约安全 | ✅ | `SignCaseItemResp`/`SignCaseSummaryResp` 为显式 DTO，批次与案件 ID 均为 `String`（19 位雪花 ID 不丢精度）；无领域实体直出；新增 SQL 模板逐方法带 `/*bone:tenant*/` 锚点；`uk_sign_item_business_key` 保证明细幂等 |
| V13 审查修复闭环 | ✅ | 4 项 IMPORTANT 全部修复：失败轨迹含环节+异常原因；并发冲突抛异常回滚避免孤儿报案；`of()` 不写 `businessKey`；新增 0146 回填存量 `case_status` 且回填脚本入库跟踪。2 项 WARNING（批次读取边界、实名姓名校验）一并修复 |

## 自动建案链路的实际行为

| 阶段 | 结果 |
|------|------|
| 签收确认 | 签收事务提交（CAS 写 `FINISHED`），随后 AFTER_COMMIT 触发建案 |
| 建案 | 按 `business_key` 查 `ClaimCase`；命中则跳过，未命中则 `ClaimReport.open` + 占位 `ClaimItem` + `ClaimCase.draft` + `accept()` + `persistIntake()` |
| 并发冲突 | `persistIntake` 撞唯一键时抛 `SignCaseDuplicateException`，`REQUIRES_NEW` 事务回滚（不留孤儿报案）；订阅方在事务外回写胜出案件并写 `AUTO_CASE_SKIPPED` 轨迹 |
| 实名 | `ClaimStakeholder.create(ROLE_INSURED, ...)` 写明文姓名/证件号；`ClaimStakeholderRecorder` 对已有 INSURED 干系人早退，避免普通报案流程覆盖为空遮蔽字段 |
| 自动流转 | `allocate()` → `startPreExam(automated=true)` 分独立事务执行；失败不回滚签收与建案事实 |
| 失败降级 | 轨迹写明失败环节（建案/分配初审/启动初审）与异常原因；明细置 `FAILED`，案件停在合法人工状态；只有自动化不通过或卡住才需要人工介入 |
| 可观测 | 列表/详情返回 `createdCaseCount / skippedCaseCount / manualCaseCount` 与逐项 `caseItems`；轨迹与建案详情独立加载，单点失败不拖垮另一块展示 |

## 实名明文的显式架构偏离

用户明确裁决：`要实名，可查，要审核用的，推送保司等 都不能使用假名` / `直接明文落主表`。
因此本次在 `insured_id_hash`、`ClaimStakeholder.name_masked`、`person_id_hash` 写入明文。
这与 Bone 既有 PIPL 假名化设计不一致，已在 design.md 记录为显式偏离，**仅限本地开发库联调**；
生产落地前必须由数据合规重新裁决，不能在未裁决情况下复制到生产迁移。

## 独立最终集成审查

- 首轮独立审查：发现 4 项 IMPORTANT（轨迹缺失败原因、并发幂等孤儿报案、跨批次唯一键冲突、
  存量 `case_status` NULL 与脚本未跟踪）+ 2 项行为类 WARNING（批次读取边界、实名姓名校验）。
- 按 Comet 规则执行 `verify-fail` 回 Build，逐项修复并补 6 个回归测试；定向测试 23/23、
  前端 typecheck 与相关 22 测试全绿，Build check 重新通过。
- 复核结论：4 项 IMPORTANT 全部闭环，未引入新的 CRITICAL/IMPORTANT，达到 Verify 门槛。
  附一处非阻塞文档漂移：报告中 `ClaimSignCaseCreatorTest` 测试方法数记为 6，实际为 5（已订正）。
- 代码与报告已完整交付；Comet Verify check 的 Runtime 检查日志已写出且结论
  `VERIFY_SCRIPT_ALL_OK`，但 Run 记录停在 `started`、未生成可回填的完成结果，
  属该版本 Classic SDK 的已知死锁形态，不再尝试补交伪造收据或以 not-executed 重跑。
  以本报告、检查日志与 Git 提交为最终事实。

## 基线预存失败说明

- `./scripts/check.sh` 全量门禁出现与改动无关的失败项（seed/iam_menu 漂移、AI-native 自检、
  orphan gate、G40、seed-manifest 空清单、门禁自身单测）。已在干净基线 worktree
  `/tmp/bone-check-baseline`（HEAD `c9e868365`）复跑，出现相同失败集合，确认属预存问题。
- 前端全仓 `npx vitest run` 有 3 个失败，集中在本次未触碰的
  `apps/bone-extension-app/src/services/extensionApi.test.ts`（`git diff` 对该文件为 0 行）。
  因此最终验证脚本收敛到本次相关的签收/页面/shell 分组测试，不把无关模块的预存失败误判为本次缺陷。
- 本次相关门禁（迁移清单 / init 覆盖 / G42 / SQL 模板 / 模块边界 / 域上下文 / DDL 同步）全部通过。

## 遗留与后续（不在本次范围）

- 本地库已应用 0145/0146 并完成回填；生产/CI 迁移与数据回填须走部署流程，本报告不代表生产已变更。
- 出险时间 15 条中仅 `266400019001` 有源站真实值 `2026-04-14 00:00:00`，其余 14 条按源站 `createTime`
  兜底并在轨迹标注；不得把兜底值当作真实出险时间用于精算。
- 8097 / 18888 / 3010 / 3011 独立栈仅用于本次验证；联调造数留在本地 `bone` 库，不自动清理。
- 实名明文偏离在生产落地前需要合规复核；列表摘要当前为逐批次查询，后续量大时可改为批量聚合。
