# 验证报告 r2 — procuraai-ai-first-landing

- 日期：2026-11-14
- 阶段：Verify（SDK Run `classic-full`，`full.verify.run`）
- 验证模式：full / `build_mode: direct`、`tdd_mode: direct`、`review_mode: standard`
- 前置报告：`docs/superpowers/reports/2026-11-14-procuraai-ai-first-landing-verify.md`（r1，结论 **FAIL**：2 CRITICAL + 3 IMPORTANT）；r1 原文保留不改，作为修复依据的历史证据
- **本报告结论：PASS —— 可进入 Archive**（见 §6）

## 1. 修复动作与流程合法性

r1 判定 FAIL 后，按协议执行 `comet state transition verify-fail` 返回 Build（revision 68，`verifyFailures=1`）。因修复触及需求/验收基线，Runtime 报 `CLASSIC_EVIDENCE_DRIFT`；`edit-review` 对正式材料诚实判 `requirements-changed`，未伪造复用旧批准。**用户于 2026-11-14 显式批准修订**（「批准并继续」），随后按 SDK 顺序重走：

`revise-design`（revision 69）→ `propose-design` → `decide-design approved`（72）→ `complete-design`（75）→ `submit-plan`（78）→ `complete-build`（80）→ `guard build --apply`（真实 `mvn clean test`，`ALL CHECKS PASSED — phase=verify`）→ 独立最终集成审查 → 数字精度校正后的 `edit-review non-behavioral` × 2 + `revise-plan`（84）→ `complete-build`（89）→ `guard build --apply` 再跑通过 → Verify（92，`healthy: true`，入口 3/3）。

## 2. r1 五项阻断项逐条复核（均闭合）

| 编号 | 修复 | 证据 |
|---|---|---|
| CRITICAL-1 | `tasks.md` 4 项验收文字按 `b04b2a8d..HEAD` 的 37 个新增 main 文件改写为真实交付；4 个 comet-task UUID、4/4 勾选、数量不变；原文进 `<details>` 存档 | `tasks.md:11-14`、`:16-25` 映射表、`:27-36` 存档；独立 reviewer 复核 Supplier/Vendor/LegalEntity 类零命中 |
| CRITICAL-2 | 3 份未实现 delta spec **全文未改、整体迁出** `specs/` → `deferred-specs/procurement/`；`specs/SCOPE.md` 记裁决、`deferred-specs/README.md` 记去向 | `openspec change show` → `deltaCount: 0 / deltas: []`；`git show HEAD:<原路径>` 与现文件 `diff` 三份全 IDENTICAL（117/120/83 行）；`openspec/specs/` 无 `procurement/` 目录 |
| IMPORTANT-1 | `application.yml` 摘要盐值默认值改空串 `${BONE_MASTERDATA_DIGEST_PEPPER:}`，由构造器 blank/长度校验在能力启用时抛 `DomainException`；源码 Javadoc 与实现对齐 | `application.yml:74`、`GoldenRecordApplicationService.java:61-64,92-94` |
| IMPORTANT-2 | 权限点改为 `masterdata:governance:write/read` | `tasks.md:14` ↔ `ChangeRequestController.java:46,53,61,72,80`、`GoldenRecordController.java:44,51,58` |
| IMPORTANT-3 | 新增「验收文字—代码证据映射」表（4 行，逐条列真实代码与真实测试落点），并声明 250 例只证明右列行为 | `tasks.md:16-25` |

## 3. 修复中实证新发现（r1 未覆盖）

**测试注入的摘要盐值从未生效。** `@ConditionalOnProperty` 读 `bone.masterdata.golden-record.enabled`，`@Value` 读 `masterdata.golden-record.digest-pepper`（`GoldenRecordApplicationService.java:92`）——两棵配置树。E2E 测试注入到 `bone.masterdata.*`，实为 yml 的 dev 占位串兜底。已把 `GoldenRecordHttpIntegrationTest.java:43` 注入改到正确前缀。这是「250 例全绿」掩盖的配置契约错位。

**未修留 owner**：`MasterDataOutboxRelay.java:40` 的 `@ConditionalOnProperty(prefix = "bone.masterdata.outbox-relay")` 与 `:55-56` 的 `@Value("${masterdata.outbox-relay.consumer-id}")` 同类前缀不一致；不在 r1 五项范围内，本轮不扩大改动面，需确认生产 profile 的 `enabled` 来源后单独处理。

