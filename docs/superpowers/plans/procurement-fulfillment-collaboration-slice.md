---
change: procurement-fulfillment-collaboration-slice
design-doc: openspec/changes/procurement-fulfillment-collaboration-slice/design.md
base-ref: 27e1948df1a6e17f5bda35c660daed63e66777d1
archived-with: 2026-11-18-procurement-fulfillment-collaboration-slice
---

<!-- comet-task-authority: openspec/changes/procurement-fulfillment-collaboration-slice/tasks.md -->

# Plan — 采购履约核心协同闭环

## 范围与约束

- 目标：交付 05 履约核心协同闭环——PO 确认(855) → ASN(856) → 自助开票(810) → IR 例外台账，形成 PR→PO→确认→发货→开票端到端。
- 设计真源：`openspec/changes/procurement-fulfillment-collaboration-slice/design.md`（D1–D7）。
- 行为真源：5 份 delta spec（po-acknowledgement / asn / invoice-submission / ir-exception / fulfillment-console），共 22 Requirement；`tasks.md` 30 个 tracked task（8 组）。
- 红线：不改 `bone-app-purchase`、不搬迁已交付模块、不写 Outbox/MQ/Saga、不引 BFF/外部账号、不做 GR 投影真实消费/三通道对账、不改 CI 判据、不连生产库、`PoStatus` 只解锁 `SENT→ACKNOWLEDGED` 一条迁移。
- 工作区：`comet/procurement-fulfillment-collaboration-slice` worktree（`isolation=worktree`，基于 `comet/procurement-supplier-mdm-slice`），只改本 change 相关文件。
- 执行配置：`build_mode=autonomous`、`tdd_mode=tdd`、`review_mode=thorough`；TDD 严格遵守 RED→GREEN→REFACTOR，负向探针注入必红/还原必绿；thorough 做任务级审查 + 最终集成独立审查。

## 依赖顺序

```
1 骨架 → 2 DDL/领域 → 3 PO 状态受控解锁 → 4 端口桩 → 5 应用服务
                                                          ↓
                            8 集成验收 ← 7 前端履约页 ← 6 Web 契约/权限/种子
```

- 2–5 严格串行（DDL → 领域不变量 → PO 状态解锁 → 端口 → 应用服务）。
- 6 组（Web 契约）在 5 组应用服务契约冻结后开始；7 组（前端）在 6 组端点冻结后开始。
- 8 组为集成验收，调用前 1–7 组的产物，不另起实现。
- 每组自带其测试与文档；不在末组集中补测。

## 环境命令（离线）

- JDK21=`/Users/renhui.trh/tools/jdk/jdk-21.0.5.jdk/Contents/Home`；Maven=`~/.m2/wrapper/dists/apache-maven-3.6.3-bin/1iopthnavndlasol9gbrbg6bf2/apache-maven-3.6.3/bin/mvn`（不在 PATH）。
- 后端模块：`mvn -o -f bone-apps/bone-procurement/pom.xml clean test`（先 `spotless:apply` 绕过 validate 阻断）。
- 前端：`cd bone-frontend && npm run -w @bone/procurement-app typecheck / eslint -f json / vitest run`（eslint 必须 JSON 解析 errorCount，默认 formatter 假绿）。
- 门禁：G32/G40/G41 脚本 + procuraAI 对齐门禁；M5 负向探针注入必红/还原必绿。

## 实施分组与任务映射

### 组 1 — 模块脚手架与装配

<!-- comet-task-ref:36071334-d5a9-4026-9c95-befb46980aa4 -->
- 1.1 新建 `bone-procurement-fulfillment` Maven 模块，加入聚合器；RED：先写模块存在性/依赖解析测试，GREEN：建 pom + 聚合器声明。验证 `mvn -o validate` 识别模块。
<!-- comet-task-ref:cc774fd4-9346-4a67-a7a9-e2cfbad6933b -->
- 1.2 app 装配履约模块扫描与依赖；RED：启动类测试断言能加载到履约包，GREEN：配置扫描。验证能力开关测试仍绿。
<!-- comet-task-ref:4deb3a2f-d0ea-4468-a9b1-a089da703c69 -->
- 1.3 DDD 四层包骨架 + ArchitectureTest（依赖方向/Controller `@PreAuthorize`/仓储白名单）；RED：注入违规依赖转红，GREEN：骨架符合规则。

