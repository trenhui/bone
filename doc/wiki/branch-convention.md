# Bone 开发分支规范

> 适用范围：Bone 全部模块（bone-system / bone-metadata-server / bone-masterdata / bone-extension-studio / bone-integration 等）。
> 评审状态：团队约定稿（非 CI 强制，约定靠 PR 门禁与人工 Review 落地）。
> 关联：AGENTS.md §三（自主权与交付流程 L0–L4）、§P1–P8 流程、`./scripts/check.sh` 门禁。

## 一、分支模型

采用「单一主干 + 受控长分支 + 短期任务分支」模型，保留三条长命分支：

```
master                # 长命·受保护：稳定 / 可发布镜像
  ↑ (release 回合 + dev 已验证批次)
dev                   # 长命·受保护：活跃集成分支，CI 全量门禁在此跑
  ↑ (feature / fix / agent 合入)
release/<version>     # 长命·受保护：发布冻结分支（如 release/mvp-v1.0）
  ↑ (从 dev 切出；只收 hotfix/* 与经评审的 cherry-pick)
  └─ hotfix/<version>-<slug>
feature/<ticket>-<slug>
fix/<ticket>-<slug>
agent/<tool>-<scope>-<ticket>
```

**合并方向固定为**：`短期分支 → dev → release/<version> → master`。

## 二、长命分支职责

| 分支 | 类型 | 职责 | 合入来源 | 直推 |
|---|---|---|---|---|
| `master` | 长命·受保护 | 稳定 / 可发布镜像，始终代表已发布或待发布状态 | `release/<version>`（及 dev 的已验证批次） | 禁止 |
| `dev` | 长命·受保护 | 活跃集成分支，所有进行中工作先汇聚此处，CI 全量门禁在此跑 | `feature/*` `fix/*` `agent/*` | 禁止 |
| `release/<version>` | 长命·受保护 | 发布冻结分支，只收修复与评审后的挑选 | `dev`（切出）、`hotfix/*` | 禁止 |

## 三、短期分支（临时，合即删）

| 类型 | 命名 | 来源 | 合入 | 生命周期 |
|---|---|---|---|---|
| 需求 / 功能 | `feature/<ticket>-<slug>` | `dev` | `dev` | 合即删 |
| 缺陷修复 | `fix/<ticket>-<slug>` | `dev` | `dev` | 合即删 |
| 紧急线上修复 | `hotfix/<version>-<slug>` | `release/<version>` | `release/<version>` 并回合 `dev` / `master` | 合即删 |
| AI 智能体任务 | `agent/<tool>-<scope>-<ticket>` | `dev` | `dev` | 合即删（远程 24h 内清） |

说明：`<ticket>` 为需求 / 缺陷单号；`<slug>` 为短横线分隔的英文或拼音摘要；`<tool>` 为智能体工具名（如 `codex`）；`<scope>` 为模块或子系统（如 `bone-system`）。

## 四、AI 分支专项

- 命名：`agent/<tool>-<scope>-<ticket>`（如 `agent/codex-bone-system-328`）。**去掉原 `codex/nightly-<scope>-<date>` 的日期后缀**——日期只表示创建日，不表示在做什么，易堆积且无"完成即删"纪律时会越挂越多。
- 多智能体并行时，每个任务独立分支；**同一模块同一文件**的任务需排队，避免互相覆盖（AI 不会像人一样 `git pull --rebase` 协调，基于过时 HEAD 直推会静默覆盖或冲突）。
- 合入目标为 `dev`（非 `release` / `master`），必须经 PR + `./scripts/check.sh` 门禁；L2+ 业务改动需双人 Review（对齐 AGENTS.md 权限分级）。
- 已合入的 `codex/nightly-*` 历史分支逐步清理归档（见第七节）。

## 五、提交信息规范

沿用 Conventional Commits 风格，补充 scope 与单号：

```
<type>(<scope>): <subject> (#<ticket>)
# 例：fix(bone-metadata-server): 修复 i18n 回归导致预览侧冒穿 (#328)
```

`type` ∈ `feat` / `fix` / `refactor` / `docs` / `test` / `chore` / `style` / `perf`。
AI 分支可保留 `[nightly][<module>]` 机器前缀用于识别，PR 合入 `dev` 时归一为上述格式。

