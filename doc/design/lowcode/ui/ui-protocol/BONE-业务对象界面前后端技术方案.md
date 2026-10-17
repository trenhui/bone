# BONE 业务对象界面 · 前后端技术方案（v1.1 · 详细设计）

> 协议：`bone-ui-schema.schema.json` v1.1（详见《BONE-业务对象界面JSON协议设计》§3/§10/§11）
> 样板：`../../cases/ui-schema-samples/expense-claim-ui-schema.example.json`（报销首个落地）+ Demo `../../cases/ui-schema-samples/mock-expense-dynamic-v1.html`（引擎已跑测 19 项全绿，两个修补点见协议 §4 注记）
> 基线代码（2026-10-13 · dev）：`bone-metadata-server`（catalog/runtime）、`bone-metadata-sdk`（`@Table/@Column/@Version`）、`bone-metadata-app`（`metadataApi/columnProjection/overlaySync/InterfaceTab/EntityDetail/RuntimeDataManagement`）

---

## 0. 结论先行（给评审的 9 条）

1. **存储 A 方案**：新表 `meta_entity_ui_schema`（租户团队级），不复用 `meta_ui_overlay`（用户个人偏好级），不给 `meta_entity` 加列（行膨胀 + 版本耦合）。
2. **后端只做存、验、快照**：CRUD + 引用完整性校验 + 发布事务内快照；linkage/submit 只在前端跑（后端已有 `RuntimeWriteValidator` + Hook + 行策略做安全底线，不后移）。
3. **分层严守**：新聚合 `UiSchemaDef`（domain）+ `UiSchemaApplicationService`（application）+ 扩展 `MetaEntityCatalogController`（adapter）；只用 SDK 仓储；Controller 返 `ApiResponse`；并发用 `@Version` 乐观锁，禁 `FOR UPDATE`。
4. **发布即快照**：`publishEntity` 同事务内 draft→published 快照；`publish-preview` 加界面 diff 区块；`validate` 加 UI 引用校验（删被引用字段阻断发布）。
5. **模板资产补齐**：`meta_model_template` 加 `ui_schema_json + ui_schema_version`（L3 DDL）；`instantiate` 同步拷贝 + 写 `base` 指针（复用 0040 动作展开口）。
6. **前端三件套**：`uiSchemaEngine.ts`（Demo 引擎原样移植 + vitest）+ `BoneDynamicForm/List`（复用 `RuntimeDataManagement` 三控件，不重写）+ `UiSchemaTab`（建模编辑器）；无 schema 的老实体行为零变化。
7. **i18n 复用现成基建**：`shared-utils/i18n` + `*Key` 解析链（miss 回落内联中文，禁裸奔 key）。
8. **DDL 与模板表变更走 L3**；`mvn spotless:apply` + `./scripts/check.sh` 为合入门槛。
9. **并发语义与 runtime 统一**：PUT 全量替换用 HTTP `If-Match: <lockVersion>`（与 `invokeAction` 的 ETag/412 语义一致，`expectedLock` 不进 body）；服务端结构校验以本目录 `bone-ui-schema.schema.json` 为 classpath 单源（P0 手写白名单断言 + 测试反向对账，见 §2.5，不引入新依赖）。

---

## 1. 现状锚点（方案挂载位置）

### 1.1 后端

| 现有资产 | 位置 | 本方案复用点 |
|---|---|---|
| 建模 CRUD + 发布事务 | `catalog/application/MetaEntityApplicationService`（`publishEntity(id, expectedVersion)`：`CatalogVersionSupport.assertExpected` → `publish()` → 缓存失效 → 预留列分配 → `align` → 二次失效 → 发领域事件） | 发布快照挂载点（§4）；`validate` 扩展挂载点 |
| 模板实例化 | `catalog/application/MetaTemplateApplicationService.instantiate`（字段复制 + `ActionDefMaterializePort.materialize` 动作展开，迁移 0040） | `uiSchema` 拷贝 + `base` 指针写入挂载点（§5） |
| 列覆盖服务端化 | `catalog/application/UiOverlayApplicationService` + `domain/gateway/UiOverlayStore` + `meta_ui_overlay` 表（迁移 0042，唯一键 `(tenant,user,entity)`） | 模式复刻（port + service + 表），但键改为团队级（§2） |
| 动作运行时 | `runtime/adapter/web/RuntimeActionController`（list/invoke，`Idempotency-Key` + `If-Match`）+ `RuntimeActionApplicationService.validateParams` + `RuntimeActionDefRepository` | 渲染器只调现成通道，**运行时零改动**（§6.3） |
| 写校验 + 行策略 | `RuntimeWriteValidator`、`ExpenseClaimWriteHook`、`row_policies`、`RuntimeAccessGuard` | 安全底线，UiSchema 不碰（§6.3） |
| SDK | `@Table/@Column/@Version`、`BaseRepository`，雪花 Long→String | 唯一持久化（HC-001/HC-006） |
| 迁移 | `scripts/migration/00xx_*.sql`（最新 0045） | 新表 0046 + 模板表变更同批（L3 审批） |

### 1.2 前端

| 现有资产 | 位置 | 本方案复用点 |
|---|---|---|
| API 层 | `services/metadataApi.ts`（`metadataEntityApi/metadataFieldApi/listActions/lookupOptions/listChildren/replaceChildren/metadataUiOverlayApi`，`ApiResponse` 包裹） | 加 `metadataUiSchemaApi`（§6.1） |
| 列投影纯函数 | `utils/columnProjection.ts`（`projectColumns/defaultOverlay` + 单测）+ `utils/overlaySync.ts`（服务端优先三级 hydrate + 单测） | 回落链复用思想：schema → overlay → 默认（§6.2） |
| 三富控件 | `pages/RuntimeDataManagement.tsx`（明细弹窗 + REFERENCE 下拉 + 附件清单，v0.46 已落地） | `BoneDynamicForm` 直接复用其编辑器模式，不另写三套（§6.4） |
| 建模页 | `pages/EntityDetail.tsx`（结构/界面/数据/发布四 Tab + URL 同步）、`components/InterfaceTab.tsx`（个人列偏好）、`FieldWizardModal` | 加「协议」Tab（§6.5）；`InterfaceTab` 保留（个人级 vs 团队级各归其位） |
| i18n | `packages/shared-utils/src/i18n`（`zh-CN/en-US` 静态包、`fallbackLng zh-CN`、Shell 广播切换） | `*Key` 解析链（§6.6） |
| 原型证据 | `mock-expense-dynamic-v1.html`（引擎跑测 19 绿） | `uiSchemaEngine.ts` 移植源（§6.2） |

