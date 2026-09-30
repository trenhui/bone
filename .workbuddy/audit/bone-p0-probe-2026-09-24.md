# Bone P0 门禁探底与收口裁定（2026-09-24）

> 配套：`.workbuddy/audit/bone-audit-2026-09-23.md`（原审计报告，P0-1/2/3 为头号发现「门禁假绿」）。
> 本报告记录：在 **6 个尚未接 P0 门禁的模块** 临时接入 3 条 P0 规则、跑 `mvn test -Dtest=ArchitectureTest` 抓出的真实违规，以及建议裁定。

## 一、探底方法

- 在 6 个目标模块的 `ArchitectureTest` 临时接入：
  - `BoneDddArchRules.oneAggregatePerTransaction()`（P0-1，一事务一聚合 / R9）
  - `BoneDddArchRules.applicationServicesMustNotOwnDomainRules()`（P0-2，反贫血 / E-6.4·R2）
  - `BoneDddArchRules.applicationSaveMustPairWithPublishOrExempt()`（P0-3，save↔publish 配对 / E-5.4）
- 跑完即 `git checkout --` 还原，**未改任何源码**（仓库仅保留 iam [P0-3]、gateway [P0-4] 两处预期内改动）。
- 作用域安全：`@AnalyzeClasses(packages = "com.bone.<module>")` 把 ArchUnit 导入范围锁定本模块，不会误算依赖模块的类。

## 二、探底结果汇总

| 模块 | P0-1（一事务一聚合） | P0-2（反贫血） | P0-3（save↔publish） | 测试 | 结论 |
|---|---|---|---|---|---|
| bone-system | 0 | 0 | 0 | 21 全过 | 已合规，仅接门禁即可消除假绿 |
| bone-metadata-server | 0 | 0 | 0 | 27 全过 | 已合规，仅接门禁即可消除假绿 |
| bone-notification | 0 | 0 | **1** | 19 / 1 失败 | 纯 P0-3 |
| bone-engine/studio-generator | 0 | 0 | **5** | 23 / 1 失败 | 纯 P0-3 |
| bone-integration | 0 | 0 | **5** | 25 / 1 失败 | 纯 P0-3，但模块已有完整 Outbox |
| bone-engine/…/bone-extension-studio | **3** | **72** | **4** | 22 / 3 失败 | 结构性贫血 + 多聚合同事务，最重 |

> 已接 P0 的基线（对照）：bone-blueprint（全接）、bone-iam（P0-3 已本轮回填）、bone-masterdata（P0-3 已接）。
> **后续全量复核（2026-09-24 续）**：全仓 20 个 ArchitectureTest 中，其余 10 个（bone-file、bone-core、bone-datasource、bone-security、bone-web、bone-metadata-sdk、bone-metadata-engine-{domain,ports,runtime,starter}）经核实**无 `ApplicationService`/`AggregateRoot`/`addDomainEvent`**，P0 规则对其无可门禁对象——属正确排除（基础设施/引擎库 + 元数据驱动引擎范式），**非「门禁假绿」缺口**。故 P0-1/2/3 统一已在所有有效业务模块收口。

## 三、违规明细

### 3.1 notification（1 × P0-3）
- `com.bone.platform.alert.application.NotificationApplicationService` — 有 Repository 写入但无 `publishFrom()`

### 3.2 studio-generator（5 × P0-3）
- `com.bone.studio.generator.application.CreateCodeGenerationApplicationService`
- `com.bone.studio.generator.application.CreateCodeTemplateApplicationService`
- `com.bone.studio.generator.application.GenerateCodeApplicationService`
- `com.bone.studio.generator.application.PublishCodeTemplateApplicationService`
- `com.bone.studio.generator.application.UpdateCodeTemplateApplicationService`

### 3.3 integration（5 × P0-3）
- `com.bone.integration.application.DeactivateFlowCommandApplicationService`
- `com.bone.integration.application.DisableConnectorApplicationService`
- `com.bone.integration.application.EnableConnectorApplicationService`
- `com.bone.integration.application.UpdateConnectorApplicationService`
- `com.bone.integration.application.UpdateFlowApplicationService`
- 注：integration 是**唯一有完整 Outbox** 的模块；集成事件可能由 Outbox 在基础设施层发布，而非经聚合 `publishFrom()`。需裁定这 5 处是「聚合不发布领域事件（Outbox 另管集成事件）」还是「应真接通领域事件」。

### 3.4 extension-studio（3 × P0-1 + 72 × P0-2 + 4 × P0-3）

**P0-1（一事务一聚合，3 处，均来自 `ExtensionCommandApplicationService`）：**
- `deleteExtension()` — 同事务持久化 `[ExtensionRepository, PluginVersionRepository]`
- `rollbackExtension()` — 同事务持久化 `[ExtensionRepository, PluginVersionRepository]`
- `uploadPluginArtifact()` — 同事务持久化 `[ExtensionRepository, PluginVersionRepository]`
- 判定：Extension 与 PluginVersion 若为「根+子实体」应合并为单聚合单仓储；若确为两聚合，则需经领域事件/Outbox/编排器拆分。需设计裁定。

