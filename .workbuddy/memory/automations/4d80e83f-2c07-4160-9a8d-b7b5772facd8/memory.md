# 自动化：夜间模块设计复核闭环（scripts/prompts/02-nightly-design-review.md）

> 口径：**每次只跑 1 个模块**，按轮换清单轮流推进（A→A'→B→B'→C→D→提交）。已完成模块记录在下表。

## 轮换进度（以 `doc/design/_review-status.yaml` + `_nightly-log.md` 为准）

| 模块（轮换序） | A/A' | B/B' | C/D | 备注 |
|---|---|---|---|---|
| bone-iam (#1) | ✅ | ✅ | ✅ | 2026-09-26/27 完成，状态 implemented |
| bone-metadata-server (#2) | ✅ | ✅ | ✅ | 2026-09-27 完成（F1 错误码） |
| bone-masterdata (#3) | ✅ | ✅（F1/F4） | — | HC-008 为真实 L3 DDL 缺口，待架构师 |
| bone-integration (#4) | ✅ | ✅（F1+F4） | — | INT_* 11 码落地 |
| bone-system (#5) | ✅ | ✅（S-1/S-2 + P0/S-8/S-11） | — | SystemErrors 四参修复；剩余 S-4~S-10、C/D |
| bone-extension-studio (#6) | ✅ | ✅ | ✅（契约级） | X-1/X-2/X-3 落地，93 测试全绿 |
| studio-generator (#7) | ✅ | ⬜ | — | **2026-09-29 因并发会话在途改动被跳过**，需回补 B（G-1 租户闭环）；届时注意 `CodeGeneratorServiceImpl.java` 可能已被对方改过 |
| bone-file (#8) | ✅ **2026-09-29 本轮** | — | — | 从零出 v1 设计稿（FL-1~FL-5 阻断级），状态 design_ready |
| bone-notification (#9) / bone-gateway (#10) | ⬜ | — | — | 无设计稿，需从零出 v1 |

## 执行记录

### 2026-09-27（首跑）bone-extension-studio B 段
- 分支 `codex/nightly-bone-extension-studio-20260927`（基于 `26e43321b`），commit `ba6ac38eb`，已推 Gitee+GitHub；pre-commit 五道门禁全绿。
- 落地：X-1 租户注入收口、X-2 错误码、X-3 deployPlugin 收敛。测试 93 全绿。

### 2026-09-29 bone-file（轮换 #8，A 段从零出 v1）
- **跳号**：#7 `studio-generator` 正被并发会话占用（`git diff --stat` 15 文件 691+/296-，新增 7 个模板生成器 + 7 个 .ftl，`BUILT_IN_TEMPLATE_TYPES` 5→12 类 = A 段 G-4 的在途 B 段实现，最后改动 03:41）→ 顺延到 #8。
- 产物：`doc/design/modules/bone-file-设计方案.md`（v1，438 行，10 章 + 附录 A 13 条证据）、`doc/design/modules/review-report-bone-file.md`（150 行）、`_review-status.yaml` bone-file→design_ready + 2 条 warnings、`_nightly-log.md` 追加一行。
- **未提交**（工作树 48 项他人在途改动，pre-commit 会跑全量 `scripts/check.sh`，风险不可控）；产物留在 `dev` 工作树，提交命令见下。
- 阻断级 5 条：FL-1 越权下载/删除、FL-2 零租户维度、FL-3 路径遍历 + Content-Disposition 注入、FL-4 零元数据表（L3）、FL-5 5 处裸中文异常无码无 i18n。建议级 8 条。
- **锚定误报第 5 例**：HC-007 语义是「**PR 的** OpenAPI spec 不得引入 breaking change」，锚定却以「openapi 下无 file spec」判 violated → 应为 not_applicable。

## 运行要点（下次照做）
1. **不要用 `git worktree`**：Comet hook 会因 worktree 缺 `.comet/config.yaml` 直接 fail-closed 拦掉 Edit/Write（且不许碰 `.comet/**`）。只能在主工作树做。
2. **并发会话**：开工前 `git status --porcelain` + `git diff --stat <下一模块路径>` 双重盘点。**若下一模块正被在途改动占用（尤其是「改动内容就是本模块 A 段 finding 的实现」），直接顺延到下一未被占用模块**，并在 `_review-status.yaml` 的 `warnings` 留痕 + 报告第〇章写清跳号原因。
3. **脏工作树下不提交**：pre-commit = `./scripts/check.sh` 全量自检，会把他人 WIP 一起判。产物留在工作树即可，给用户留显式路径的提交命令；**绝不 `git add -A`**。
4. 提交前先跑 `mvn -o -pl <模块> spotless:apply` + `test`（须 `dangerouslyDisableSandbox`）；`python3 scripts/check-i18n-sync.py` 单独跑一遍更快。
5. **`design_doc == null` 的模块**（bone-file / bone-notification / bone-gateway）：只产出 v1 设计稿（功能 + UI 完整），状态停 `design_ready`，不进实现。写稿前先跑一遍「反向 0 命中」类扫描（TenantContext / PreAuthorize / 错误码 / DDL 表 / OpenAPI / 前端应用），这些是阻断级 finding 的主要来源。
6. **所有 `文件:行` 引用必须回读核对**（`grep -n`）。本轮设计稿初稿有 8 处行号漂移（FileController 26→29、31→32、47→45、72→70；SecurityConfig 62-72→73-90；application.yml 41-44→34；锚定 475→485），已逐条修正。

### 2026-09-29（第 2 次运行）bone-gateway A+A' 段（轮换 #10）
- ⚠ **本自动化出现并发双实例**：另一实例同夜做了 bone-file #8（同 HEAD `332c1b925`、同 `dev`）。本实例让路改做 bone-gateway。**根因（重复触发）未消除，需核查调度配置**。
- 模块选择链：`studio-generator`(#7) 被在途改造占用 → `bone-file`(#8) 被并发实例占用 → `bone-gateway`(#10)。
- 产出（**untracked 未提交**）：`doc/design/modules/bone-gateway-设计方案.md`(v1) + `review-report-bone-gateway.md`。
- 阻断级 2 项：G-1 熔断配置三源冲突（yml `resilience4j.*` 实际不生效，运维调参无效）、G-3 错误信封与 `ApiResponse` 不一致（网关是唯一入口）。建议级 G-2/G-4~G-11。
- **未提交**：pre-commit 的 `check-i18n-sync` 被并发会话 WIP 阻塞（3 个 `GEN_*` 未登记台账/语言包）。**已 `git reset` 撤销暂存**——保持 staged 会被对方的下一次 `git commit` 误卷入。
- 未改 `_review-status.yaml` / `_nightly-log.md`（并发会话正在写）。状态迁移待补：bone-gateway → `design_ready` + `stages_done [A,A']`。

## 运行要点（下次照做，2026-09-29 增补）
4. **开工前先查并发**：`find . -newermt "<15 分钟前>" -type f` 看是否有别的实例正在写；重点看 `doc/design/` 是否刚出现别人的新产物。撞车就换模块，别抢。
5. **不要 `git add` 后留着 staged**：并发环境下，staged 文件会被对方的 `git commit`（不带 `-a` 也只提交 staged）误卷入。提交失败立刻 `git reset` 撤销暂存，让产物回到 untracked。
6. **门禁可能被他人 WIP 阻塞**：`check-i18n-sync` 会扫**全仓**代码常量，别人新增 ErrorCode 未同步台账时全仓库提交都会被拦。**绝不用 `--no-verify`**；改为说明原因、留 untracked。