---

## 2. 后端存储设计

### 2.1 决策：新表，不复用 overlay，不扩 meta_entity

| 候选 | 结论 | 理由 |
|---|---|---|
| A. 新表 `meta_entity_ui_schema` | ✅ 采用 | 生命周期独立（草稿/发布快照/base 指针/i18n 命名空间）；键 `(tenant, entity)` 团队级；MEDIUMTEXT 不污染 `meta_entity` 行 |
| B. 复用 `meta_ui_overlay` 加行 | ❌ | 该表键含 `user_id`（个人偏好：显隐/列序），团队 schema 与之语义冲突；混存会导致"删个人偏好误删团队 schema" |
| C. `meta_entity` 加 `ui_schema_json` 列 | ❌ | 行膨胀（MEDIUMTEXT 常驻主查询）；发布快照要再加两列；模板指针不属于实体聚合；`update()` 全字段改写路径会被动触碰大 JSON |

### 2.2 DDL（迁移 0046，L3 审批）

```sql
-- 0046：业务对象界面 JSON 协议服务端化（团队级 schema，与用户级 overlay 分离）
-- expand 阶段：只建新表 + 模板表加列；零存量迁移（无 schema 的实体走回落渲染，行为不变）。
CREATE TABLE IF NOT EXISTS meta_entity_ui_schema (
  id                    BIGINT       NOT NULL COMMENT '雪花 ID（SDK 仓储 Long→String）',
  tenant_id             BIGINT       NOT NULL COMMENT '租户（与 meta_entity 同链路）',
  entity_id             BIGINT       NOT NULL COMMENT 'meta_entity.id（逻辑外键，不建物理 FK，防删实体级联惊动）',
  schema_json           MEDIUMTEXT   NOT NULL COMMENT 'BoneUiSchema 草稿（schemaVersion=1）',
  schema_version        INT          NOT NULL DEFAULT 1 COMMENT '协议大版本（const 1）',
  schema_minor          INT          NOT NULL DEFAULT 1 COMMENT '协议增量版本（版本协商）',
  published_schema_json MEDIUMTEXT   NULL COMMENT '随实体发布打的快照（§4）',
  published_version     INT          NULL COMMENT '快照时的实体 version（对账用）',
  base_template_code    VARCHAR(128) NULL COMMENT '实例化来源模板 code（base 指针）',
  base_version          VARCHAR(32)  NULL COMMENT '实例化时的模板版本',
  follow_mode           VARCHAR(16)  NOT NULL DEFAULT 'pinned' COMMENT 'pinned/notify（永不自动推送）',
  lock_version          BIGINT       NOT NULL DEFAULT 0 COMMENT 'SDK @Version 乐观锁（禁 FOR UPDATE，ADR-0031）',
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted               TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_uischema_tenant_entity (tenant_id, entity_id),
  KEY idx_uischema_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务对象界面 schema（团队级）';

-- 模板资产补齐（与 0040 动作展开对称：模板缺 uiSchema 则实例化产出"有形无面"）
ALTER TABLE meta_model_template
  ADD COLUMN ui_schema_json MEDIUMTEXT NULL COMMENT '模板 baseUiSchema 快照',
  ADD COLUMN ui_schema_version VARCHAR(32) NULL COMMENT '模板界面版本（base_version 来源）';

-- 模板界面版本快照（§5.3 三路归并的 base.old 来源：随模板发版同事务追加写，只增不改）
CREATE TABLE IF NOT EXISTS meta_model_template_ui_history (
  id            BIGINT       NOT NULL COMMENT '雪花 ID',
  tenant_id     BIGINT       NOT NULL COMMENT '租户',
  template_code VARCHAR(128) NOT NULL COMMENT '模板 code',
  version       VARCHAR(32)  NOT NULL COMMENT '模板界面版本',
  schema_json   MEDIUMTEXT   NOT NULL COMMENT '该版本的 baseUiSchema 全量',
  published_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tmpl_ui_hist (tenant_id, template_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板界面版本快照（归并基线）';
```

注意：`entity_id` 不建物理 FK——删实体由应用层同步删 schema 行（`deleteEntity` 流程内），删表级联会把"删实体但留审计"的未来选项堵死。

### 2.3 领域层（`catalog/domain/model/meta` 或新建 `catalog/domain/model/ui` 包）

```java
// UiSchemaDef：独立聚合根（与 MetaEntity 不同的写生命周期：草稿可随时改，发布才冻结）
@Table("meta_entity_ui_schema")
public class UiSchemaDef extends AbstractEntity<Long> {   // 租户列 explicit（如 MetaEntity 做法）
  @Column(name = "tenant_id", nullable = false) private Long tenantId;
  @Column(name = "entity_id", nullable = false) private Long entityId;
  @Column(name = "schema_json", nullable = false) private String schemaJson;
  @Column(name = "schema_version", nullable = false) private Integer schemaVersion; // const 1 断言
  @Column(name = "schema_minor", nullable = false) private Integer schemaMinor;
  @Column(name = "published_schema_json") private String publishedSchemaJson;
  @Column(name = "published_version") private Integer publishedVersion;
  @Column(name = "base_template_code") private String baseTemplateCode;
  @Column(name = "base_version") private String baseVersion;
  @Column(name = "follow_mode", nullable = false) private String followMode; // pinned/notify
  @Version @Column(name = "lock_version", nullable = false) private Long lockVersion;

  public static UiSchemaDef create(...) {
    assertSchemaVersion(json);   // schemaVersion==1，否则 DomainException（未知大版本显式拒绝，不猜渲染）
    ...
  }
  public void replaceDraft(String json, long lockVersion) { ... }  // 幂等全量替换 + minor 校验
  public void snapshotOnPublish(int entityVersion) { this.publishedSchemaJson = this.schemaJson; ... }
  // 纯领域校验（不碰仓储）：JSON 可解析、深度 all/any/not ≤3、virtual 标记存在性、computedFn 白名单
  // 引用存在性（fieldCode/actionCode/policyCode）走应用层端口（§3.2），domain 不直连 repository 以外的读
}
```

