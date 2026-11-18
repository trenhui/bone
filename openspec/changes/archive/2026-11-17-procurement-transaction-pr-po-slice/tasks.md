# 任务清单

## 1. 模块骨架与构建接入

- [x] 1.1 新建 `bone-apps/bone-procurement` 聚合器 pom（packaging=pom，modules 含 app 与 transaction），并在 `bone-apps/pom.xml` 增加 `<module>bone-procurement</module>`。验证：`mvn -o -f bone-apps/pom.xml validate` 通过且新模块出现在 reactor 列表。 <!-- comet-task:83602e67-4b34-4a5b-a2e1-22f929670a03 -->
- [x] 1.2 新建 `bone-procurement-transaction` 模块（parent 指向聚合器，依赖 bone-core / bone-security / bone-web / bone-metadata-sdk / spring-boot-starter-web / validation / test），建立 `com.bone.procurement.transaction` 四层包骨架。验证：`mvn -o -f bone-apps/bone-procurement/pom.xml -pl bone-procurement-transaction compile` 通过。 <!-- comet-task:7f3691aa-7595-45ea-98f2-925af697229a -->
- [x] 1.3 新建 `bone-procurement-app` 模块（`ProcurementApplication` + `application.yml` + 能力开关配置类），装配口径对齐 `ClaimApplication`（`@EnableSqlRepositories` + `@Import(MetadataAutoConfiguration, SqlRepositoryAutoConfiguration)`）。验证：`mvn -o -f bone-apps/bone-procurement/pom.xml -pl bone-procurement-app compile` 通过，且装配测试能加载上下文。 <!-- comet-task:0c68b0e0-2127-41eb-aadd-29804c87b056 -->

## 2. DDL 与持久化映射

- [x] 2.1 在 `bone-init.sql` 追加 `t_proc_requisition` / `t_proc_po` / `t_proc_po_line` / `t_proc_price_snapshot` 四张表，含必备列（id / tenant_id / 审计四列 / deleted / version）、唯一键与索引，金额列 `DECIMAL(19,4)` + `currency CHAR(3)`。验证：DDL 在测试库执行无语法错误，唯一键与索引可查。 <!-- comet-task:4555e3e2-c66d-41f3-973b-889490c6b936 -->
- [x] 2.2 用 metadata-sdk 实体（`@Table`/`@Column`）映射四张表，领域仓储接口继承 SDK `Repository`，审计列用 `java.util.Date`。验证：新增「DDL 列 ↔ 实体字段」对账测试，任一列未映射即失败；查询实体可正常往返读写。 <!-- comet-task:459c5989-61cb-42f0-be7e-a707d9fbb8af -->

## 3. 领域模型与不变量（先写测试）

- [x] 3.1 实现 `Money` 值对象：`scale=4`、`HALF_UP`、跨币种运算抛错；行金额按行舍入后再汇总。验证：单测覆盖四位小数保留、逐行舍入求和、跨币种抛错三个用例。 <!-- comet-task:d62c69ad-0c47-491b-93db-b7f1a25f1ece -->
- [x] 3.2 实现 `Requisition` 状态机（显式迁移表，未列出即拒绝）：`DRAFT → SUBMITTED → APPROVED → CONVERTED`，`CANCELLED` 终态。验证：合法迁移通过；`DRAFT` 直接转单、`CONVERTED` 回写、`CANCELLED` 再提交三类用例均被拒绝。 <!-- comet-task:5651e379-637c-4921-8f64-5634bf748459 -->
- [x] 3.3 实现 `PurchaseOrder` 状态机与金额一致性不变量：`DRAFT → SUBMITTED → APPROVED → SENT`，`CANCELLED` 终态，`po_amount = Σ line_amount`。验证：总额与行合计不等被拒；`DRAFT → SENT` 跳步被拒；`CLOSED`/`CANCELLED` 回写被拒。 <!-- comet-task:071a51d6-5163-4745-8534-37aa87cc7151 -->
- [x] 3.4 实现 `PriceSnapshot` 计算与 `computed_hash`：覆盖行净价、税额、币种、汇率取数、舍入口径、税码版本；插入后不可更新。验证：同输入两次计算哈希一致；重复触发不新增第二行同版本快照。 <!-- comet-task:8fe26667-f25a-4329-97f6-26f9cbb51de4 -->