## 六、合并纪律

- 三条长分支禁止直接 push，全部走 PR。
- 一个用例 / 一个任务一个分支，禁止把不相关需求塞进同一分支（对齐 AGENTS.md P3–P8 单用例契约）。
- `release/<version>` 只收 `hotfix/*` 与经评审的 cherry-pick，禁止 `feature/*` 直合。
- PR 必须通过 `./scripts/check.sh` 门禁；L2+ 业务改动需双人 Review（权限分级 L0–L4）。
- 合并后删除临时分支（远程 + 本地）；长分支不删。
- 长分支同步：`dev` 定期合入进展至 `release/<version>`；`release/<version>` 回合 `master` 与 `dev`。

## 七、与历史分支的迁移 / 清理

- `master`、`release/mvp-v1.0` 保留，按本规范受保护。
- 已完全合入 `release/mvp-v1.0` 的 `codex/nightly-*` 分支：删除本地与远程，内容无丢失（`git branch --merged` 校验）。
- 未完全合入的 `codex/nightly-<scope>-20260927` 分支：**先确认工作已合入或已废弃，再删除**；禁止在确认前删除，以免丢失未合入提交。
- `master-20260911-tag` 等仅作 tag 冗余引用的分支：确认 tag 已保留历史后删除。

## 八、FAQ

**Q：`dev` 和 `master` 有什么区别？**
`dev` 是进行中工作的集成缓冲，CI 全量门禁在此；`master` 是稳定 / 可发布镜像，只接收经 `release` 验证过的批次。两者分离旨在给 CI 慢或需发布隔离的团队留缓冲（trunk-based 更激进的团队可只用 `master` + `release`，本规范按团队约定保留 `dev`）。

**Q：AI 能直接在 `dev` 上裸提交吗？**
不推荐。即使串行执行，分支也是零成本的可审阅 diff，且绕过分支即绕过 PR 门禁与 Review 入口。AI 任务一律走 `agent/*` 分支 + PR。

**Q：hotfix 为什么不合 `dev` 先？**
紧急线上修复目标在已发布的 `release/<version>`，需最小变更面；合入后必须回合 `dev` 与 `master`，避免修复丢失。

## 九、落地前存量核查（2026-09-28 实测）

> 将本规范从"文档"变为"执行"前，基于当前仓库实测状态记录的迁移清单。**本节为操作护栏，不改变 §一–§八 结构。**

1. **`dev` 分支当前不存在**（仓库仅有 `master` / `release/mvp-v1.0` / 若干 `codex/nightly-*`）。须先 `git branch dev <基线>`（取当前集成基线，建议 `release/mvp-v1.0`）并推 `origin/dev`，否则 `短期分支 → dev` 链路无法启动。
2. **`master` 当前落后于 `release`**（release 含 master 没有的提交），且历史上存在直合 master 的批次（如 DDD v4.7）。落地后 `master` 仅经 `release/<version>` 快进更新，禁止再直合。
3. **⚠ 禁止删除含未提交在途改动的分支**：`codex/nightly-bone-metadata-server-20260928` 当前工作树有 **41 个未提交文件**（其他 agent 在途改动），即使其提交已合入 release，也绝不可删 / reset——会丢失他人在途工作。
4. **删除 nightly 前先核验合入度**：6 个本地 `codex/nightly-*` 中 5 个（`extension-studio` / `integration` / `masterdata` / `metadata-server-20260927` / `system-20260927`）tip 尚未合入 `release/mvp-v1.0`，删除会丢提交。仅 `codex/nightly-bone-metadata-server-20260928` 已合入提交层（但仍受第 3 条约束）。
5. **`agent/*` 远程 24h 清理须限定"已合并 / PR 已关闭"**：长时跨会话任务（如 bone-system 多轮改造）若按盲 24h 定时器删远程分支，会中途销毁远端备份。建议改为 PR 合并 / 关闭时清理。
6. **既有 release 上的 AI 提交为祖父条款**：`release/mvp-v1.0` 上已有的 S-5 / S-7① 等 AI 提交属规范生效前历史，保留不动；不得为对齐规范而 force-push / reset release 重写历史（高风险）。新 AI 工作一律走 `agent/* → dev`。