## 4. 对 r1 报告两处事实的修正

1. r1 §4.1「`grep -rni "统一信用代码|uscc"` 在 src/main 零命中」**不成立**：`GoldenRecordMerger.java:34` 有 `IDENTITY_FIELDS = Set.of("unified_social_credit_code", "tax_id")`。只影响论证措辞，不影响 CRITICAL-1 结论（Supplier/Vendor/LegalEntity 类仍零命中）。
2. r1 §4.1 对构造器校验的描述正确，但**Java 层并非完全没有 fail-closed**；真缺陷是 yml 默认值本身满足校验、且 `:62` 注释「无默认值」与配置矛盾。本轮保留构造器校验作唯一裁决点并让注释与实现一致。

## 5. 真实执行证据（本轮实跑）

| 检查 | 结果 |
|---|---|
| `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` | **250/250、0 失败 0 错误、BUILD SUCCESS**（首次为 Javadoc 折行触发 spotless validate 假红，`spotless:apply` 后重跑） |
| `comet guard ... build --apply -- <mvn> ... clean test` | 两次均 `ALL CHECKS PASSED — phase=verify`（revision 80、89 后各一次） |
| 负向探针：删除测试 pepper 注入 | **必红** `DomainException: Golden Record 摘要密钥缺失或过短，禁止产出可反推的事件摘要` |
| 还原探针 | 增量 `test` 首次假红（编译缓存），`clean test` 复验 **4/4 BUILD SUCCESS**，文件与备份 `diff` 零差异 |
| procura 主门禁 / 注入自检 / manifest | **10/10** / **146/146** / `10/A10.28/9/6/146/27` 未变 |
| evalset / frontmatter | `PASS` / 33 份全绿 |
| 门禁与 CI 三入口 | `tools/check_procura_alignment.py`、`.github/workflows/ci.yml`、`scripts/check.sh`、`check-procura-ci.sh` **四文件零改动**；A10.16 黑名单精确串在 README / 资产登记 / 旧族 `采购AI-*.md` 零命中 |
| `deferred-backlog.md` | clean 未改动，23 项齐全 |

### 独立最终集成审查（review_mode: standard）

独立只读 reviewer 复核修复后完整 diff，结论 **「无 CRITICAL / IMPORTANT，可进入 Archive」**；逐条以 `file:line` 确认五项阻断项闭合、3 份 spec 全文未改、24 项口径一致、无新增能力码/门禁判据/CI 判据/DDL。其提出的 3 个 WARNING（端口数 9→实证 8、行号 `:101`→`:105`、plan 旧章节仍写「5 个端口 / 模块骨架 / 3 份 delta specs 已落地」）已在归档前校正，并经 `edit-review non-behavioral` × 2 + `revise-plan` 合法登记，未复用旧批准。

## 6. 结论

**PASS —— r1 的「不得进入 Archive」理由已全部消除。** 五项阻断项经源码实证闭合，修复轮已完成独立集成审查、真实构建检查与合规的 Design/Plan 重新登记，Runtime `phase=verify`、`healthy: true`、Verify 入口 3/3 通过。用户已明确授权归档并推送 `origin/dev`。

### 边界（不得据本 change 宣称超出事实）

- 实际交付仅为 `bone-platform/bone-masterdata` 上的**通用主数据 Golden Record 去重内核**：**0 个 `bone-procurement-*` 模块 / 0 条 `t_proc_*` DDL / 0 个 AI handler / 零 AI 能力**；AI 审查流水线仍为 N 态零实现资产。
- `deferred-backlog.md` 的 23 项 owner/L3 WBS 未启动；法务 D-08、F1–F4 估工、A·B·C 立项、判据纳入门禁、E-10 索引登记均留 owner。
- 文档族 X1–X4 字段级契约为纯叙述 `DesignOnly`，不在 CI 三入口判据集合内。**CI 绿 ≠ AI 可上线**。
- 3 份设计储备 spec 随 change 归档封存（不删除、不否决），后续独立 change 可取回按真实实现重出 delta spec；主 spec 不因本 change 新增 `procurement` 能力。
- 未执行全仓 `scripts/check.sh`（存在与本 change 无关的 5 类预存红项）与全仓 maven（spotbugs / Maven 版本错配），理由见 r1 §1 已接受偏差。
- 未执行生产数据库操作、未自行建/切分支、未 force push。