## 4. 应用服务与端口

- [x] 4.1 定义 `VendorQueryPort` 与 `VendorQueryPortAdapter`（注入 `SupplierApplicationService.currentState`，投影为 `VendorSnapshot`）；提供测试用内存实现。验证：端口契约测试用内存实现通过；Spring 上下文装配测试确认生产 adapter 可注入。 <!-- comet-task:26b58f6c-a088-4a83-826f-56cc6393faa4 -->
- [x] 4.2 实现 `RequisitionApplicationService`：建单、提交、审批（通过/驳回）、转 PO、取消。验证：未审批转单被拒且状态不变；`CONVERTED` 重复转单被拒；转单失败（供应商未开通）时请购单保持 `APPROVED`。 <!-- comet-task:79a9ffe1-60e6-4d28-8d90-c313ba4ff14d -->
- [x] 4.3 实现 `PurchaseOrderApplicationService`：建单（Vendor 闸门 + 法人校验 + 金额一致）、提交、下达（冻结快照）、查询详情。验证：M4-1（`31003`）、M4-2（`31004`）、M4-5（`31402`）、M4-10（`31405`）四类负向用例全部命中。 <!-- comet-task:04099025-2bda-463a-898f-20b700a1e90e -->
- [x] 4.4 实现 `OrderNoGenerator`：`PR-{yyyy}-{6 位}`、`PO-{yyyy}-{6 位}`，按租户+类型+年份取最大值 +1，唯一约束冲突重试上限 3 次。验证：连续生成不重复；模拟冲突时重试生效且超限返回可读错误。 <!-- comet-task:93c2162d-8cb3-47a9-b894-140afdb13cca -->

## 5. HTTP 端点与契约

- [x] 5.1 实现 `RequisitionController` 两个端点：`POST /api/v1/procurement/requisitions`、`POST /api/v1/procurement/requisitions/{no}/convert`，统一响应信封，权限码 `procurement:requisition:write/read`。验证：契约测试锁定 path / method / 请求体 / 状态码。 <!-- comet-task:ad2996cc-be0b-4a43-9135-79c1e62e89c9 -->
- [x] 5.2 实现 `PurchaseOrderController` 五个端点：`POST /purchase-orders`、`GET /purchase-orders`（列表分页，tenantId+status+page+size）、`POST /purchase-orders/{no}/submit`、`POST /purchase-orders/{no}/send`、`GET /purchase-orders/{no}`。验证：契约测试锁定路由与响应字段，详情返回行与快照引用，列表按租户锚定并收窄分页参数。 <!-- comet-task:d988635e-fdcc-4b22-a7fb-e9168a07b34e -->
- [x] 5.3 实现全局异常映射：`31003` / `31004` / `31402` / `31403` / `31405` / `409 invalid_transition`（含 `currentState` / `attempted` / `allowed[]`）。验证：每个错误码至少一个用例断言响应体字段。 <!-- comet-task:21a5d644-a6a2-4ce9-a43d-a38c2e938e5f -->
- [x] 5.4 接入能力开关 `bone.procurement.transaction.enabled`（默认 false），关闭时不注册端点。验证：关闭态请求返回未找到；开启态端点可达。 <!-- comet-task:3656e201-0c0f-446c-8093-b70a57fb9094 -->

## 6. 前端：新建采购子应用与交易控制台