`UiSchemaDefRepository`：SDK 仓储接口（仿 `MetaEntityRepository` 同包风格），方法：`findByTenantAndEntity(tenantId, entityId)`、`insert`、`update`、`deleteByEntity`。

### 2.4 草稿可编辑 vs 已发布的规则

- 草稿实体：schema 随时 PUT（`@Version` 挡并发覆盖）。
- 已发布实体：**允许改草稿**（下次发布才生效，与字段"新增字段下次发布生效"同哲学，脏态徽标复用 `publishState` 机制：`isDirtySincePublish` 对 schema 行同样生效，前端 `UiSchemaTab` 显示"有更新待重新发布"）。
- 删实体：应用层同步删 schema 行（`deleteEntity` 内，非 FK 级联）。

### 2.5 服务端校验单源（契约即代码：抄 RJSF/Amis"同一份 Schema 双端用"思想，Bone 版是"同一份文件双端读"）

- `bone-ui-schema.schema.json` 复制一份进 `bone-metadata-server/src/main/resources/ui/bone-ui-schema.schema.json`（classpath 只读）；同步检查脚本 `scripts/check-ui-schema-sync.py` 覆盖**三方契约**：① doc ↔ classpath SHA-256 一致；② 前端 lint 白名单 `PROP_WHITELIST`（`uiSchema/constants.ts`）↔ schema `definitions.control.properties.props` 逐项一致（26 项）。**接入 `check.sh` 需 L3 审批**，wiring 前走架构师评审——防"文档改了，服务端或前端没跟"。
- P0 **不引入** `networknt json-schema-validator` 等新依赖（新增依赖走 L3，本期不排）：服务端用 Jackson `JsonNode` 手写三表白名单断言（`controlKind` 18 种 / condition `op` 13 个 / `function` 9 个），白名单常量与 schema `enum` 的一致性由**后端单测反向断言**（`UiSchemaContractTest` 读 classpath schema 抽 enum，与常量逐项比对——schema 加类型测试必红，逼出"先改契约再改代码"的顺序）。
- P1 再评估引入 validator 做全结构校验（走 L3 依赖审批）；联调真源顺序：classpath schema > doc schema > 样板。

### 2.6 审计与可观测（搭现有车，不新建表）

- 审计：复用 `AbstractEntity` 的 `createdBy/updatedBy/updatedAt`（谁、何时改了界面）；发布事件写 `operLog`（`publishEntity` 已有日志点，加 `uiSchemaVersion` 字段即可）。
- 日志对账：`validate/publish` 失败日志带 `entityId + UI-REF-xx/SUB-xx/LINK-xx code`（MDC），与前端规则 `code` 同 ID（ServiceNow policy 双跑对账思想）。
- 指标（P1）：`ui_schema_render_fail_total{entity}`（前端上报）、`ui_schema_json_bytes` 直方图（256KB 上限水位预警）；P0 只保证 code 可 grep。

---

## 3. 后端应用层与适配层

### 3.1 `UiSchemaApplicationService`（新建，仿 `UiOverlayApplicationService` 结构）

```java
@Service
public class UiSchemaApplicationService {
  // 依赖：UiSchemaDefRepository + MetaEntityRepository + MetaFieldRepository
  //      + TenantProvider + CurrentUserProvider（写权限：developer+，复用既有 JwtRoleConverter 口径）
  //      + UiSchemaRefCheckPort（actionCode/policyCode 存在性；action def 读口径仿 ActionDefMaterializePort）

  @Transactional(readOnly = true)  String getDraftSchema(Long entityId);       // 无行 → null（前端回落）
  @Transactional                   void   saveDraftSchema(Long entityId, JsonNode schema, Long lockVersion); // 锁版本由 Controller 从 If-Match 头取
  @Transactional                   void   deleteDraftSchema(Long entityId);
  @Transactional(readOnly = true)  List<UiSchemaIssue> validateSchema(Long entityId);
  // validate 规则（code 即前后端对账码 §11.1-⑥）：
  //   UI-REF-01 fieldCode 必须 ∈ meta_field.code（含 CHILD_TABLE 声明字段）或 virtual:true
  //   UI-REF-02 actionCode 必须 ∈ 已发布 action def
  //   UI-REF-03 dataScope.policyCode 必须 ∈ row_policies 声明
  //   UI-REF-04 virtual 控件 fieldCode 禁与真实字段码碰撞
  //   UI-REF-05 条件深度 ≤3；UI-REF-06 computedFn/controlKind/verifyType 白名单
  //   UI-REF-07 tabs.value 的 $ 变量白名单（$me/$myteam/$all）
  //   UI-REF-08 tier 上下文变量命名空间白名单（tier/user/org/channel/device）；UI-REF-09 key 键存在性（linkage/submit/actionBar 变体）
  @Transactional(readOnly = true)  TemplateUpdateCheck checkBaseUpdate(Long entityId); // base_version vs 模板 currentVersion
  @Transactional                   UiSchemaMergePreview previewMerge(Long entityId); // 三路归并预览（§5.3）
  @Transactional                   void   confirmMerge(Long entityId, ...);          // 租户确认后合入
}
```

