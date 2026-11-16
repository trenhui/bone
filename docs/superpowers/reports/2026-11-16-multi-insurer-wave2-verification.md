# W2 `multi-insurer-biz-identity-catalog-mapping` 验收与不变量复核

> 日期：2026-11-16　范围：Wave 2（Pilot，15 项）　纳管方式：**保持 unmanaged OpenSpec**（用户裁定）
> 关联：`docs/superpowers/plans/2026-11-15-comet-all-changes-closeout-plan.md` §Wave 2 / §6 决策点 2

## 1. 结论

W2 的实现、证据、流程收口、四方 SHA 四层 DoD **全部完成**；逐项交付回执见 §8。
**15/15 任务已勾选**；核心结论是：**这 15 项在开工前没有一项「完全未实现」**——并行会话已完成主体实现（8 项完整、7 项部分），本轮工作是补缺口、补钉子、补证据、收口，而不是重写服务主体。

| DoD 层 | 判据 | 结果 |
|---|---|---|
| 实现 | 15/15 勾选 | ✅ `tasks.md` 全勾 |
| 证据 | 全模块单测 / 门禁 / 前端构建 / E2E / 变异验证 | ✅ 见 §5 |
| 流程收口 | 归档 + 精确路径提交 + 双远端 | ✅ 见 §8 |
| 四方 SHA | HEAD = origin/dev = Gitee = GitHub | ✅ 四方一致于 `12ccf41e7…`，见 §8 |

## 2. 纳管方式：为什么最终没走 `state select` / `state init`

| 检查 | 实证结果 |
|---|---|
| `comet state select <change> --json` | `ERROR: Classic artifact layout is unavailable from .comet/config.yaml` |
| `comet classic root show` | `artifactLayout=legacy`、`configuredRootExists=true`；config 里 `classic.artifact_layout: legacy` 存在 |
| **根因** | 报错文案误导。真实原因是该 change 没有 Classic state 文件（SDK 的 `comet-state.yaml` / compat 的 `.comet.yaml`），即 `comet status` 归类的 `unmanagedOpenSpec` / `cometManaged=false`。拿 SDK 状态机去 select 未纳管 change 是错配，不是配置缺失 |
| `comet classic validate <name>` | `FAIL: comet-state.yaml does not exist`（预期，不是产物损坏） |
| `openspec validate <name> --strict` | `Change ... is valid`；`status` 4/4 artifacts complete |
| `comet status` 推荐通道 | `comet classic openspec -- archive <name> -y`（non-SDK adapter）；adapter help 明确「specify a non-SDK change when using the OpenSpec adapter」 |

**三条路径的 `/tmp` 隔离探针**（复制 change + config 到临时 git 仓库，未碰共享树）：

| 路径 | 覆盖已有产物 | 新引入 SDK Run | select 可用 | 归档通道 |
|---|---|---|---|---|
| 不 init（**采用**） | — | 否 | ❌ 恒失败 | OpenSpec adapter（官方推荐） |
| `state init full`（默认 sdk） | ❌ 8 个产物 `UNCHANGED` | **是**（revision=1、`full.open` pending） | ✅ init 后 | SDK Archive + 完整 Delivery |
| `state init --runtime compat` | ❌ 全部 `UNCHANGED` | 否（生成 `.comet.yaml`） | 待测 | compat 文档式 |

规划 §153 担心的「init 覆盖已有产物」已证伪；但 init 的真实代价是**新引入一条完整 SDK Run**：`configurationReadiness.missingFields=[build_mode,tdd_mode]`，须从 `full.open` 重走 Design/Build/Plan 审批（产物早已批准），并与 W1 卡在 `full.open.revalidate` 的 Run 抢唯一 selection，在共享 `dev` + 并行会话下极易复现 procuraai `complete-delivery` 的 `baseCommit..HEAD` 夹带死锁。故按用户裁定保持 unmanaged。

## 3. 15 项逐项判定（开工前 → 本轮）

