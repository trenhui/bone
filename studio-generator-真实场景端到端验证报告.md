# studio-generator 真实场景端到端验证报告

> 日期：2026-10-01 ｜ 分支：dev（工作树未提交改动） ｜ 后端：`bone-engine/studio-generator` (8086)，前端：`bone-generator-app`

## 一、模拟的真实诉求

电商「订单中心」开发者在低代码平台里的典型链路：

1. 连接真实 MySQL（本库 800+ 张表），按表名前缀找到 `biz_sales_order` / `biz_sales_order_item`；
2. 同步表结构到生成器元数据（含列、类型、注释）；
3. 选择 DDD 内建模板一键生成后端代码包（entity / repository / application / adapter / test / api-doc）；
4. 下载产物并入工程——**必须可直接编译**。

为此在真实库创建了带混合列型（DECIMAL/TINYINT 状态字典/TEXT/审计字段/乐观锁/租户）的销售订单表 `biz_sales_order`（18 列）与明细表 `biz_sales_order_item`（11 列）作为场景数据。

## 二、发现并修复的问题

| # | 级别 | 问题 | 修复 |
|---|------|------|------|
| 1 | P0 | 文件名与类名不一致：`BizSalesOrderResp.java` 内声明 `class BizSalesOrderResponse`，产物必然编译失败 | `AbstractFileGenerator` 渲染后按声明类名校正文件名（通用覆盖用户自定义模板） |
| 2 | P0 | 聚合测试调用 `create(...)` 但实体无工厂、无校验，聚合与测试不自洽 | entity 模板（DB 收敛到 classpath 版）产出 `create` 工厂 + 必填校验 |
| 3 | P0(根因) | DB 模板与 classpath `.ftl` 漂移：迁移 0009 只清了平台 ID 段，5 条旧雪花 ID 脏模板残留 | 新增 `scripts/migration/0015_generator_builtin_template_drift.sql`，12 条内建模板全部收敛到 classpath |
| 4 | P1 | `synced-tables` 不返回列元数据（前端列数恒 0）；同步接口无计数反馈 | `ListSyncedTablesApplicationService` 填充 columns；`tables:sync` 返回 `{syncedCount}` |
| 5 | P1 | 包名重复段：`com.bone.order` + `moduleName=order` → `com.bone.order.order` | `GeneratorUtils.getPackagePath` 末段去重 |
| 6 | P1 | 物理表发现一次性返回全库 800+ 张表，真实库会卡 | `/tables` 支持 `keyword` 过滤 + `limit` 上限 |

## 三、前端联动改造（bone-generator-app）

- `api.ts`：`listTables` 透传 `keyword`/`limit`；同步返回类型接 `syncedCount`。
- `useCodeGeneration.ts`：表选择服务端搜索（300ms 防抖，`handleTableSearch`）；默认拉取上限 500（搜索 200）；同步成功提示带「共同步 N 张表」。
- `SyncTablesModal.tsx`：物理源下 Select `onSearch` 绑定服务端搜索。
- `npm run typecheck` 通过。

## 四、串联验证结果（14/14 PASS）

`e2e-generator-real-scenario.mjs`（`npm run e2e:real`），真实 HTTP 调用 8086：

```
PASS backend health                 PASS POST /tables:sync returns syncedCount — 1
PASS GET /templates — 12            PASS GET /synced-tables has columns — 18
PASS GET /data-sources — 1          PASS POST /code-generation?sync=true
PASS GET /tables?keyword&limit — 5  PASS generation status SUCCESS
PASS tables keyword filters         PASS download zip — 18587 bytes
PASS generated java files — 13      PASS no duplicated package segment
PASS file name matches class — 13   PASS javac compiles generated code — 13 sources
```

- 产物结构：`com/bone/order/{adapter,application,domain}` 五层齐全，含 `BizSalesOrderTest` 与 `docs/bizsalesorder-api.md`。
- **javac 0 错误**（classpath = bone-core/bone-metadata-sdk/lombok/swagger-annotations）。
- 后端单测：`mvn -pl bone-engine/studio-generator test` → **105/105 通过**（含 26 条 ArchUnit 架构守卫）。

## 五、遗留与建议（待拍板）

1. **git 提交**：改动均在工作树未提交（遵循分支纪律，当前分支 dev），建议确认后走 `/commit`。
2. `synced-tables` 历史同步记录（此前旧版本同步的表）列数仍为 0，属存量数据；如需可补一次性回填脚本。
3. `templateIds` 为空被 400 拒绝是既定校验（前端始终显式传模板）；如需"不选=全部内建"语义可再加默认值逻辑。
4. 生成器模板目前不含 `biz_sales_order_item` 的主子聚合（一对多）生成能力，可作为下一轮真实场景增强。