安全线：`schema_json` 存前做大小上限（如 256KB，MEDIUMTEXT 是上限不是目标）+ JSON 深度上限；`$`/spel/公式一律不支持——协议无表达式语言，服务端不 eval 任何字符串。

### 3.2 Controller（扩展 `MetaEntityCatalogController`，与 ui-overlay 同控制器内聚）

键用 `{id}`（与同控制器的 `/{id}/validate`、`/{id}/publish-preview` 一致；overlay 用 `{entityCode}` 是个人偏好键历史，团队 schema 跟实体主键走，删实体改码回收不影响寻址）。

| 方法 | 路径 | 请求 | 响应 | 错误 |
|---|---|---|---|---|
| GET | `/entities/{id}/ui-schema` | — | `ApiResponse<UiSchemaResp{json, minor, lockVersion, updatedAt} \| null>` | 404 实体不存在 |
| PUT | `/entities/{id}/ui-schema` | 头 `If-Match: <lockVersion>` + `UiSchemaReq{json(JsonNode)}` 全量替换幂等（**锁版本走标准头，不进 body**——与 runtime `invokeAction` 的 `If-Match`/ETag/412 同语义；`If-Match: *` 表"我不管并发"，建模页默认带当前 `lockVersion`） | `ApiResponse<UiSchemaResp>`（响应带新 `ETag: <lockVersion>`） | 400 UI-REF-xx；409 锁冲突；412 前置条件失败（缺 `If-Match` 且行已存在，防误覆盖） |
| DELETE | `/entities/{id}/ui-schema` | — | `ApiResponse<void>`（删草稿行；已发布快照保留） | — |
| POST | `/entities/{id}/ui-schema/validate` | — | `ApiResponse<List<EntityValidationIssue{level, code, message, fieldId?, fieldName?}>>` | — |
| GET | `/entities/{id}/ui-schema/base-update` | — | `ApiResponse<TemplateUpdateCheck{hasUpdate, baseVersion, currentVersion, digest}>`（digest 只含计数：sections/rules/actions 增减数，不返全量） | 无 base 指针 → `{hasUpdate:false}` |
| POST | `/entities/{id}/ui-schema/merge-preview` | — | `ApiResponse<MergePreview{autoMerged[], conflicts[]}>` | — |
| POST | `/entities/{id}/ui-schema/merge-confirm` | `{acceptConflicts: bool, templateVersion: string}`（templateVersion 必填 = preview 时的模板版本，不一致 → 409 作废重跑；锁版本仍走 `If-Match` 头，不进 body） | `ApiResponse<UiSchemaResp>` | 409 合入期间模板又发版（base_version 已变）或草稿并发修改 |

OpenAPI：新建 `doc/architecture/openapi/metadata-modeling-v1.yaml`（P3 契约；catalog 建模端点此前散在实现无 YAML 收敛，借本方案 7 个端点起头收敛，后续字段/动作建模端点渐进补入；运行时 `metadata-runtime-v1.yaml` **不动**——本方案运行时零新增端点）。

### 3.3 权限

- 读 schema：实体可见即可（随实体读权限）。
- 写/删/合入：`developer`+（与字段建模同权；`viewer` 禁写，复用既有角色拦截器口径，不新发明）。
- 行策略/`permissionCode`/幂等：全部后端原有强制，UiSchema 只做显隐，**前端显隐永不作为鉴权依据**（`RuntimeAccessGuard` 逐请求再断言）。
- 越权面：`entityId` 先过租户归属（`TenantProvider` + 仓储层 `tenantId` 条件，与现有 `MetaEntityRepository` 同模式），再查 schema 行——防跨租户枚举 `/{id}`。

### 3.4 读路径：工作台聚合加 `hasUiSchema`（防 N+1，抄 Salesforce"列表只返摘要"思想）

- `ModelingWorkspace` 聚合 DTO 加 `hasUiSchema: bool`（`UiSchemaApplicationService.schemaEntityIds` 一次 `IN` 查询批量带出，禁 N+1），UiSchemaTab 徽标与 RuntimeDataManagement"有定制界面"入口都读它；**不**在列表里展开 schema 全量。
- 缓存：设计态读（GET draft/published 快照）**不进** runtime 快照（`CatalogRuntimeEntityProvider` 快照只服务运行时读）；catalog 侧读缓存 P1 再议（写多读少，`@Version` 失效复杂度不值）。

---

## 4. 发布联动（挂载 `publishEntity` 事务）

```java
// MetaEntityApplicationService.publishEntity 内，align 成功后、发领域事件前：
uiSchemaSupport.snapshotOnPublish(entity);   // 同事务：draft→published 快照 + published_version=entity.version
```

- 失败语义：快照失败则发布整体回滚（同一 `@Transactional`），不出现"结构已发布、界面停在旧版"的半吊子。
- `publish-preview` 扩展：加 `ui` 区块 `{hasSchema, sections, linkageRules, submitRules, actions, blockingIssues}`（只读摘要；供 `PublishPreviewModal` 渲染"界面 diff"，与结构 statements 同屏）。
- `validate`（`GET /entities/{id}/validate`）扩展：`UI-REF-0x` 作为 `EntityValidationIssue`（level=ERROR）混入，code 前缀 `UI-` 与现有校验码正交；**删被 schema 引用的字段 → 发布阻断 + 定位到 UiSchemaTab**（P0 关洞"在途影响分析"首版形态）。
- 已发布实体的 schema 草稿修改 → 脏态（复用 `publishState.isDirtySincePublish(schemaUpdatedAt, lastPublish)`，`UiSchemaTab` 顶栏金 Tag + 顶栏「重新发布」直达）。
- **发布门禁升格（P0，协议 §13.6/§14 同源）**：`snapshotOnPublish` 前置 `ui-schema/validate` 硬门禁——服务端以 classpath `bone-ui-schema.schema.json` 单源结构校验（additionalProperties=false 硬拒）+ 引用完整性 + 变体组合冲烟（逐变体断言视图非空、pageHeader 悬空、变体全遮蔽字段），任一 ERROR 阻断发布并返 `blockingIssues`（`PublishPreviewModal` 同屏渲染三道门报告：真源机校/语义/组合冲烟，与 DEMO v1.9 发布预检同口径）。
- **412 冲突响应结构化（PUT `If-Match` 失败语义）**：`412 Precondition Failed` 响应体返回 `{serverLockVersion, serverSnapshot, diff[{path, base, incoming}]}`——把「整份重做」降为「按路径合并」；前端捕获 412 后弹冲突面板（服务端快照 vs 本地草稿结构化 diff，逐路径取舍），避免长 JSON 全量重填，降低团队级 schema 多人协作的丢失面。