### 组 2 — DDL 与领域模型

<!-- comet-task-ref:2cc2eace-f0cc-4ab7-b99f-650ac7b5ddf8 -->
- 2.1 `bone-init.sql` 新增 5 张履约表 + 列级对账测试；RED：DDL 覆盖测试缺表转红，GREEN：按 05 卷 §4 补齐。验证 `DECIMAL(19,4)`/UTC/唯一键。
<!-- comet-task-ref:7df46352-31c3-4319-9eb3-b53f2a2ed667 -->
- 2.2 `PoAck` 聚合 + ack_type 三态 + `(po_no,po_version)` 幂等；RED：幂等/终态拒绝用例，GREEN：领域实现。
<!-- comet-task-ref:da88a7b2-546b-46a3-b4d1-e0e6107921dc -->
- 2.3 `ASN` 聚合 + 显式迁移表 + `AsnLine`/SSCC 校验；RED：非法迁移/包装引用用例，GREEN：状态机实现。
<!-- comet-task-ref:77e485e9-e8d0-418c-97a7-a01f00e9972c -->
- 2.4 `InvoiceSubmission` 聚合 + 状态机 + 997 不越级；RED：31506 拒绝用例，GREEN：状态机实现。
<!-- comet-task-ref:67ff4551-34a6-41c9-abcd-91fd9c348ef5 -->
- 2.5 `IRException` 聚合 + 状态机 + 采购域无 RESOLVED 入口；RED：31505 越权用例，GREEN：状态机实现。
<!-- comet-task-ref:670b0086-5d66-458d-a649-e4f28e357960 -->
- 2.6 Money 语义对齐 + D1–D6 精度用例；RED：精度漂移/币种用例，GREEN：复用 transaction Money。

### 组 3 — PO 履约状态受控解锁（关键改动点）

<!-- comet-task-ref:8557b7c5-6a95-4145-84a9-56b629c42d82 -->
- 3.1 `PoEvent.ACKNOWLEDGE` + `PoStatus` 仅 `SENT→ACKNOWLEDGED`；RED：PoStatusTest 断言「只允许这一条新迁移、其余保留态仍拒绝」，GREEN：新增单条迁移。守住对已冻结状态机的唯一改动。
<!-- comet-task-ref:2d3f3d48-cb9a-487d-b569-a450658434c2 -->
- 3.2 确认锚点：PO 已确认成为 ASN 上游；验证 transaction 既有用例全绿、无越级迁移。

### 组 4 — 端口与 fail-closed 桩

<!-- comet-task-ref:1de44504-ff99-40af-84c3-7e0c543a3d61 -->
- 4.1 定义 `PoQueryPort`/`InventoryAclPort`/`FinanceAclPort`/`ToleranceConfigPort`；RED：default fail-closed 用例，GREEN：端口默认拒绝。
<!-- comet-task-ref:bd2d4506-392c-4593-a27d-727a291486f5 -->
- 4.2 `PoQueryPortAdapter` 只读实现；RED：读真实 PO 与行快照用例，GREEN：经 application service 只读适配。
<!-- comet-task-ref:88cd6e79-7f27-47f5-84f3-15daf6f44af6 -->
- 4.3 跨上下文默认桩 + 测试夹具；RED：M5-4/M5-6/M5-11 负向（不推断通过），GREEN：桩运行期不触发 + 待态保持。
<!-- comet-task-ref:0bb8eae0-06ab-42ee-9cac-71c025ecfa7f -->
- 4.4 `ToleranceConfigPort` 配置化容差；RED：M5-12 TDS 超容差必红，GREEN：按供应商/品类读取 + 逐行舍入。

### 组 5 — 应用服务与命令

