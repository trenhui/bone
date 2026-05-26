# 应用模块 ArchUnit / ACL 状态（2026-05-22）

> 扫描命令：`mvn test -Dtest=ArchitectureTest`（各应用模块）。  
> 规则真源：`bone-framework/bone-architecture-test/BoneDddArchRules`。

## 已清零 `application → infrastructure` 存量

| 模块 | 端口（domain/gateway 或 application/port） | 说明 |
|------|------------------------------------------|------|
| bone-iam | `AccessTokenIssuer`、`RefreshTokenIssuer`、`AccountAuthorityCache` | JWT / Redis 缓存 |
| bone-masterdata | `MetaEntityCatalogPort`、`MasterDataExcelImportPort` | catalog 读取、Excel 导入 |
| bone-integration | `CamelFlowExecutionPort`、`IntegrationExecutionRecorder` 等 | 原已用 application/port |

## ArchitectureTest 通过（本轮验证）

- bone-iam、bone-masterdata、bone-blueprint、bone-extension-studio

## extension-studio 仓储拆分（ADR-0013，2026-05-22）✅

写侧 `domain/repository/*` 仅保留白名单方法；读侧迁入 `domain/gateway/*ReadPort`（`ExtensionReadPort`、`ExtPointReadPort`、`PluginVersionReadPort`、`PluginExecutionLogReadPort`、`StudioAuditReadPort`）。`ArchitectureTest.repository_methods_whitelist` 已去掉 freeze，**37 测试通过**。

## bone-iam 聚合根基类（ADR-0011 阶段 1）

| 基类 | 实体 |
|------|------|
| `TenantAggregateRoot` | `Account`、`Role`、`AuditLog` |
| `AggregateRoot` | `Tenant`、`Permission` |

## ArchUnit 规则扩展（2026-05-23，DDD v4.2）

新增 6 条共享规则（#11–#16）：adapter 禁直注 `application/service` 与 `domain/repository`；Handler 命名 `*CommandHandler`/`*QueryHandler`；写/读 Handler 事务边界。`bone-blueprint` **不 freeze**（参考样板须 0 违规）；其它应用模块 freeze 存量后随迁移收缩。

## ArchUnit freeze 策略（2026-05-22 统一）

| 规则 | freeze |
|------|--------|
| `applicationMustNotDependOnInfrastructure` | **否**（全应用模块已 ACL 整改） |
| `domainRepositoriesShouldOnlyDeclareWhitelistedMethods` | **否**（空仓储 / ADR-0013 ReadPort） |
| `noBoneCoreUseCaseApiDependency` | **否** |
| `noUseCase*` / `noNewDomainStore` / `noBusinessException*` | **是**（防回潮） |

各模块 `ArchitectureTest` 使用 `importOptions = ImportOption.DoNotIncludeTests.class`（**仅扫描 src/main**，避免测试类误报 application→infrastructure）。

`archunit_store/` 已收缩；`application_no_infra` / 仓储白名单 **直接门禁**。

## 已知技术债

| 模块 | 项 | 建议 |
|------|-----|------|
| bone-iam | `LocalDateTime` 审计统一 | [ADR-0014](../adr/0014-iam-localdatetime-audit.md) 阶段 2 按需 |

## 仓储方法 `*And*` 禁令

ArchRule 已拦截 `findBy*And*`（如原 `findByPluginIdAndVersion`）。extension-studio 已改名为 `findByPluginVersion`。