### 4.1 回滚（迁移 0046 expand/contract 两阶段，禁删数据式回滚）

- 回滚 = 代码回退 + `DROP TABLE meta_entity_ui_schema/meta_model_template_ui_history` + 模板表 `DROP COLUMN ui_schema_json/ui_schema_version`（0046 只加表加列、无历史数据回填、无 `meta_entity` 存量变更——`publishedUiSchema` 与 `isDirtySincePublish` 扩展失败时，未发布草稿丢失可接受，已发布运行时读不受影响，因运行时读永远以 `meta_entity` 版本化结构为准、schema 只是渲染增强）。
- 模板表已有行 `ui_schema_json` 全 NULL 即兼容态，`instantiate` 遇 NULL 直接跳过拷贝（无需数据迁移脚本）。

---

## 5. 模板资产与跟随（挂载 `instantiate` + 新查口）

### 5.1 实例化拷贝（`MetaTemplateApplicationService.instantiate` 内，动作展开之后）

```java
// 模板 ui_schema_json 非空 → 建 UiSchemaDef 行：
//   schema_json = 模板快照原样拷贝（含 i18n namespace：实例化时重写为 bone.ui.<entityCode>，防多实例共用同一 namespace 导致词条串味）
//   base_template_code = template.getCode(), base_version = template.getCurrentVersion(), follow_mode = 'pinned'
```

namespace 重写规则必须在服务端做（前端不可信），且只做字符串替换 `bone.ui.*`，不解析语义。

### 5.2 前置诚实门（§11.4 落字）

模板资产完整性（`ui_schema_json` 非空 + 引用的 action/field 在模板内自洽）补齐前，跟随入口 `base-update` 返回 `{hasUpdate:false, reason:"TEMPLATE_ASSET_INCOMPLETE"}`，前端横幅 disabled + tooltip 明示，不静默开放。

### 5.3 三路归并（`previewMerge` 服务端算，前端只确认）

输入：`base.old`（= 实例 `base_version` 对应的模板快照，来源 `meta_model_template_ui_history(template_code, version, schema_json, published_at)`——**新版本快照表，随模板发版同事务写入**，纳入 0046 同批 L3 DDL；该表只追加不更新，天然审计）→ 输出 `{autoMerged[], conflicts[{path, baseNew, instance}]}`。
策略：租户未改过的 section/rule（与 `base.old` 逐段一致）自动取 `base.new`；改过的保留实例值 + WARNING。`confirmMerge` 写回草稿（`@Version` 再断言一次，防合入期间有人编辑）。
降级：历史表缺行（老模板无快照）→ 降级"两路：base.new vs instance"，冲突面全量标出，前端横幅明示"模板无历史快照，请逐项确认"。

---

## 6. 前端方案（`bone-metadata-app`）

### 6.1 API 层（`services/metadataApi.ts` 追加，与 overlay 同文件内聚）

```ts
export const metadataUiSchemaApi = {
  get: (entityId) => api.get(`${META}/entities/${id}/ui-schema`),                 // ApiResponse<UiSchemaResp|null>
  save: (entityId, json, lockVersion) => api.put(`${META}/entities/${id}/ui-schema`, { json }, { headers: { 'If-Match': lockVersion } }),
  remove: (entityId) => api.delete(`${META}/entities/${id}/ui-schema`),
  validate: (entityId) => api.post(`${META}/entities/${id}/ui-schema/validate`),
  baseUpdate: (entityId) => api.get(`${META}/entities/${id}/ui-schema/base-update`),
  mergePreview: (entityId) => api.post(`${META}/entities/${id}/ui-schema/merge-preview`),
  mergeConfirm: (entityId, accept) => api.post(`${META}/entities/${id}/ui-schema/merge-confirm`, accept),
};
```

运行时通道**零新增**：渲染数据继续用 `listActions/invokeAction/lookupOptions/listChildren/replaceChildren/records CRUD`（§1.1 表）。

### 6.2 引擎层（`utils/uiSchemaEngine.ts` + `uiSchemaEngine.test.ts`，Demo 移植）

从 `mock-expense-dynamic-v1.html` 原样移植已跑测逻辑：`pathVal`（数组穿透）、`cmp/cmpOrd`（数值/字符串双轨 + 金额容差）、`evalCond`（原子 + all/any/not + `$me/$myteam/$all` 上下文 + 深度≤3 fail-closed）、`linkageState`、`numVals/countVals/scalarOrSum`、`runSubmit`（九函数 + `when` 生效前置 + 必填短路 + 未知函数 fail-closed）。vitest 用例 = node 跑测 19 项一字不改搬运（repo 已有 `columnProjection.test.ts` 范式）+ 九函数/`when`/版本协商增量；双场景串联验证见 `utils/uiSchemaScenarios.test.ts`（读真实样板：报销 7 步 + 理赔 9 步）。

加载链（回落，行为兼容）：`ui-schema`（团队）→ 有则用；无 → `fetchOverlay`（个人偏好，`projectColumns`）；都无 → `defaultOverlay`。三级都有单测，截图 diff 为空即验收（协议 §9）。

### 6.3 渲染组件