**P0-2（反贫血，72 处，集中在少数服务直接用 setter 改领域对象 + 直接 new 领域对象）：**
- `ExtPointCommandApplicationService`：`ExtPoint.setCategory/setDescription/setDomain/setEnabled/setInterfaceName/setName/setVersion`（9 处）
- `ExtensionCommandApplicationService`：`Extension.setBizCode/setClassName/setConfig/setDescription/setEnabled/setExtPointId/setName/setPriority/setScenario/setTenantCode/setUseCase/setUserGroup/setVersion` + `PluginVersion.setActive/setChangeLog/setChecksum/setDeploymentStatus/setFilePath/setFileSize/setPluginId/setVersion` + `new PluginVersion`（约 30 处）
- `ExtensionStudioApplicationService`：`Extension.setExtPointId`（1 处）
- `MarketplaceInstallApplicationService`：`Extension.setConfig`（1 处）
- `PluginExecutionLogCommandApplicationService`：`PluginExecutionLog.setDurationMs/setErrorMessage/setExecutionId/setExtensionPointId/setInputData/setOutputData/setPluginId/setStatus` + `new PluginExecutionLog`（约 10 处）
- `StudioPatchSupport`：`ExtPoint.set*` / `Extension.set*`（约 21 处）
- 本质：application 层未通过聚合行为方法改状态（贫血），且直接实例化领域对象（应走工厂/仓储）。

**P0-3（save↔publish，4 处）：**
- `com.bone.engine.extension.studio.application.ExtPointCommandApplicationService`
- `com.bone.engine.extension.studio.application.ExtensionCommandApplicationService`
- `com.bone.engine.extension.studio.application.PluginExecutionLogCommandApplicationService`
- `com.bone.engine.extension.studio.application.support.StudioAuditSupport`

## 四、建议裁定（待用户确认）

| 模块 | 建议收口方式 | 风险 |
|---|---|---|
| system / metadata-server | **仅接门禁**（零违规，纯消除假绿） | 零 |
| notification / studio-generator | **声明不发 + 接门禁**：5+1 处 ApplicationService 加 `@NoDomainEvent` + E-5.4 豁免 JavaDoc，接 P0-3（同 iam 打法，可逆） | 低 |
| integration | **待裁定**：Outbox 已存在→大概率是「聚合不发布领域事件，集成事件由 Outbox 管」→ 加 `@NoDomainEvent` 注明；或真接通领域事件 | 中（需确认事件意图） |
| extension-studio | **待裁定**：P0-2 为结构性贫血，非 `@NoDomainEvent` 可解。可选 ① 先接 P0-3 + freeze P0-1/2 承债；② 全量重构到领域行为（L2 大重构）；③ 暂不接该模块门禁 | 高（需设计决策） |

> 注意：P0-1/2/3 是每模块自家 `ArchitectureTest` 的 opt-in，接门禁**不改共享规则库**，不属 L3；但 extension-studio 的 P0-2 真修属 L2 业务重构。

## 五、下一步
用户裁定后逐模块执行（同 iam 节奏：改完跑 `mvn -o -pl <m> spotless:check test` 验证 0 违规）。

## 六、执行结果（2026-09-24，已落地）

裁定（用户选全部推荐项）：system / metadata-server 零违规直接接门禁；notification / studio-generator / integration 的 P0-3 服务加 `@NoDomainEvent` + 接门禁（integration 注明 Outbox 另管集成事件）；extension-studio 接 P0-3 + `FreezingArchRule` 冻结 P0-1/2 承债。

**代码改动**
- 6 个 `ArchitectureTest` 接入 `oneAggregatePerTransaction` / `applicationServicesMustNotOwnDomainRules` / `applicationSaveMustPairWithPublishOrExempt`（ext-studio 前两条用 `FreezingArchRule.freeze`）。
- 14 个 ApplicationService 加 `@NoDomainEvent` + E-5.4 豁免 JavaDoc：notification 1、studio-generator 5、integration 5、ext-studio 4。

**验证（全部 `mvn -o -pl <m> spotless:check test -Dtest=ArchitectureTest`）**
| 模块 | 测试数 | 结果 |
|---|---|---|
| bone-system | 21 | 全绿 |
| bone-metadata-server | 27 | 全绿 |
| bone-notification | 19 | 全绿 |
| bone-engine/studio-generator | 23 | 全绿 |
| bone-integration | 25 | 全绿 |
| bone-extension-studio | 22 | 全绿（冻结基线已生成） |

**仍待办**
- 未 commit（用户未要求）。
- ext-studio `archunit_store/` 新基线须随改动提交入库（README 要求；CI 不开 `allowStoreCreation`）。

## 七、全量复核结论（2026-09-24 续）

全仓 20 个 `ArchitectureTest` 的 P0（P0-1/2/3）接线状态：

| 分类 | 模块 | P0 接线 |
|---|---|---|
| 业务/DDD 模块（有效范围） | blueprint、masterdata、iam、gateway(P0-4)、system、metadata-server、notification、integration、studio-generator、extension-studio | ✅ 已接 |
| 基础设施/框架库 | bone-core、bone-datasource、bone-security、bone-web、bone-metadata-sdk | N/A（规则提供方，无 DDD 聚合/服务） |
| 纯基础设施组件 | bone-file | N/A（自身 ArchitectureTest 注明 DDD 规则空过） |
| 元数据驱动引擎 | bone-metadata-engine-{domain,ports,runtime,starter} | N/A（SmartEntity/Metadata 范式，有独立领域层零依赖门禁） |

**结论：原审计头号发现「门禁假绿」在有效业务范围内已消除**——所有具备 Bone DDD 应用/领域层的模块均已接 P0 门禁；剩余 10 个模块是框架/引擎，P0 规则无可适用对象，不属于缺口。