<!-- comet-task-ref:6c9a275e-9c6b-46a0-b4fd-b78b808c7d5b -->
- 5.1 `PoAckApplicationService.ack`：身份绑定 + CAS 幂等 + 终态拒绝 + 驱动 ACKNOWLEDGED；RED：M5-1/INV-01/02，GREEN：实现。
<!-- comet-task-ref:8a9a8454-eb0f-4185-826d-d6e87385e89d -->
- 5.2 `AsnApplicationService`：asn_no 唯一 + 行引用已确认 PO + 数量/包装校验；RED：M5-3/INV-03/04，GREEN：实现。
<!-- comet-task-ref:ff682fa0-1650-46ea-b021-304ec2eeb3df -->
- 5.3 `InvoiceSubmissionApplicationService`：去重 + 未开票余额 + TDS + 只提交；RED：M5-2/05/12/07，GREEN：实现。
<!-- comet-task-ref:38992773-de03-4101-92db-1d3e7b32bf23 -->
- 5.4 `IrExceptionService`：登记/补料 + Finance 回写；RED：M5-6/重复码幂等，GREEN：实现。
<!-- comet-task-ref:3ec32de2-24e7-4822-beef-dd7aacb3f7a1 -->
- 5.5 履约只读聚合查询 + 例外 cursor 分页；RED：分页/跨供应商零数据用例，GREEN：实现。

### 组 6 — Web 契约与权限

<!-- comet-task-ref:4f963f5a-f61d-4a85-8dc9-c5c15a2ba2db -->
- 6.1 6 端点 Controller，`@PreAuthorize` + `procurement:fulfillment:read/write`，31501–31506 + 409；RED：契约测试，GREEN：实现。
<!-- comet-task-ref:147ae7a1-c999-42c8-9c15-24c87fdde4e4 -->
- 6.2 `ProcurementExceptionHandler` 扩展履约异常到 problem+json；RED：错误码/结构断言，GREEN：实现。
<!-- comet-task-ref:37784fa5-1035-4802-9539-d11bc967220b -->
- 6.3 权限码 migration SQL + 履约协菜单叶子（避开保留字别名）；验证 MySQL exit=0 + G40 门禁 + 无残留临时表。

### 组 7 — 前端履约协同页

<!-- comet-task-ref:b198ac5f-f688-4985-a5ef-fc326030f829 -->
- 7.1 `fulfillmentApi.ts`（6 端点）+ types + 单测；RED：请求/响应映射用例，GREEN：实现。
<!-- comet-task-ref:3771a426-fa9b-423b-8a9e-91c13642ae76 -->
- 7.2 履约页 4 视图 + 路由 + 菜单 + 挂载/空态/按钮按态用例；RED：组件测试，GREEN：实现。
<!-- comet-task-ref:0a9a56ed-6c70-4b3f-97e6-cb2d7aec0ada -->
- 7.3 失败与权限降级；RED：31504 保留表单/只读隐藏写按钮/403 提示用例，GREEN：实现。
<!-- comet-task-ref:b1134621-0d19-4fbb-bfc9-7d597810a07a -->
- 7.4 前端 typecheck + eslint(json errorCount=0) + vitest 全绿；验证三条命令 exit=0。

### 组 8 — 集成验收与证据

<!-- comet-task-ref:dab49fea-4d8b-4e52-a7b4-61bb298ec32d -->
- 8.1 后端 `mvn -o clean test` 全绿（transaction + fulfillment）；验证退出码 0、用例数与基线。
<!-- comet-task-ref:14ce3733-592e-4a64-8bf0-725cf7070186 -->
- 8.2 G32/G40/G41 + procuraAI 对齐门禁未转红；验证脚本 exit=0、A10.16 黑名单未命中。
<!-- comet-task-ref:027977d0-b29d-45e2-89b1-3c3a8a5962f7 -->
- 8.3 真实浏览器 CDP E2E：登录→菜单→履约页→确认/ASN/开票/例外交互（含降级）；截图 + evidence JSON 落 `outputs/`，无白屏。

## 失败处理与回退

- TDD 失败：RED 未按预期失败则修测试；GREEN 未过则改最小实现；其他用例受影响立即修复。
- 门禁转红：按 debug-gate 定位，先 HEAD 基线探针判假红，再修真因；不绕过、不改 CI 判据。
- 按组回退：履约表/菜单种子可独立撤销；`PoStatus` 移除 `ACKNOWLEDGE` 注册即回 fail-closed；无数据迁移。
- thorough 审查发现 CRITICAL/IMPORTANT 必须修复；WARNING/SUGGESTION 涉及行为/范围由用户决定。
