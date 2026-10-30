# scripts/migration —— 存量环境的增量迁移集

> 本目录回答一个问题：**一个已经跑起来的老库，怎么追上最新的结构**。
> 新库怎么建，不在这里，在仓库根的 `bone-init.sql`（结构）+ `scripts/seed/bootstrap/`（种子）。

## 1. 先分清三条路径

| 场景 | 做法 | 为什么 |
|---|---|---|
| 全新环境 | `mysql < bone-init.sql` → `mysql <db> < scripts/seed/bootstrap/10_bootstrap_seed.sql` | init = 结构 + 最小引导数据（admin 账号 / 租户 / 内置角色）；seed = 菜单树、完整权限点、字典。只灌 init 能登录，但侧栏空白、下拉无值 |
| 已有环境（要追结构） | `python3 scripts/migrate.py --status` 看待办 → `--apply` | 增量脚本按 order 应用，账本记谁跑过、跑的是什么内容 |
| 已有环境（首次接入账本） | `python3 scripts/migrate.py --baseline` | 承认既有状态，不执行任何脚本；`--status` 会明示这批「未经执行验证」 |

**不要把 84 个脚本对空库跑一遍。** 2026-10-30 实测：65 成功 / 19 失败，失败项多为
`Duplicate column`（对象早已并入基线）或脚本自带 `SIGNAL` 校验发现目标状态不满足。迁移集面向存量，
不是幂等的建库脚本——这也是 CI 明确**不做**「空库全量 apply」的原因。

## 2. 顺序的唯一真源：`migration-manifest.json`

文件名里的 4 位数字是**变更号**，不是执行序：同一次变更的 README / RUNBOOK / 校验脚本会复用同一个号
（例如 `0008_README.md` 与 `0008_*.sql`），所以编号本身构不成全序。真正决定顺序的是 `order`。

- `order` 只给 `.sql`，且必须 1..N 连续无重复；
- 新增脚本**追加到末尾**（`order = 当前最大 + 1`），不得插入中间——插入会让所有后续脚本的 order 平移，
  账本里已有的 `order_no` 与磁盘对不上；
- 重号必须同步登记 `ordinalConflicts`（含 `verdict`）；门禁双向校验，多登少登都红。

这些由 `scripts/check-migration-manifest.py`（G38）把守，`migrate.py` 在 `--apply` / `--baseline` 前
会先跑它：**清单不可信就不许改库**。

## 3. 账本：`schema_migration_journal`

由 `scripts/migrate.py` 维护（`--status` 默认只读，不会自动建表）。要点：

- **按 `file` 认身份，不按 `order`**。operator 重编号（≈清单里插入新脚本）时，按 order 认身份会把
  「顺序变了」误诊成「有人偷改已发脚本」，进而诱导人还原一个本来没错的文件；
- **`source` 区分 apply / baseline**。`--baseline` 与真跑过在库里是同形的，不区分就等于工具亲手制造假绿；
- **失败行与从未执行分开**。`success=0` 的库可能处于半截状态，处置与 pending 完全不同，不可合并计数；
- 账本表是**环境状态，不是结构**，重新生成 `bone-init.sql` 时必须排除它。

## 4. 把结构并入全量（这是最容易忘的一步）

增量脚本建了表，`bone-init.sql` 没有 ⇒ 新环境缺表，而**缺表通常不报错**（代码已上线、DDL 没跟上，
0072 头部记的就是这种事故）。所以有一条必须走完的闭环：

```bash
# 1. 在活库确认这些表已就绪，把它们登记进 MERGED_SOURCES（scripts/sync-init-from-live.py 顶部）
# 2. 从活库导出 DDL，写进 bone-init.sql 的托管区块（末尾「6. 迁移并入的结构」）
python3 scripts/sync-init-from-live.py
# 3. 校验：区块与活库逐字节一致
python3 scripts/sync-init-from-live.py --check
# 4. 把对应条目从 init-coverage-baseline.json 的 tables 里删掉（G39 要求「只可收缩」）
python3 scripts/check-migration-init-coverage.py
```

`check-migration-init-coverage.py`（G39）按 order **模拟 CREATE/DROP 终态**再比对 init——只看 CREATE
会把「已废弃并被后续脚本 DROP 的表」误报成缺口（`t_claim_case_item` 就是这么被误判的：0047 建、
0058 删，终态不存在才是对的）。

### G39 基线的两个域，语义完全不同

- `tables`：**待并入**的欠账，**只可收缩**；并入后必须删除该条，否则判红。
- `blocked`：**明确决定不并入**的表，每条要 `reason` + `evidence`。典型是「L3 待审批、代码零引用」的
  未落地脚本（`0067_fin_voucher.sql` 的 5 张 `fin_*`）。它不是欠账而是决定——但决定会被现实推翻：
  该表一旦出现在活库，说明脚本已执行，必须并入 init 并从 blocked 移除，否则新环境仍缺表。

## 5. 下线旧脚本：`retired`，不要建子目录

脚本并入全量后，理论上可以从本目录删掉。但删了就没有「它跑过什么」的记录，也不再有校验和可比对。
所以在清单里登记 `retired`（附理由与并入时间），文件保留在同层。

**不要为了整理归档而建子目录**：门禁只看本层文件（`os.listdir` + `isfile`），放进
`scripts/migration/archive/` 的迁移对门禁完全隐身、也不会被执行——实测把一张未登记的表塞进子目录，
G38 仍报绿。M1 已直接禁止子目录。

## 6. 新增一次迁移的标准动作

1. 新建 `NNNN_<主题>.sql`（`NNNN` 取当前最大变更号 + 1；同变更的文档复用同号）；
2. 在 `migration-manifest.json` 追加条目：`order` = 当前最大 + 1、`kind`、`summary`（≥8 字，禁占位）；
3. 若脚本建表 → 走 §4 的并入闭环，把表加进 `MERGED_SOURCES`；
4. `python3 scripts/check-migration-manifest.py` 必须绿；
5. 提交。

## 7. 已知未落地项（不是遗漏，是有意的）

| 脚本 | 表 | 状态 |
|---|---|---|
| `0067_fin_voucher.sql` | 5 张 `fin_*`（会计引擎凭证） | L3 待架构师审批；代码零引用；已登记 G39 `blocked` |

这些脚本**不应**被 `--apply` 顺带执行完就当作已落地——落地的前提是审批，不是执行成功。
