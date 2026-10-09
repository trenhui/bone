# 供应链补货场景演示包（模式 B · 零手写前端）

> 对应 PRD：《BONE-低代码最小闭环 PRD：建模即页面 · 发布即生效》
> 需求映射：RC-004（运行时 CRUD 契约）/ UIF-007（业务应用免手写 CRUD）/ G7（契约驱动查询）/ UC-IMP（存量纳管）

## 场景叙事（为什么贴真实场景）

供应链补货是典型业务场景：**库存低于安全库存 → 系统出补货建议 → 下补货单 → 审批 → 入库核销**。
在本工程的现状里：

| 环节 | 归属 | 说明 |
|---|---|---|
| 库存台账/低库存判定/预留核销 | **手写域（保留）** | `Inventory` 聚合（`belowSafetyStock()`）、`/api/v1/inventories/*` —— 复杂业务规则显式在代码 |
| 补货单（`bp_replenishment_order`） | **存量纳管（模式 B）** | 物理表与领域模型已在（DDL / `ReplenishmentOrder` 状态机 / 仓储），管理界面**不再手写页面**，由元数据运行时直接给出 |
| 供应商（`bp_supplier`） | **新建建模（模式 B）** | 物理表都无需手写 DDL：catalog 发布时 `align` 自动 `CREATE TABLE` |

> 这正是 PRD 的双模式叙事：**复杂逻辑走模式 A（手写代码），配置型/管理面走模式 B（元数据驱动）**——本次演示补的场景**一个前端页面都不写**。

## 文件

| 文件 | 作用 |
|---|---|
| `seed_catalog.sh` | 一键种子脚本：纳管补货单 + 新建供应商 + 建关系 + 发布 + 运行时冒烟（幂等可重跑） |
| `demo_low_stock.sh` | 手写域演示：IAM 登录 → 抬安全库存触发 lowStock → 探测补货建议 API |

前置：`bone-metadata-server`(9001)、`bone-blueprint`(8082)、`bone-iam`(8081) 已启动；`python3` 可用。

## 快速开始

```bash
cd tools/supplychain-replenishment-demo

# 1) 先预览纳管（dryRun，逆向采集 bp_replenishment_order 字段，不写库）
bash seed_catalog.sh --dry-run

# 2) 正式落地（纳管 + 建模 + 关系 + 发布 + 冒烟）
bash seed_catalog.sh

# 3) 手写域低库存演示（blueprint）
bash demo_low_stock.sh
```

成功后打开（metadata-app 本地 3004，**Hash 路由**；页面经网关 8888 调用后端，需先完成 IAM 登录获取 JWT —— 网关从 token 注入 X-Tenant-Id，直连 9001 会报 MissingTenantContext）：

- 运行时数据页（元数据驱动，零手写前端）：
  - `http://localhost:3004/#/runtime?entity=bp_supplier`
  - `http://localhost:3004/#/runtime?entity=bp_replenishment_order`
- 实体详情（建模/发布/关系/结构对齐）：脚本末尾会打印两个 `entities/{id}` 直链。

## 演示点清单（对照 PRD 能力）

| # | 演示点 | 脚本输出位置 | 契合的 PRD 能力 |
|---|---|---|---|
| 1 | 存量表逆向纳管（`import-from-table` dryRun→正式） | 步骤 ① | UC-IMP、G「存量先纳管后扩展」 |
| 2 | 建 modeling→发布，**物理表自动创建**（无手工 DDL） | 步骤 ②④ | 模式 B、RC-003 L1 变更、align |
| 3 | 发布 If-Match 乐观锁 + Idempotency-Key | 步骤 ④ | RC-001 契约先行、幂等 |
| 4 | 发布即生效（evict 后运行时立即可读写） | 步骤 ⑤ | RC-002 热加载（毫秒级 evict 语义） |
| 5 | 运行时 CRUD：POST/GET 记录、排序、分页 | 步骤 ⑤ | RC-004 契约（fields/sort/query） |
| 6 | 关系建模（补货单→供应商 ManyToOne @ supplier_code） | 步骤 ③ | 元数据关系、UIF-002 FK 渲染依据 |

## 实测记录与已知注意事项（2026-10-09 真机）

- ✅ 纳管/建模/发布/运行时 CRUD 全部通过：发布后 `CREATE TABLE bp_supplier` 自动完成；运行时 POST 供应商记录 `SUP202610097762` 成功，`?sort=supplier_code&order=asc` 排序查询正常；库存台账存量 2 条经纳管实体直接可查。
- ⚠️ **运行时记录 API 使用物理列名（snake_case）**作为字段键（如 `supplier_code`），与 catalog 建模 API 的 camelCase（`supplierCode`）不同——演示脚本已按此构造。
- ⚠️ **catalog 管理 API（详情/发布/纳管）必须带 `X-Tenant-Id`**（ADR-0029 失败关闭，缺头即 500）——脚本统一注入，可用环境变量 `MS_TENANT_ID` 覆盖。
- ⚠️ 补货单依赖 `scripts/migration/0026_replenishment_order.sql`（并行会话未提交工作），表就绪后重跑 `seed_catalog.sh` 即可自动补上（含关系）。
| 7 | 低库存预警（手写域）× 补货单（元数据面）协作 | `demo_low_stock.sh` | 双模式协作叙事 |

## 治理边界（诚实标注，必读）

1. **运行时 CRUD 不替代补货单状态机**：RUNTIME 实体的写路径是物理表 CRUD（必填/类型/唯一校验），不知道 `DRAFT→SUBMITTED→APPROVED→RECEIVED` 的迁移合法性。当前状态字段校验靠**约定**；状态机强约束（submit/approve/receive）应走手写域 API（该切片如在工作区处于开发中，以其实际就绪为准）。这正是 PRD RC-007（写路径统一校验门禁，W2）要收口的 Gap——演示时不回避这一点。
2. **删除是软删**（`deleted=1`），与平台保留列约定一致。
3. 种子脚本幂等：实体/关系已存在则跳过；重复执行不会产生脏数据。供应商冒烟记录会新增一条测试数据，可在运行时页删除。
4. **本包全部为新增文件，不改动 blueprint 既有人写代码**（遵守「现有手写前端不改」的约束）。

## ⚠️ 并发工作提示

执行本演示前注意到：工作区存在**另一会话的未提交变更**（`ReplenishmentManagement.tsx`、`scripts/migration/0026_replenishment_order.sql`、补货单仓储/领域模型等 untracked 文件，且应用服务/控制器层处于增删变动中）。含义：

- 补货场景（库存+补货）**可能正在被另一条会话以"手写页面"路线并行落地**；
- 本演示包刻意选择**互不冲突的增量路线**（纯 catalog 侧 + 运行时页，不新增/修改任何手写页面与 Controller）；
- 若手写路线落地完成，两包可共存：手写页面向导深度操作（状态机闭环），运行时页兜底管理面（低代码价值演示）——建议与并行会话的作者对齐分工后再决定保留哪条路线作为主线。

## 回滚

```bash
# 仅移除演示产生的 catalog 实体（保留物理表 bp_replenishment_order；bp_supplier 物理表如需清理请手工 DROP）
# 1) UI：metadata-app 实体详情 → 删除（DRAFT/PUBLISHED 均支持逻辑删除）
# 2) API：
#   DELETE /api/v1/metadata/entities/{id}   # 逐个
#   POST   /api/v1/metadata/entities/batch-delete   # 批量（body 见契约）
```
