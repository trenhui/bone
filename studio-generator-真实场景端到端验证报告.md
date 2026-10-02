# studio-generator 真实场景端到端验证报告

> 日期：2026-10-01（首轮）/ 2026-10-02（第二、三轮） ｜ 分支：dev ｜ 后端：`bone-engine/studio-generator` (8086)，前端：`bone-generator-app`

---

## 〇-A、第三轮（2026-10-02 下午）：主子聚合 + 存量列回填（建议项落地）

### A.1 主子聚合（一对多）代码生成

真实诉求：订单（主） + 订单明细（子）是业务系统最常见的聚合形态，单表生成器产出的是两套互不关联的 CRUD。

- **触发方式**：生成配置的 `genConfig` 增加 `childTable` + `childFkColumn`（仅支持单张主表；子表必须已同步、外键列必须真实存在，违反即提交时报错）。
- **产物**：子表全套单表产物 + 关系级产物 `{主表}AggregateApplicationService`——`createWithChildren(主命令, 子命令列表)` 一次事务创建聚合根与全部子实体；子实体经 `create` 工厂构造（必填校验生效），外键参数替换为预分配的主表 id（子命令不携带外键，聚合一致性由生成器保证）；明细为空抛 `DomainException`。不用 `FOR UPDATE`（AGENTS §一.7），主表 id 预分配即可无锁关联。
- **实现**：新领域 SPI `AggregateRelationFileGenerator`（独立于单表 `FileGenerator`——签名需要主+子两份元数据）+ `AggregateRelationServiceGenerator` + classpath `aggregateService.ftl`；模板类型清单新增第三类 `RELATION_TEMPLATE_TYPES`（非骨架、非开关产物，不进模板管理）。
- **前端**：生成配置弹窗新增「子表（主子聚合，可选）」+「子表外键列」两级联动选择（从已同步表/列取选项），多选主表时禁用并提示。

### A.2 存量列元数据回填

真实诉求：早期版本同步只写表不写列，存量表列数恒 0、生成实体只有 id 字段；列数据只能从物理库现取，SQL 无法回填。

- **端点**：`POST /data-sources/{id}/tables:repair-columns`——找出缺列表，复用幂等的同步服务（`updateFrom + 整表替换列`）逐表重新同步，返回 `{repairedCount, tables}`。
- **前端**：已同步表列表检测到缺列时显示警示条 +「回填列元数据」一键修复。

### A.3 第三轮串联验证（E2E 22/22 PASS）

`npm run e2e:real` 新增 5 步（12/12b/13 等）：

```
PASS aggregate generation produces AggregateApplicationService — files=25
PASS aggregate service composes parent + child transactionally
PASS aggregate manifest complete — 25/25
PASS javac compiles aggregate code — 25 sources（聚合服务与主/子实体命令同包编译 0 错误）
PASS POST /tables:repair-columns idempotent — repairedCount=0
```

- 后端单测 **111/111**（新增关系解析 4 测 + 聚合生成器 2 测；模板一致性门禁扩展第三类清单），spotless 通过。
- 前端 typecheck 通过。
- 本轮改动已提交（见 A.4）。

### A.4 提交记录

- 首轮 + 第二轮成果已由并发会话随 `aa2e77d0d`（feat metadata/iam/generator）入库；第三轮改动按显式路径单独提交（不携带其他会话在途文件）。

---

## 〇、第二轮（2026-10-02）：菜单去重 + 产物在线查看 + 默认模板语义

### 0.1 菜单去重（用户裁定落地确认）

- 现象：父容器 Shell 已有「代码生成」菜单组，微应用内又渲染一层本地 Sider（Studio Generator + 数据源管理/代码生成/模板管理/生成历史），双层重复。
- 修复：`GeneratorLayout.tsx` 整体删除本地 Sider（历史遗留注释保留说明），导航职责全部归 Shell，与其他微应用一致。
- **真实链路验证**（无头浏览器 + 真实登录 admin）：Shell(3000) → 网关(8888) → studio-generator(8086) 全链打通，左侧仅有 Shell 单层「代码生成」分组（数据源管理/代码生成/模板管理/生成历史），微应用内无任何重复菜单；数据源管理页真实加载出 `local-bone` 数据源。截图：`/tmp/gen-shell-datasources.png`、`/tmp/gen-shell-codegen.png`。