- `components/BoneDynamicForm.tsx`：`sections → controls` 映射 antd（INPUT/TEXTAREA/NUMBER/SWITCH/DATE/ENUM）；富类型走现成模式：REFERENCE=`lookupOptions` 远程下拉、`CHILD_TABLE`=明细弹窗 + `replaceChildren` 整组替换（照抄 `RuntimeDataManagement` 交互，不重写第三套）、ATTACHMENT=清单（`maxCount/maxSizeMB` 前端先拦，真上传另立项）；展示型 v1 最小渲染：STAT=卡片、CHART=占位（`dataRef` 聚合口径接入前显式"聚合口径未接入"空态，禁静默空白）、QUOTE=引用块 + 锚点跳转、TIMELINE=时间线、COMPUTED=只读值、ALERT=行内警告、SLIDER=滑块。
- `components/BoneDynamicList.tsx`：columns/search/tabs（`$me/$myteam/$all` 上下文由 `bone-user` + localStorage 口径解析）/stats 卡片/`viewKind=table` 首版（calendar/kanban P1）；`dataScope` 不改变查询形状（后端 WHERE 强制，前端不拼）。
- `hooks/useBoneLinkage.ts` + `hooks/useBoneSubmit.ts`：值变更即时重算 + 提交前重放；循环联动单轮收敛 + warn 日志（协议 §4）。
- 数据页接入：`RuntimeDataManagement` 录入抽屉内，若实体有 schema 则用 `BoneDynamicForm`，否则走现有表单（特性开关 = schema 存在性，无需配置项）。

### 6.4 建模编辑器（`components/UiSchemaTab.tsx`，P0-2）

`EntityDetail` 新增「协议」Tab（与「界面」并存：前者团队级 schema，后者个人级偏好，Tab 副标题写清归属，防用户混淆）。内含（全部表格行编辑，无画布）：
分区表（id/title/顺序）→ 控件表（fieldCode 下拉只能选 `meta_field.code` + 富类型 props 白名单表单）→ linkage 表（when 条件编辑器支持 all/any/not 嵌套 ≤3 + then 四态）→ submit 表（function 白名单 + compare + message 必填）→ actionBar 表（actionCode 下拉只能选已发布 action + 门控 + confirm/maxRounds/双人条件）→ i18n 页（`*Key` 列表 + 缺失 WARNING）→ base 页（指针 + 检查更新横幅 + diff 预览 + 合入确认）。顶部常驻「校验」（调 validate，`UI-REF-xx` 行内定位）+ 脏态金 Tag。

### 6.5 状态机/行策略/模板在前端的唯一真实入口

- 状态机建模 Tab（P0-1 关洞）：`meta_entity_action_def` 的 Modeling 侧 CRUD（路径与运行时 `listActions` 只读对称），转移矩阵表格编辑（from→action→to + 权限码 + 幂等 + params_schema JSON），发布联动（随实体发布校验 action 完整性）。本方案先行、渲染器随后消费。
- 行策略建模入口（P1）：`rowPolicies` 从 JSON 文本框升级为策略表（owner_or_approver 为首个模板行），`UiSchemaTab` 的 dataScope 下拉只能选已声明 policyCode（引用不复制）。

### 6.6 i18n 接入

`t(namespace + ':' + key, { defaultValue: 内联中文 })`，namespace 段维护进 `shared-utils/i18n/locales/*.json`（随模板资产包版本走）；`UiSchemaTab` 对无 key 的文案标 WARNING（不阻断发布）。

### 6.7 场景变体裁决（协议 §12）

`useBoneUiSchema` 在取数后先 `resolveVariant(ctx)`（ctx：roles 取 JWT 应用角色、tier 取订阅档上下文、channel/device 取运行环境；缺 tier → WARNING 回落 base，禁猜）。命中变体按块整块替换后，再进 linkage/submit 管道（引擎无感）。`UiSchemaTab` 加变体切换器 + 重叠匹配提示（UI-REF-08）+ "从 base 复制"起草；审计日志记 `variantKey`；运行时缓存键不变（裁决在前端）。

### 6.8 前端工程化（抄 Retool/Amis 的运行时纪律，P0 落字防返工）

- 性能：`BoneDynamicForm` 按 section 级 `React.memo`（`performance-policy` 既有 `React.memo` 纪律延续到动态表单）；CHILD_TABLE 行数软上限 200（超限分页或折叠，防万行 DOM 卡死）；lookup 下拉防抖 300ms + 失焦取消（复用 `RuntimeDataManagement` 既有模式）；首屏骨架屏（schema 解析 < 200ms 内仍闪骨架，防布局跳动）。
- 错误边界：动态表单区包 `AppErrorBoundary`（已有），schema 解析失败三色旗（红=结构坏回落默认表单+上报；黄=未知 minor/缺 tier 上下文告警条；绿=正常）。
- 离线/弱网：published 快照可进 `localStorage` 缓存（key 含 `published_version`，版本对不上即废弃）；**草稿永不缓存**（防脏写回）。缓存策略与 `overlaySync` 同文件内聚但独立 key 前缀 `uischema:`。
- 安全渲染：`labelOverride/notice/message` 全是租户输入——一律按文本渲染，**禁 `dangerouslySetInnerHTML`**（React 默认转义 + antd 组件即满足；富文本需求 P1 再议，绝不在 P0 开口）。
- 可访问性：动态控件 `label htmlFor` 关联、`aria-required` 随 required 联动、`notice` 黄标用 `role="status"`；对照 WCAG 2.1 AA 做 Tab 页自查表（PRD 无障碍要求落到本 Tab）。
- 移动端：`devices: ["mobile"]` 变体 + antd 栅格 `xs=24` 全宽 + 触控目标 ≥44px；P0 只保证"能用不滚坏"，精细适配 P1。

---

## 7. 接口契约总表（后端 §3.2 + 前端 §6.1 对齐）