| 编号 | 开工前 | 本轮动作 | 证据 |
|---|---|---|---|
| 1.1 | 已实现 | 补「读取侧空主体不查库」显式钉子 | `ClaimConfigBizIdentityGuardTest#getPolicy_withBlankBizIdentity_readableWithoutInsurerLookup` |
| 1.2 | 已实现 | **实跑** dry-run 留档 | 见 §5.4，输出可读清单，只读不落库 |
| 2.1 | 已实现 | 核对分页/健康度/排序 | `CodeMappingApplicationService#listMappings/mappingHealth/listMisses` + `MappingManagementQueryTest` |
| 2.2 | 部分 | 补「2001 行整批拒绝」+「正好 2000 行接受」两个边界钉子 | `MappingImportTest#import_exceedsMaxRows_rejectsWholeBatchWithoutWrite` / `import_exactlyMaxRows_accepted` |
| 2.3 | 部分 | 补导入留痕 `reason_code=IMPORT`（保留 `PublishCommand` 12 参旧签名，5 个调用点零改动） | `CodeMapping#markReason`、`publishCore`、`MappingImportTest` 断言 `getReasonCode()=="IMPORT"`；偏离见 §6 |
| 3.1 | 已实现 | 核对 `ClaimCoverageOverride` + DDL 三通道 | `bone-init.sql:3533`、`scripts/migration/0112`、`V22`；`EntityTableDdlCoverageTest` 通用扫描覆盖 |
| 3.2 | 部分 | 补 PAUSE 兼容单测 | `CoverageOverrideMergeTest#publish_onPausedPolicy_doesNotChangeConfigStatusAndMergeStillApplies` |
| 4.1 | 部分 | **补齐假名名单 + 额度池聚合** | `PolicyDetailApplicationService` 注入 `QuotaAccountRepository`，`PolicyDetail` 加 `insuredRoster`/`quotaAccounts`；6 个单测 |
| 5.1 | 已实现 | 核对 `InsurerSelect` 分页加载 ACTIVE 主体 | `PolicyManagementPage` / `components/InsurerSelect.tsx` |
| 5.2 | 已实现 | 核对主体管理页与关联保单数 | `InsurerManagementPage.tsx` |
| 5.3 | 已实现 | 核对映射工作台三视图/导入/处置 | `MappingPage.tsx` |
| 5.4 | 已实现 | 核对两段式覆盖 + 合并预览对照表 | `LiabilityConfigPage.tsx`；偏离见 §6 |
| 5.5 | 部分 | **补「适用名单」「额度池」两个独立 Tab** | `PolicyManagementPage.tsx` Drawer + `claimConfigApi.ts` 两个新 interface；typecheck/build 通过 |
| 6.1 | 部分 | **本报告** | 七条不变量逐条复核 + 偏离登记 |
| 6.2 | 部分 | 补齐全量自检证据并回写 tasks.md | 见 §5 |

**实现改动文件（12 个）**：后端 `PolicyDetailApplicationService`、`CodeMappingApplicationService`、`ClaimPolicy`、`CodeMapping`、`PolicyQueryPortAdapter`、`ScenarioHarness`、`PolicyDetailViewTest`、`MappingImportTest`、`CoverageOverrideMergeTest`、`ClaimConfigBizIdentityGuardTest`；前端 `claimConfigApi.ts`、`PolicyManagementPage.tsx`。

### 3.1 唯一真源处理

`insured_snapshot`（假名化 ID JSON 数组）的解析原先只在 `PolicyQueryPortAdapter` 私有方法里。本轮把它提升为 `ClaimPolicy.parseInsuredHashes(String)`，infrastructure adapter 改为一行委托，`PolicyDetailApplicationService` 直接调用——避免两处各写一份解析器导致口径漂移。解析失败仍抛异常（不兜底成空清单，否则「数据坏了」会伪装成「没承保任何人」，表现为报案被 403 拒收却查不出原因）。

## 4. 七条不变量复核清单（6.1）