- [x] 6.1 新建 `bone-frontend/apps/bone-procurement-app` 骨架（10 文件：package.json / vite.config.ts / index.html / tsconfig.json / tsconfig.node.json / src/main.tsx / src/qiankun-entry.ts / src/App.tsx / src/components/Layout.tsx / src/services/api.ts），端口 3014、qiankun 名与 activeRule `/procurement`，入口照抄 `bone-iam-app` 的 lifecycle 单对象范式。验证：`npm run typecheck --workspace=bone-procurement-app` RC=0，`npm run build --workspace=bone-procurement-app` 成功。 <!-- comet-task:d9eb0918-0c60-4e30-b344-64e70522fcc5 -->
- [x] 6.2 shell 注册 4 处（`bone-shell/src/App.tsx` 的 microApps / MICRO_APP_ROUTE_PREFIXES / MICRO_APP_BASES / `<Route path="/procurement/*">`）+ 硬编码清单 6 处（`bone-frontend/package.json` build:apps、`dev-frontend-matrix.sh`、`up-all-frontend-e2e.sh`、`restart-all-apps.sh` 含循环上界、`check-menu-route-consistency.py`、`micro-app-matrix-probe.mjs`）+ 运行时拓扑图。验证：菜单可达性探针（起 shell + 新 app，登录后点 `/procurement#/transactions` 不白屏）；G32 菜单路由一致性门禁通过。 <!-- comet-task:a7759d95-ba8c-41e2-a3c1-0b7e1cbb9838 -->
- [x] 6.3 新增 2 个 migration SQL：权限种子（`procurement:transaction:read/write`、`procurement:requisition:read/write`、`procurement:purchase-order:read/write` 绑超管）+ 菜单种子（「采购」目录 + `/procurement#/transactions`、`/procurement#/suppliers` 两叶子，`ShoppingOutlined`）。验证：G40 权限码校验通过，图标白名单门禁通过，SQL 幂等重复执行无重复行。 <!-- comet-task:c3c989ab-32a4-4b89-822b-234326d5d2c0 -->
- [x] 6.4 迁入供应商页 7 文件（masterdata-app → procurement-app），解耦 `SupplierManagement.tsx:18` 的 `useMessage`（新 App 提供同构 `MessageContext`）；新增 `types/purchase.ts`、`services/purchaseApi.ts`（7 端点 + `normalizeTotal`）、`pages/purchaseView.ts`（纯逻辑）、`pages/PurchaseConsole.tsx` + 组件测试。验证：组件测试覆盖「建单失败保留弹窗与表单值」「迁移失败不本地推进状态」「404 降级空态」「403 只读」；供应商页原有测试随迁全绿。 <!-- comet-task:09377c3d-4ce8-49e7-8588-10f28369fda7 -->
- [x] 6.5 前端门禁全绿：全 workspace `tsc --noEmit` RC=0；`eslint -f json` 的 errorCount=0（解析 JSON 不看退出码）；vitest 全绿（含新 app 与迁入的供应商用例）。 <!-- comet-task:5962fbdf-3441-49c1-b13a-e5ac119bcd7c -->

## 7. 门禁与交付验证

- [x] 7.1 后端模块 `clean test` 全绿，含 ArchUnit 分层规则（adapter → application → domain ← infrastructure）。验证：`mvn -o -f bone-apps/bone-procurement/pom.xml clean test` 输出 0 failures / 0 errors。 <!-- comet-task:9a5d400e-763e-48ee-8a5f-0de8685dfd21 -->
- [x] 7.2 负向探针注入必红、还原必绿：至少覆盖 Vendor 未开通（M4-1）、金额不一致（M4-5）、终态回写（M4-6）。验证：临时移除对应守卫后测试变红，还原后变绿并记录两次结果。 <!-- comet-task:59c2954e-a41b-4a0a-8472-84f0fc8f3c62 -->
- [x] 7.3 前后端契约对齐复核：7 个端点（2 PR + 5 PO 含列表分页）的 path / method / 请求字段 / 响应字段逐项比对。验证：差异清单为空，或将差异如实写入验证报告。 <!-- comet-task:dab82009-c544-4009-a9ec-f66eb4ff0ce6 -->

## Workflow follow-up

- Verify 阶段：在 `docs/superpowers/reports/` 写验证报告，如实标注条件价格引擎、预算占用、GR 收货不在本期范围，以及供应商模块命名偏差。
- Archive 阶段：只提交本 change 相关文件，排除工作区中无关的历史报告改动。