| # | 方法 | 路径（`META=/api/v1/metadata`） | 入参 | 出参 | 主要错误码 |
|---|---|---|---|---|---|
| 1 | GET | `/entities/{id}/ui-schema` | — | `ApiResponse<UiSchemaResp\|null>` | 404 实体不存在 |
| 2 | PUT | `/entities/{id}/ui-schema` | 头 `If-Match` + `{json}` | `ApiResponse<UiSchemaResp>`（新 ETag） | 400 UI-REF-01~09；409 锁冲突；412 缺前置条件 |
| 3 | DELETE | `/entities/{id}/ui-schema` | — | `ApiResponse<void>` | — |
| 4 | POST | `/entities/{id}/ui-schema/validate` | — | `ApiResponse<UiSchemaIssue[]>` | — |
| 5 | GET | `/entities/{id}/ui-schema/base-update` | — | `ApiResponse<TemplateUpdateCheck>` | 无指针/reason 返回 hasUpdate=false |
| 6 | POST | `/entities/{id}/ui-schema/merge-preview` | — | `ApiResponse<MergePreview>` | — |
| 7 | POST | `/entities/{id}/ui-schema/merge-confirm` | `{acceptConflicts}` | `ApiResponse<UiSchemaResp>` | 409 模板又发版/有人并发改草稿 |

既有扩展（非新增路径）：`publish-preview` 加 `ui` 摘要；`validate` 混入 `UI-` 码；`instantiate` 透传 schema（响应不变，加日志字段）。

---

## 8. 关键流程（时序文字版）

**建模保存**：UiSchemaTab 编辑 → POST validate（行内标错，`code` 即前后端对账码）→ PUT（头带 `If-Match: lockVersion`；409/412 则提示刷新后合入，禁静默覆盖）→ 脏态金 Tag。
**发布**：点发布 → publish-preview（含 ui 摘要 + UI-REF ERROR 则禁发布）→ publishEntity 同事务（结构对齐 + schema 快照 + 缓存双失效 + 领域事件）→ 脏态转绿。
**实例化**：模板实例化（字段 + 0040 动作展开 + schema 拷贝 + base 指针 + namespace 重写）→ 草稿态可改 → 发布冻结快照。
**跟随**：`follow_mode=notify` 且模板发新版 → 建模 Tab 横幅 → base-update（digest 计数）→ merge-preview（三路归并）→ merge-confirm（@Version 再断言）→ 脏态待发布。

### 8.1 故障模式与降级（SLO：渲染增强永不阻断业务）

| 故障 | 降级 | 不可接受 |
|---|---|---|
| GET ui-schema 500/超时 | 回落 overlay/默认表单 + 黄条"定制界面加载失败" + 上报 | 白屏/转圈卡死 |
| published 快照缺失但 draft 存在（发布事务中途失败已回滚，不应出现；出现即 bug） | 按无 schema 回落 + 红条 + 日志 ERROR（entityId 可 grep） | 用 draft 渲染运行时页 |
| PUT 409/412 | 弹"有人先改了"→ 一键刷新 + diff 高亮对方版本（读 GET 最新）→ 用户手动合 | 静默覆盖 |
| merge-confirm 409（模板又发版） | 重新跑 preview（三路基准已变，旧 preview 作废），前端废弃旧 diff | 按旧 preview 强行写入 |
| 模板历史表缺行 | 两路降级 + 横幅"无历史快照请逐项确认"（§5.3） | 假装三路（base.old 用空对象=全量冲突误报已是正确行为，明确写出即可） |

---

## 9. 测试与门禁

| 层 | 用例 | 载体 |
|---|---|---|
| 领域 | `UiSchemaDef` 版本断言/深度校验/白名单/快照语义 | 后端单测（仿 `RuntimeActionDescribeSupportTest` 范式） |
| 应用 | 引用完整性 9 条（UI-REF-01~09）、并发 409/412、删实体联动、发布快照同事务、实例化拷贝 + namespace 重写、三路归并冲突保留实例 | 后端单测 + 集成测 |
| 契约 | classpath schema 与服务端白名单常量反向断言（§2.5，加类型测试必红）+ `scripts/check-ui-schema-sync.py` 的三方契约断言（doc↔classpath SHA、前端 `PROP_WHITELIST`↔schema props） | 后端单测 + 门禁脚本 |
| 架构 | 新类分层（domain 零框架、application 不碰 infra、Controller 只注 Service） | ArchUnit（`BoneDddArchRules`，仿 `ArchitectureTest`） |
| 前端引擎 | Demo 19 项原样移植（vitest） | `uiSchemaEngine.test.ts` |
| 前端组件 | 回落三级（schema/overlay/默认）+ 无 schema 老实体截图 diff 为空 + 三色旗错误态（坏 schema 回落默认表单、黄条告警可关） | 组件单测 + 手工走查（沿用 preview-walk 截图法） |
| 门禁 | `mvn spotless:apply`、受影响测试全绿、`./scripts/check.sh`、L2 双人 Review、DDL L3 审批 | P5/P6/P7 |

**2026-10-14 双场景联调实证**（报销 + 智能理赔样板，经网关 8888）：

- 全链路 API E2E `scripts/e2e/two-scenario-full-chain-e2e.py` **58/58 通过**：建实体→发布→字段/动作/ui-schema/运行数据→`validate` 零 ERROR→发布门控（UI-REF-01 拒绝 + 过闸放行）→回滚态清理；实体级与 schema 级 ETag/乐观锁语义两侧自证一致。
- 浏览器探针 `scripts/e2e/ui-two-scenario-probe.mjs` **7/7 通过**（登录、双场景实体搜索、数据面记录与状态、无致命 console 错误）。
- 产品「界面设计器」自检两实体均**「自检通过」**：修复前端 `PROP_WHITELIST` 滞后协议 v1.2 导致的 9 项误报（补 `mask/maxSizeMB/lineage/…` 26 项），并为自检补 `virtual` 控件跳过字段存在性校验 + 与真实字段码碰撞 ERROR（对齐后端 UI-REF-04 口径）；`EntityDetail` 结构表 `onRow` 误传 `onSave` 的 React Unknown event handler 警告已修。

---

## 10. 分期与验收（工程侧视角，对齐协议 §8/§9）