| # | 不变量 | 证据 | 判定 |
|---|---|---|---|
| 1 | fail-closed 无空串回落 | `CodeMappingResolver.java:43-45/90-91/106-108`；`CodeMapping.java:135-138` 空标准码抛「未映射请走未命中队列，不要落空串」 | ✅ |
| 2 | 未命中一码一行不撞键 | `MappingMiss.java:15-16`（UK 不含 status）、`recordMiss` 走 `findByKey`+`recordOccurrence`；`0120:194 uk_miss` + `:226-232` 终态断言 | ✅ |
| 3 | `active_flag='Y'` 唯一 | `CodeMapping.java:17-19/69-71/153`；`close/supersede` 置 NULL；`0120:92/107` + `:218-224` 断言 | ✅ |
| 4 | `superseded_by_id` 回放链 | `CodeMapping.java:79-80/179-191`；`publishCore` 调 `current.supersede(newId, now)`；单测 `publishDifferentTargetSupersedesOldRow` | ✅ |
| 5 | 身份型表无物理删除 | `ClaimInsurer#changeStatus`、`InsurerCodeItem#retire/reactivate`、`CoverageOverrideRepository` 注释；`biz-identity-registry/spec.md:8 MUST NOT` | ✅ |
| 6 | 发布前标准码验存在 | `CodeMappingApplicationService#assertStandardCodeExists`（`publishCore` / 导入逐行）；单测 `ghostStandardCodeRejected` | ✅ |
| 7 | 软删偏离的 SDK 兼容 | `deleted` 恒 0（`0120:105`、`0112:37`、`V22:24`）+ 实体状态迁移无删除 + 三不变量单测/终态断言齐备；**但「删除方法覆盖为置状态」未在本域实现** | ⚠️ 偏离，见 §6 |

## 5. 验收证据（6.2）

| 项 | 命令 | 结果 |
|---|---|---|
| 全模块单测 | `mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test` | **1423 用例 / 0 失败 / 0 错误 / 7 跳过**，BUILD SUCCESS，42.5s |
| `spotless` | `mvn -o -pl ... spotless:apply` + 每次 `test` 的 `spotless:check` | 全模块 622 文件 clean |
| 前端 | `npm run typecheck` + `npm run build` | RC=0，3368 modules transformed |
| 配置面 E2E | `./scripts/e2e/run.sh claim-config-e2e.mjs` | **86/86** |
| 全链路 E2E | `./scripts/e2e/run.sh node-api` | **41/41**（含工作台/AI 回流；小额直付、SoD、额度守恒等） |
| 存量对齐 dry-run | `BONE_DB_PASSWORD=... python3 scripts/biz_identity_align_dry_run.py` | 见 §5.4 |
| 全量门禁 | `./scripts/check.sh` | **59/59 RC=0**，末行「✅ 本地自检通过！」；实现批 pre-commit / pre-push、归档后各复跑一次 |

### 5.1 变异验证（判别力唯一证明）

基线 16/16 绿后执行 5 个变异，每个跑完立即恢复原文件并 `git diff --exit-code` 校验逐字一致：

| 变异 | 方向 | 结果 |
|---|---|---|
| `A1_guard_ACTIVE_relaxed` | 放宽：停用主体不再被拒 | KILLED（8 用例 / 2 failures） |
| `A2_guard_blank_tightened` | 互补/收紧：空主体不再放行 | KILLED（8 / 1 error） |
| `B1_import_atomicity_relaxed` | 放宽：有错误行仍继续写入 | KILLED（8 / 1 error） |
| `B2_maxrows_threshold_raised` | 阈值式：上限 2000→3000 | KILLED（8 / 1 failure） |
| `B3_maxrows_off_by_one` | 互补式：`>` 改 `>=` | KILLED（8 / 1 failure） |

**5/5 全部被杀、5/5 文件逐字恢复**。`B2` 的行数特意写死 2001 而非 `MAX_ROWS+1`，否则阈值改大时测试会「水涨船高」假绿——这是分模/阈值式变异必须配互补变异的原因。

### 5.2 前后端契约新增字段

`GET /api/v1/claim-config/policies/{policyNo}/detail` 的 `PolicyDetail` 新增 `insuredRoster[]` 与 `quotaAccounts[]`；`InsuredRosterRow` 含假名 ID、账户数、initial/claimed/frozen/available；`QuotaAccountRow` 含计划/年度/维度/责任 UUID/三值/减人状态。`planId` 前端类型为 `string`（雪花 ID 禁 number 化 / `toLocaleString`）。配置面 E2E 86/86 证明新增字段未破坏既有契约。

