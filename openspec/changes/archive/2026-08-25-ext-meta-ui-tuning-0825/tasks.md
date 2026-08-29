## 1. 扩展前端路由与页面修复

- [x] 1.1 在 `bone-extension-app/src/App.tsx` 将 `/logs` 路由从 `SandboxManagement` 改为指向新建的 `ExecutionLogPage`，并确认 `/sandbox` 仍指向 `SandboxManagement`；验证：`npm run typecheck` 于 `bone-extension-app` 无错误，路由表无重复指向
- [x] 1.2 从 `SandboxManagement.tsx` 抽取执行日志逻辑到 `ExecutionLogPage.tsx`（独立 `loadLogs` 调 `listExecutionLogs`），沙箱页仅保留沙箱配置 + 审计日志（`listAuditLogs`）；验证：`grep -rn "loadLogs"` 仅 `ExecutionLogPage` 一处定义
- [x] 1.3 修改 `DeploymentStateDiagram.tsx` 的容器：新建 `DeploymentManagementPage.tsx`，提供插件选择器并调 `getDeploymentState(pluginId)` + `getDependencyGraph()` 渲染真实部署状态/依赖图，含"暂无部署数据"空态；`/deploy` 指向该页

## 2. 元数据服务端 Controller 集成测试

- [x] 2.1 在 `bone-metadata-server/src/test` 新增 `MetadataManagementControllerTest`（`@SpringBootTest` + MockMvc），覆盖实体 CRUD（`/api/v1/metadata/entities`）；验证：测试编译通过且各 CRUD 场景绿
- [x] 2.2 在集成测试中覆盖字段创建与校验规则写入读回（`/fields`）；验证：创建字段后查询实体详情包含该定义
- [x] 2.3 在集成测试中覆盖视图与审批流接口（`/relationships` 可达且返回结构化结果）；验证：接口返回非空或定义明确结构，无未处理异常

## 3. 元数据服务端真实 MySQL E2E

- [x] 3.1 在 `bone-metadata-server/src/test` 新增真实 MySQL E2E 测试类（复用本地 `bone` 库，通过 `@DynamicPropertySource` 注入 `BONE_DB_PASSWORD`）；验证：测试连接本地 MySQL 成功运行
- [x] 3.2 在 E2E 中固化 moduleId 引用 IAM 校验异常路径：创建实体指定不存在的 `moduleId` 被 `IamModuleValidator.requireExists` 拒绝且无脏数据写入；验证：断言返回 400 + "所属模块不存在" 且 `meta_entity` 行数前后一致
- [x] 3.3 在 E2E 中验证建模数据真实落库并可经接口回读（实体 + 字段）；验证：直接查 MySQL `meta_entity` 表与接口查询一致

## 4. runtime 引擎归属核对

- [x] 4.1 核对 `bone-metadata-engine` 是否暴露 `/api/v1/runtime` 或被 `bone-metadata-server` 装配；结论：runtime 引擎挂载于 `bone-metadata-server`（`runtime/adapter/web/RuntimeRecordController.java`），前端 `metadataApi.ts` 的 `RUNTIME='/api/v1/runtime'` 归属正确；记录于 `design.md` "Runtime 核对结论" 小节

## 5. 验证与收尾

- [x] 5.1 运行 `bone-metadata-server` 全部测试（`mvn test`），确认集成测试 + MySQL E2E 真实通过（37 tests, 0 failures, 0 errors）
- [x] 5.2 对 `bone-extension-app` 运行 `npm run typecheck` + 对改动文件单独 `eslint` 全绿（既有 5 warning 来自 main.tsx/Marketplace/vite-env.d.ts，非本次引入）
- [x] 5.3 运行 `openspec validate --change ext-meta-ui-tuning-0825` 通过（`valid: true`，仅 RFC2119 措辞 WARNING 非阻塞）
