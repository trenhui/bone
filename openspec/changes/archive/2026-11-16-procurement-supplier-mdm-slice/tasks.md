# Tasks — 采购供应商主数据模块（最小可验证切片）

## 1. 领域模型 — 供应商角色与身份

- [x] 1.1 新增 `LegalEntity`（统一社会信用代码/税号 + partyId）与 `Supplier`（合作资格）/ `Vendor`（交易与付款资格）角色聚合，二者各自持有**互不覆盖**的生命周期状态；验证：`LegalEntityTest` 覆盖双角色关联、角色不可穿透、双状态机互不覆盖 <!-- comet-task:378bcf63-9f1b-4477-9b61-aff4ed103bf9 -->
- [x] 1.2 新增 `SupplyRelationship` 聚合根与 `SupplierItem` 供货条目（均属 Purchasing Org data，Owner = 采购）；验证：领域单测覆盖关系约束与不可变语义 <!-- comet-task:d9bc9167-3e52-45a5-87ad-b265a3171c3b -->
- [x] 1.3 新增身份解析：复用既有 `EntityKeyMappingRegistry` 将本地编码解析为统一 partyId，不自建 dedup；验证：`SupplierIdentityResolverTest` 覆盖身份冲突转人工、跨租户 fail-closed <!-- comet-task:6bc3c1fd-989a-4a57-afdb-bbd4ed98a40b -->

## 2. 领域模型 — 合并血缘与双状态机

- [x] 2.1 新增供应商合并装配：调用既有 `GoldenRecordMerger` + `FieldSurvivorshipRule`，将合并结果映射为供应商视图 DTO，不修改内核；验证：应用服务测试覆盖 lineage 完整才产出、缺 lineage 整笔拒绝 <!-- comet-task:5c0701a1-6bda-43d4-ae12-eba999c3f66a -->
- [x] 2.2 新增 **Supplier 合作资格状态机**（`PROSPECT→ONBOARDING→ACTIVE⇄SUSPENDED/CORRECTIVE→EXITED`）：枚举 + 显式迁移表，迁移表未列出即拒绝，非法迁移抛 `DomainException`（携带 `current_state/attempted/allowed[]`）；`EXITED` 终态不可回写；退出前置经 `SupplierExitGuard` 端口裁决；验证：`SupplierLifecycleTest` 覆盖合法路径 + 非法迁移 + 退出前置拒绝 + 终态回写拒绝 <!-- comet-task:16ea2af2-fd25-4f34-a769-8a1b4392d32b -->
- [x] 2.3 新增 **Vendor 交易与付款资格状态机**（`PENDING_VENDOR→ACTIVE_VENDOR⇄PAYMENT_BLOCKED→INACTIVE_VENDOR`）：开通经 `VendorActivationGuard` 端口全项通过、未经闸门不得开通、域内不得自行解除 `PAYMENT_BLOCKED`、停用不得留下进行中 PO；验证：`VendorLifecycleTest` 覆盖四类守卫 <!-- comet-task:e6b6f634-7fe9-4bad-8055-7b9332ae59b1 -->
- [x] 2.4 负向探针：具名裁决缺失（`STEWARD_OVERRIDE`/`MANUAL_REVIEW`）整笔转人工、零副作用；验证：单测断言无落库无事件 <!-- comet-task:795f60e9-859d-4045-9c5e-23b7f4f7f926 -->

## 3. 应用服务与端口

- [x] 3.1 新增 `SupplierRepositoryPort` 领域出站端口与内存实现（测试/演示装配），含按 partyId/租户查询与保存；验证：端口契约单测 <!-- comet-task:c43f9557-28af-49ca-9e05-b71d4432934d -->
- [x] 3.2 新增语义化 `SupplierApplicationService`：合并解析、Supplier 合作资格迁移、Vendor 交易付款资格迁移、当前状态查询四个用例，租户与登录态一致校验 fail-closed；验证：应用服务测试覆盖四个用例 + 租户不一致拒绝 <!-- comet-task:33fe065b-9a87-488f-ae6c-df0f7fa3cee9 -->
- [x] 3.3 能力开关：应用服务与端点受 `bone.procurement.supplier-mdm.enabled` 控制（默认关闭），遵循 tenant 从登录态取值不接自报；验证：上下文测试断言关闭时不装配、开启时装配 <!-- comet-task:467ccb12-9aac-4877-a801-5fe9957823e9 -->

## 4. Adapter 与 HTTP 契约

- [x] 4.1 新增 `SupplierController`，端点返回 `ApiResponse<T>` / `PageResult<T>`，不裸返领域对象；写操作 `masterdata:governance:write`、读操作 `masterdata:governance:read`；验证：HTTP 契约测试覆盖响应包装与权限点 <!-- comet-task:2b945214-f392-45ef-9790-767cdc23c9e4 -->
- [x] 4.2 治理端点只返回裁决/版本/摘要，不回传字段原值或 PII；验证：契约测试响应体不含身份原值 <!-- comet-task:fde61e9a-4e4a-468c-9aa2-7f29c0dbdef0 -->
- [x] 4.3 开关关闭时端点不注册；验证：`@SpringBootTest` 关闭态断言路由 404/不存在 <!-- comet-task:ae06ef26-86f1-4cb7-974b-581532afb295 -->

## 5. 集成验证与门禁回归

- [x] 5.1 `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` 全绿（新增测试全部通过，既有 251 例不退化）；先 `spotless:apply` <!-- comet-task:aa155423-ca7f-43bc-a33b-99c9675b8c73 -->
- [x] 5.2 负向探针注入必红 + 还原必绿：跨租户 / 身份冲突 / 缺 lineage / 具名裁决缺失 / 未结 PO 退出 / 域内自行解除付款冻结 六类探针各验证一次转红，还原后复绿，记录命令与结果 <!-- comet-task:6eaa57cf-a90d-45bb-9177-496c9cccac48 -->
- [x] 5.3 文档族门禁保持绿态：`bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh` 主门禁 10/10、自检 146/146 不退化（本 change 不改文档族真源） <!-- comet-task:e4cc7283-e28a-44e8-811e-0cd9197d8a03 -->

## Workflow follow-up（归档/交付，非 tracked 任务）

- Verify 阶段：真实构建门禁 + 独立只读最终集成审查，报告落 `docs/superpowers/reports/`
- Archive 阶段：确认归档；交付方式（local / push / pr）由用户明确授权后执行，不自行建分支、不 push、不 force
- 红线外事项（DDL 落库 R-2 / CI / 生产库 / 密钥 / 发布 / 法务结论）登记为 owner 前置，不在本 change 交付