### 5.3 顺带修复的基线问题（重要）

按项目规范 `find src -name "*.java" -exec touch {} +` 强制全量重编译后，暴露出 **HEAD 基线本来存在的编译错误**：`ScenarioHarness.InMemoryClaimCaseRepository` 未实现 `ClaimCaseRepository.searchByKeyword(String,int)`（接口在 HEAD 里就有）。`check.sh` 只跑 `*ArchitectureTest` 且依赖增量编译，长期掩盖了它——即 Wave0「全绿」在这个维度是**增量编译假绿**。本轮按 `searchByKeyword.sql` 的真实语义补了内存夹具实现（LIKE `%xxx%` 退化为 contains，只回受控 `matchedKey`、不回 PII 值），全模块 1423 用例从此可在全量重编译下通过。

> 该夹具属共享测试夹具，可能被并行会话用陈旧副本整体覆盖；重放方式：在 `ScenarioHarness.java` 的 `InMemoryClaimCaseRepository` 内按 `findStaleEvidenceClaimsAllTenants` 锚点重新插入 `searchByKeyword` / `searchMatchedKey` / `searchLike` 三个方法，随后立刻全量编译验证。

### 5.4 1.2 dry-run 实跑输出

```
distinct 存量取值 35 个；自动匹配 30 个；未匹配 5 个
[   EXACT_CODE] 'UI-PROBE-BIZ-001' × 19 单 -> UI-PROBE-BIZ-001 ACTIVE
[         NONE] 'E2E-BIZ-002' × 1 单 -> (需人工裁决)
... 共 35 行，EXACT_CODE / EXACT_NAME / NONE 三级分类
```

脚本只做 SELECT + stdout/CSV，全文无 INSERT/UPDATE。**输出中多个统一社会信用代码被 `EXACT_NAME` 归到同一主体，属疑似误配**——这正是 dry-run 的意义：机器只出候选、人做裁决，不自动落库。本轮不执行任何对齐写入。

## 6. 诚实边界与偏离登记（不谎报完美）

| 项 | 事实 | 处置 |
|---|---|---|
| 2.3 发布留痕 | 导入已补 `reason_code=IMPORT`；单条 `publish` 的 reason_code 仍为空，操作人来自 `PublishReq.reviewerId`（客户端可自填） | **登记偏离**：未改 controller 公共签名以免破坏并行会话与 E2E 契约；导入/发布的「批次」由 `version_no` 指纹/发布批次记录，SDK 审计列 `created_by`/`reviewed_by` 记录操作人；处置链路已由 `CurrentOperator.requireId()` 强制。建议另立安全改进 change 把 `reviewerId` 改为服务端认证主体 |
| 4.1 risk 层 | tasks.md 写「plan→risk→coverage 树」，实际模型是 plan→liability 两层 | **登记模型差异**：`policy-detail-view/spec.md` 要求「责任/险种/计划层级及其限定规则」，已由 `plans→liabilities`（含 limit_*_codes / feeRules / overrides）覆盖 |
| 5.4 / 5.5 独立路由 | design.md §7 说 3 个新页面注册进 `CLAIM_ROUTES`；实际两段式配置嵌在 `LiabilityConfigPage`、详情是 `PolicyManagementPage` 的 Drawer | **登记偏离**：本轮补齐 Tab 与数据口径，未新建独立路由页（避免新增路由/权限面） |
| 6.1 第 7 条 | 「删除方法覆盖为置状态」未在本域显式实现，走的是「SDK 基类不动 + 文档例外 + 单测/门禁锁定」替代方案（`L3-待架构评审-草稿.md:135`），`deleted` 恒 0 与三不变量由终态断言锁定 | **L3 架构评审事项**，AI 不改 SDK 基类；保持现状并登记 |
| 「同步标准」 | 无独立方法/端点/按钮 | **合规等价**，非缺口：`policy-config-two-stage/spec.md:25-26` 明确「同步为只读合并语义，不产生新的覆盖记录」 |
| `.comet/check-policy.json` v2 | 规划 §176 要求归档前补 v2 | **不适用**：该配置只服务于 SDK Archive 的 "check inputs changed"；本轮按裁定走 non-SDK OpenSpec adapter，补了也不会被消费（项目铁律：不配不生效的配置） |
| L3 DDL | `t_claim_coverage_override` 三通道 DDL 已由并行会话入库 | 本轮**未新建/未回改**任何已发迁移，未执行生产库迁移 |

