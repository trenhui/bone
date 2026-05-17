# TPA Java API 清单（As-Is 基线）

> **用途**：阶段 0 契约对齐；Go 按域迁移时在此表更新 `go-status` 列。  
> **来源**：`tpa-saas` 下 `*Controller.java` 的 `@RequestMapping`（2026-05 扫描）。  
> **真源**：运行态以 Java Swagger / 网关路由为准；本表为迁移跟踪索引。

## 图例

| go-status | 含义 |
|-----------|------|
| `java` | 仅 Java 实现 |
| `proxy` | Go 反向代理至 Java |
| `go` | Go 原生实现 |
| `dual` | 双写/灰度期 |

## bone-auth（`bone-auth/`）

| 前缀 | Controller | go-status |
|------|------------|-----------|
| `/system/auth` | AuthController | java |
| `/system/user` | UserController | java |
| `/system/role` | RoleController | java |
| `/system/menu` | MenuController | java |
| `/system/dept` | DeptController | java |
| `/system/post` | PostController | java |
| `/system/permission` | PermissionController | java |

## bone-tpa 核心（`bone-tpa-saas/bone-tpa/`）

| 前缀 | Controller | go-status |
|------|------------|-----------|
| `/tpa/claim` | ClaimController | java |
| `/tpa/claim/flow` | ClaimFlowController | java |
| `/tpa/claim/config` | ClaimConfigController | java |
| `/tpa/query` | GenericQueryController | java |
| `/tpa` | BasicConfigController | java |
| `/tpa/sign` | SignRecordController | java |
| `/tpa/file` | FileUploadController | java |
| `/tpa/image` | ClaimImageController | java |
| `/tpa/adjust` | AdjustmentController | java |
| `/tpa/adjust/plan` | PlanController | java |
| `/tpa/adjust/liability` | LiabilityController | java |
| `/tpa/adjust/liability/sort` | LiabilitySortController | java |
| `/tpa/adjust/liability/share` | LiabilityShareController | java |
| `/tpa/liabilityMapping` | LiabilityMappingController | java |
| `/tpa/personalQuota` | PersonalQuotaController | java |
| `/tpa/certificateConfig` | CertificateConfigController | java |
| `/api/v1/claim` | CaseSignController | java |
| `/api/v1/claim/submission` | ClaimSubmissionController | java |
| `/api/v1/claim-documents` | ClaimDocumentController | java |
| `/v1/tpa/support` | TpaSupportController | java |
| `/invoice` | ClaimInvoiceController | java |
| `/claim/push` | ClaimPushController | java |

## bone-lowcode（`bone-lowcode/`）

| 前缀 | Controller | go-status |
|------|------------|-----------|
| `/page` | PageController | java |
| `/field` | FieldController | java |
| `/table` | TableController | java |
| `/model` | ModelController | java |
| `/data` | GroupDataController | java |
| `/upload` | UploadController | java |
| `/uploadcommon` | UploadCommonController | java |
| `/optionSet` | OptionSetController | java |
| `/bizIdentity` | BizIdentityController | java |
| `/event` | EventController | java |
| `/processPage` | ProcessPageController | java |
| `/provide` | ProvideController | java |
| `/tableRule` | TableRuleController | java |
| `/fieldTableRule` | FieldTableRuleController | java |
| `/fieldLinkageRule` | FieldLinkageRuleController | java |
| `/scriptRule` | ScriptRuleController | java |
| `/submitRule` | SubmitRuleController | java |

## Go 服务（tpa-go）

| 方法 | 路径 | go-status |
|------|------|-----------|
| GET | `/health` | go |
| GET | `/ready` | go |
| GET | `/api/v1/tpa/migration/status` | go |

---

维护：每完成一个域的 Go 迁移，更新对应行的 `go-status` 并在 PR 中链接契约 diff。