### 0.2 本轮新增优化（基于上轮遗留项）

| # | 级别 | 优化 | 实现 |
|---|------|------|------|
| 1 | P1 | 生成产物只能下载 zip，无法在线确认生成了什么 | 新端点 `GET /code-generation/tasks/{taskId}/files`（清单模式回路径/大小，`?content=true` 带正文）；`ListGeneratedFilesApplicationService` + `GeneratedFileView`，与 download 同口径处理 JSON 列反序列化为 Map 的存量行为 |
| 2 | P1 | ResultModal 硬编码「正在处理中」死文案——前端实际已轮询到终态才弹窗，文案纯属误导，且失败时无错误详情 | ResultModal 重写：打开即查任务状态；SUCCESS 展示文件列表 + Monaco 在线预览（下载前先看代码）；FAILED 展示 operation 错误详情；下载按钮仅在成功态可用 |
| 3 | P2 | `templateIds` 为空直接 400 拒绝，不满足「我就要一份完整 DDD 代码」诉求 | 空模板 = 全部内建（平台 PUBLISHED）模板一键生成；历史记录记 `builtin`/「全部内建模板（默认）」 |
| 4 | P2 | api.ts `getTaskStatus` 类型声明错误（声明为对象，后端实际返回状态字符串） | 修正类型与解析口径 |

**关键踩坑**：默认模板解析最初用 `findPlatformTemplatesAllTenants`（种子口径 `created_by IS NULL`），E2E 实测只产出 6/12 个文件——迁移 0015 收敛的内建模板有一半带创建人，被种子口径漏掉。新增 `findPlatformPublishedAllTenants`（`tenant_id=0 AND status='PUBLISHED'`，口径差异已在仓储 javadoc 文档化），修复后默认生成产出 13 个文件。

### 0.3 第二轮串联验证结果（17/17 PASS）

`npm run e2e:real`（新增 7b/7c/11 三步），真实 HTTP 调用 8086：

```
PASS backend health                 PASS GET /tasks/:id/files manifest — 14
PASS GET /templates — 12            PASS GET /tasks/:id/files?content=true — 14/14
PASS GET /data-sources — 1          PASS download zip — 18587 bytes
PASS GET /tables?keyword&limit — 5  PASS generated java files — 13
PASS tables keyword filters         PASS no duplicated package segment
PASS POST /tables:sync — syncedCount=1  PASS file name matches class — 13
PASS GET /synced-tables — columns=18    PASS javac compiles generated code — 13 sources
PASS POST /code-generation — SUCCESS    PASS generation without templateIds — SUCCESS files=13
PASS generation status SUCCESS
```

- 后端单测：**105/105 通过**（含 26 条 ArchUnit），spotless 通过。
- 前端：`npm run typecheck` 通过。
- UI 链路：登录 → 代码生成菜单 → 数据源管理/代码生成页真实渲染，全程走 Shell→网关→8086。

### 0.4 第二轮遗留与建议（待拍板）

1. **git 提交**：本轮改动（后端 4 文件 + 前端 4 文件 + E2E 脚本 3 步）均在工作树未提交，建议确认后走 `/commit`。
2. 主子聚合（一对多，如 `biz_sales_order` + `biz_sales_order_item`）生成能力仍为下一轮增强方向。
3. `synced-tables` 存量列数为 0 的历史数据仍需一次性回填（或提示用户重新同步）。

---

## 一、首轮（2026-10-01）模拟的真实诉求

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

## 五、首轮遗留与建议（已于第二轮部分落实，状态见 §0.4）

1. **git 提交**：改动均在工作树未提交（遵循分支纪律，当前分支 dev），建议确认后走 `/commit`。
2. `synced-tables` 历史同步记录（此前旧版本同步的表）列数仍为 0，属存量数据；如需可补一次性回填脚本。
3. `templateIds` 为空被 400 拒绝是既定校验（前端始终显式传模板）；如需"不选=全部内建"语义可再加默认值逻辑。
4. 生成器模板目前不含 `biz_sales_order_item` 的主子聚合（一对多）生成能力，可作为下一轮真实场景增强。