## 7. 收口动作

- `tasks.md` 15/15 勾选（勾选依据为 §3 逐项证据 + §6 偏离登记，偏离项如实登记而非抹平）；
- 归档走 `comet classic openspec -- archive multi-insurer-biz-identity-catalog-mapping -y`（non-SDK adapter，WIP=1 静默窗口）；
- 提交采用 `git commit -F <msgfile> --only -- <精确路径>`，不卷入并行会话在途的 `scripts/e2e/ui/probe-*.mjs`；
- 推送后分别 `git ls-remote` 核对 Gitee / GitHub 与 `origin/dev` 四方 SHA。

## 8. 交付回执（flow closure evidence）

### 8.1 两批精确路径提交

| 批次 | SHA | 路径范围 | 门禁 |
|---|---|---|---|
| 实现 | `b3dfac246` | 12 个实现/测试文件 + `tasks.md` + 本报告 + `claim-full-chain-e2e.mjs`（16 files，769 insertions / 57 deletions） | pre-commit `check.sh` 59/59 RC=0 |
| 归档 | `12ccf41e7` | 8 个 OpenSpec 产物 100% 重命名至 `archive/2026-11-16-…` + 4 个主规范新增 | 归档后 `check.sh` 59/59 RC=0 |

全程 `git commit -F <msgfile> --only -- <精确路径>`；并行会话在途的 `scripts/e2e/ui/probe-commerce.mjs` 与 `probe-metadata-loop.mjs` 始终保持未暂存、未提交。

### 8.2 双远端核对（2026-11-16，CST）

| 来源 | SHA |
|---|---|
| 本地 `HEAD` | `12ccf41e72d92a0a5f2cc46cf96f3d0e3ad268c3` |
| `origin/dev` | `12ccf41e72d92a0a5f2cc46cf96f3d0e3ad268c3` |
| Gitee `dev` | `12ccf41e72d92a0a5f2cc46cf96f3d0e3ad268c3` |
| GitHub `dev` | `12ccf41e72d92a0a5f2cc46cf96f3d0e3ad268c3` |

`git push origin HEAD:refs/heads/dev` 一次写入两个 push URL，Gitee 与 GitHub 均报 `e437e8ed0..12ccf41e7`。pre-push 影响分析：实现批命中 claim 域 8 个文件 / 3 个数据契约，同步资产 `scripts/e2e/claim-full-chain-e2e.mjs` 命中，造数资产同步与真实性判据均 0 违规；归档批 0 Java 变更、0 域命中。

### 8.3 归档现场的取证与处置

| 现场 | 事实 | 处置 |
|---|---|---|
| 首次归档被拒 | `Archive ... is already being created`；锁实际在 `openspec/changes/archive/.openspec-archive.lock`（不是仓库根），`pid=36217` 已死、mtime 2026-11-14 20:26，对应 change 已完整归档、无半成品 | 按 CLI 官方提示清理 stale claim 后重试，归档 RC=0 且锁被正常回收；该锁由 `.gitignore:360` 覆盖，未入库 |
| `scripts/e2e/impact-map.json` 覆盖度变化 | 实现批 pre-commit 生成器把保单详情端点 `cases` 从 `[]` 补回 `["claim-full-chain-e2e.mjs"]`，`covered` 242→243 | 这是同批同步 E2E 资产带来的**正向修复**，不是倒退；`commit --only` 临时索引合并造成真实索引滞后一版（`MM`），已 `git add` 对齐，不另起提交 |
| `openspec validate --strict`（不带 change 名） | 全仓扫描被环境 SIGTERM（exit 137）中断 | 非门禁判据；归档前对该 change 的 `validate --strict` 已通过，归档后的 `check.sh` 59/59 亦含 DDD 文档防漂移与 doc↔code sync lint |

> 本回执提交自身的 SHA 不入文件（技术上无法自指）；§8.2 四方一致值即「实现 + 归档」交付完成时的终态快照。