> 协议层面的「落地顺序」与「可验证口径」见《BONE-业务对象界面JSON协议设计》§8/§9。本节只列**工程侧差异**：存储 DDL、端点、模板拷贝、前端引擎与编辑器，避免与协议重复。

1. **P0-1 渲染器**：`uiSchemaEngine + BoneDynamicForm/List` + 数据页开关（schema 存在性）；验收：报销 Demo 走查剧本 5 步在生产页可复现（专票/合计/大额/流转/幂等）。
2. **P0-2 建模**：状态机动作 CRUD Tab + `UiSchemaTab`（条件构建器替代 `validationRules` 文本框）；验收：业务方不靠迁移能建出 DRAFT→PAID 全流转。
3. **P0-3 服务端化**：0046 DDL + 7 端点 + 发布快照 + 模板拷贝 + 跟随三步；验收：换设备 schema 不丢；删被引用字段发布阻断定位。
4. **P1**：MONEY 类型（DDL L3）、行策略表、汇总显示、日历/看板 viewKind、CHART 聚合口径接入。

## 11. 风险与对策

| 风险 | 对策 |
|---|---|
| `schema_json` 无限膨胀 | 256KB 存前上限 + 发布校验大小；超限 ERROR 指引拆 section/减规则 |
| 并发覆盖（多人同改一实体 schema） | `@Version` 锁 + 409 提示合入；合入确认时再断言一次 |
| 模板表 DDL 影响平台资产 | expand-only（加可空列），回滚只需删列；L3 审批 |
| i18n key 碰撞/缺失 | namespace 强制 `bone.ui.<entityCode>`（服务端重写）；缺失 WARNING 不阻断，渲染回落中文 |
| 前端显隐被当成权限 | 白盒写进 `UiSchemaTab` 副标题 + 代码注释：显隐≠鉴权，`RuntimeAccessGuard` 逐请求断言 |
| CHART 无数据源静默空白 | 未接入聚合口径一律显式空态（PRD §5.5 禁静默），P1 补齐 |
| 租户输入 XSS（labelOverride/notice/message） | 禁 `dangerouslySetInnerHTML`（§6.8），文本渲染；富文本 P1 再议 |
| 跨租户枚举 `/{id}` | 先租户归属后查行（§3.3），`viewer` 写拦截复用既有口径 |

---

## 12. 非目标与待定事项（诚实清单：业界方案评审必问的两栏）

### 12.1 非目标（本方案明确不做，后续有独立提案再议）

- 公式引擎/表达式语言（`$`/spel/三元表达式一律不支持，服务端不 eval 字符串——协议已封口）。
- 可视化画布拖拽（建模=表格行编辑；画布是另一个量级的产品提案）。
- 跨实体联动（linkage/submit 只读本实体 + 本单行；跨单据取数走后端 Hook/行策略）。
- 服务端跑 linkage（后端只做引用校验，不逐规则求值；安全底线由既有 `RuntimeWriteValidator` 承担）。
- 用户级 schema（个人偏好仍是 `meta_ui_overlay`，团队 schema 不下沉到人）。

### 12.2 待定事项（需业务/架构拍板，默认按括号内 fallback 开工）

1. `tier` 上下文来源（订阅档服务是否就绪？fallback：缺 tier 即 WARNING 回落 base，见 §6.7）。
2. CHART 聚合口径（fallback：P0 显式空态，P1 接入）。
3. 新建 `metadata-modeling-v1.yaml` 还是并入现有 catalog YAML（fallback：新建，渐进收敛）。
4. P1 是否引入 `networknt json-schema-validator`（fallback：P0 手写白名单 + 反向断言，见 §2.5）。

---

## 13. P0 任务拆解（按 AGENTS.md L1/L2/L3 标注审批线）

| # | 任务 | 层 | 审批 |
|---|---|---|---|
| P0-1.1 | `uiSchemaEngine.ts` 移植 + vitest 19 项（Demo 原样搬运） | 前端 | L1 |
| P0-1.2 | `BoneDynamicForm/List` + `useBoneLinkage/useBoneSubmit` + 数据页开关 + 三色旗/骨架屏/三级回落 | 前端 | L1（逻辑 L2 Review） |
| P0-2.1 | 迁移 0046（`meta_entity_ui_schema` + 模板表两列 + `meta_model_template_ui_history`） | DB | L3 |
| P0-2.2 | `UiSchemaDef` 聚合 + ApplicationService（CRUD/validate 9 条/If-Match/`hasUiSchema` 聚合）+ Controller 7 端点 + OpenAPI YAML | 后端 | L2（DDL 见上） |
| P0-2.3 | 发布快照挂载 + preview/validate 扩展 + 脏态 + classpath schema 同步脚本与门禁断言 | 后端 | L2 |
| P0-2.4 | `UiSchemaTab` 七表编辑器 + 条件构建器（≤3 层）+ 变体切换器 + i18n 缺失 WARNING | 前端 | L2 Review |
| P0-3.1 | 模板实例化拷贝 + namespace 服务端重写 + base 指针 + 跟随三步（base-update/preview/confirm） | 后端 | L2 |
| P0-3.2 | 报销端到端走查（Demo 5 步在生产页复现 + 无 schema 老实体截图 diff 为空） | 联调 | L1 |

---

## 附：文件索引

- 协议：`./bone-ui-schema.schema.json` · 设计：《BONE-业务对象界面JSON协议设计》· 样板：`../../cases/ui-schema-samples/expense-claim-ui-schema.example.json` · Demo：`../../cases/ui-schema-samples/mock-expense-dynamic-v1.html`
- 后端锚点：`MetaEntityApplicationService.publishEntity` / `MetaTemplateApplicationService.instantiate` / `UiOverlayApplicationService` + `UiOverlayStore` / `RuntimeActionController` / `0042_ui_overlay_server_side.sql`
- 前端锚点：`services/metadataApi.ts` / `utils/columnProjection.ts+overlaySync.ts`（均有单测）/ `pages/RuntimeDataManagement.tsx` / `pages/EntityDetail.tsx` / `shared-utils/src/i18n`
