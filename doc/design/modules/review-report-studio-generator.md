# Studio Generator（studio-generator）设计方案复核报告

> 执行时间：2026-09-27 ｜ 轮换序号 #7 ｜ 对应设计稿：`doc/design/modules/8.Studio Generator 详细设计方案.md`、`doc/design/BONE-X-Studio-详细设计方案.md`
> 锚定版本：`doc/design/_global-contracts.yaml` generated_at=2026-09-26T23:40
> 代码快照：分支 `codex/nightly-bone-system-20260927`（基于 `8eafc978b`，工作树干净）
> 对标基线：锚定 `hc_semantics` + `doc/architecture/多租户数据隔离方案设计.md`（§3.6 / §5 R4、通则 T1）+ AIP-151（LRO）+ AIP-133/134

## 一、阻断级问题（必须先解决才能达标）

| # | 问题 | 违反项 | 修复建议 | 证据（文件:行） |
|---|------|--------|----------|------------------|
| G-1 | **租户上下文不闭环 + 隐式魔法默认租户**：全模块**零处读取** `TenantContext`，仅在 `WebTenantConfiguration` 被设置；写入/查询侧租户来自请求参数，缺失时回落硬编码默认值——`DataSource` 落 `0L`、`CatalogMetadataGatewayAdapter` 用 `1L` | 多租户方案 §5 R1/R4、通则 T1「禁止隐式 0，须经 `TenantProvider.currentTenantIdOrNull()` 明确意图」 | 移除 `0L`/`1L` 隐式默认；写入与查询统一经 `TenantProvider.currentTenantIdOrNull()` 显式取值，缺失即失败关闭（对齐 `bone-metadata-server` 的 fail-closed 行为） | `WebTenantConfiguration.java:32-42`（仅设置，缺头置 `null`）；全模块 `TenantContext.getTenantId` / `TenantProvider.` / `currentTenantIdOrNull` **0 命中**；`DataSource.java:58`（`ds.tenantId = tenantId == null ? 0L : tenantId`）；`CatalogMetadataGatewayAdapter.java:32-33`（`long tid = tenantId != null ? tenantId : 1L`） |

> **影响面待实测确认**：若 metadata-sdk 在查询时叠加注入 `tenant_id = :current`，则 `1L` 默认表现为**空结果**而非越权读取；若未叠加，则为**跨租户数据泄漏**。二者都是缺陷，仅严重度不同——修复方式相同（去魔法默认值）。
> 补充：`DataSource` 默认 `0L` 会把新建数据源写到平台租户 0，叠加实体已声明 `tenantId` ⇒ 该租户后续读不到自己建的数据源（与 extension X-1 同构）。

> 锚定原判 **HC-003 violated 经复核为误报**（见 §五自审），已纠正为 `implemented`，不计入阻断级。

## 二、建议级优化

| # | 当前设计/实现 | 业界对标 | 优化方案 | 证据 |
|---|--------------|----------|----------|------|
| G-2 | **信封碎片化**：8 个 Controller 中 7 个用 `com.bone.core.model.ApiResponse`，仅 `CapabilityController` 用模块自有 `com.bone.studio.generator.common.result.ApiResponse`；该本地类全模块仅此 1 处引用 | 统一响应（HC-003 精神）、单一信封类型 | 删除本地 `ApiResponse`，`CapabilityController` 改用 core 版本（等价重构，低风险） | `CapabilityController.java:5`（import 本地）；其余 7 个 Controller `:3` 均 import `com.bone.core.model.ApiResponse`；`common/result/ApiResponse.java:6` 定义处，引用仅 1 处 |
| G-3 | 错误码裸字符串：`GEN_GENERATION_FAILED` 硬编码在 Map 内；`GEN_TEMPLATE_NOT_FOUND` 被**拼进 message** 而非独立 errorCode 字段 | 错误码 i18n、可机读 errorCode | 建 `GeneratorErrorCodes` 常量类；`errorCode` 独立字段返回，不混入 message | `GenerationTaskOperationApplicationService.java:56`（`error.put("errorCode", "GEN_GENERATION_FAILED")`）；`GetCodeTemplateDetailQueryApplicationService.java:22`（`throw new BizException(404, "GEN_TEMPLATE_NOT_FOUND: " + qry.getId())`） |
| G-4 | 模板数量不足：doc8 #L68 承诺 **7 类**，实际仅 5 个 `.ftl` | 设计承诺 | 补齐缺失 2 类模板，或订正 doc8 承诺为 5 类 | `ls src/main/resources/templates/` → `applicationService` / `controller` / `entity` / `repository` / `response`（5 个）；doc8 #L68 |
| G-5 | **领域事件零实现**：doc8 以目录结构 + 代码示例承诺 `DataSourceCreatedEvent` / `GenerationTaskCreatedEvent` / `GenerationTaskCompletedEvent` 等，代码 0 命中 | DDD 领域事件 | 需裁定：若属路线图则标注 [Target]（对齐 doc5 口径）；若应落地则排期实现 | 代码 `addDomainEvent` / `implements DomainEvent` / `class *Event` **0 命中**；doc8:692（`new DataSourceCreatedEvent(ds)`）、:760（`GenerationTaskCreatedEvent`）、:774（`GenerationTaskCompletedEvent`）、:905-914（目录列出 `event/` 包） |
| G-6 | `gen_type_mapping` 表已建但**全模块零引用**（无实体、无 Repository） | DDL 治理 | L3：确认下线（DDL 移除）或补实现 | `grep -rn "GenTypeMapping\|gen_type_mapping" src/main/java` → 0 命中；DDL 中表已存在 |
| G-7 | 横切文档滞后：`BONE-X-Studio` #L436 仍写 8082/8085 与 `/api/v1/generate/**` | 文档-代码一致 | 订正横切稿为 8086 与 `/api/v1/generator` | 代码实为 `8086` + `GeneratorApiPaths.CODE_GENERATION`（`/api/v1/generator/code-generation`）；`BONE-X-Studio` #L436 |

## 三、参考级对标（亮点）

- **响应契约是本轮最优**：8 个 Controller 中 **7 个直接返回 `ApiResponse<...>`**，零 `ResponseEntity`；唯一使用 `ResponseEntity` 的 `CodeGenerationController` 两处都合规——`createCodeGeneration` 为 `ResponseEntity<ApiResponse<?>>`（同步 `200` / 异步 **`202 Accepted` + `Location`**，标准 AIP-151 LRO），`downloadCode` 为 `ResponseEntity<ByteArrayResource>`（生成产物 ZIP 下载，合法例外）。
- **生成入口已收敛**：`/api/v1/generator/code-generation` 为唯一入口，旧 `/generation-tasks` 已删除，无双入口残留。
- **租户声明面合规**：`DataSource` / `CodeTemplate` / `GenerationTask` / `GenTableMetadata` / `GenColumnMetadata` **均显式声明 `tenantId`**。
- **DDL 审计列规范**：`gen_*` 表中仅 `gen_code_generation_history` 缺 `updated_at`，且已按 **by-design** 登记（`ddl-required-columns-baseline.json:35-41`：只追加，`started_at`/`completed_at` 替代，已有 `deleted`），与 `bone-integration` `int_execution_log`、extension `exts_audit_log` 同口径。
- **LRO 细节到位**：异步分支返回 `operationId` + `taskId` 并给出 `Location`；`getTaskStatus` 对 `null` 与 `UNKNOWN` 有明确兜底（注释说明 `success(String)` 会命中 message 重载，故用双参形式）。
- **易误判点已排除**：项目已知的「注释乱码」「`/tables` 跨库」「类名未驼峰」「列为空需重同步」均为已知环境/历史问题，本轮**不计为缺陷**。

## 四、v2 优化稿（锚定纠正 + B 阶段清单 + L3 待审批）

### 4.1 锚定纠正（`_global-contracts.yaml`）
- **HC-003**：`violated` → **`implemented`**。锚定证据「7 个 Controller 中 1 个返回 ResponseEntity」属**只看 `ResponseEntity` 不看 body** 的误判——该处正是标准 LRO 信封（`ResponseEntity<ApiResponse<?>>`，202+Location），另一处是 ZIP 文件下载。这是继 metadata-server / system / extension-studio 之后的**第四例同源误报**。
- **HC-008**：`partial` → **`violated`**。声明面与 DDL 均合规（5 实体显式 `tenantId`、仅 `gen_code_generation_history` 为 by-design 只追加），但**运行时租户来源不闭环**（G-1：零读取 `TenantContext` + 隐式默认 `0L`/`1L`），构成真实租户隔离缺口——与 extension-studio X-1 同类。
- **design_vs_code_gaps 复核结论**：doc8 事件零实现（确认成立）、`GenTypeMappingRepository` 不存在（确认成立）、模板 5/7（确认成立，实测 5 个 `.ftl`）、`BONE-X-Studio` #L436 端口/路径滞后（确认成立，代码已收敛 8086 + `/api/v1/generator`）、「工作树有 3 个未提交改动」→ **已消解**（当前工作树洁净）。

### 4.2 B 阶段实现清单（L2，非阻断）
- 修复 G-1：去 `0L`/`1L` 魔法默认值，写入/查询统一经 `TenantProvider.currentTenantIdOrNull()`；缺头时失败关闭。
- G-2：删除本地 `ApiResponse`，`CapabilityController` 改用 core 信封。
- G-3：建 `GeneratorErrorCodes` 常量类 + `errorCode` 独立字段，同步 `Bone-错误码登记.md` 与 i18n。

### 4.3 L3 待审批（本任务不执行）
- G-6：`gen_type_mapping` 下线（DDL 移除）或补实现的裁决。
- G-5：doc8 领域事件定位裁定（路线图标 [Target] vs 应落地）。
- G-4：doc8 #L68 模板承诺 7 类的口径订正或补齐。

## 五、A' 自审（三问）

| 问 | 结论 |
|----|------|
| **Q1 有无假阳性（误报）？** | **有 1 例已剔除**：HC-003。锚定据「1 个 Controller 返回 ResponseEntity」判 violated，实测该处为 `ResponseEntity<ApiResponse<?>>`（202+Location LRO）+ `ResponseEntity<ByteArrayResource>`（ZIP 下载），其余 7 个 Controller 直接返回 `ApiResponse`。已纠正为 `implemented`。另 HC-008 的 `partial` 中，`gen_code_generation_history` 缺 `updated_at` 属 **by-design**（已登记），该表本身不算违规。 |
| **Q2 有无漏检（假阴性）？** | **有 1 例已补**：锚定仅记 HC-008 `partial`（只提 DDL by-design），**未记运行时租户来源缺口** G-1（零读取 `TenantContext` + 隐式 `0L`/`1L`）。本轮全模块扫描确认后升级 HC-008 为 `violated` 并列阻断级。 |
| **Q3 证据是否可复现？** | 是。全部结论带 `文件:行`；G-1 另以「全模块 0 命中」的反向扫描佐证（非单点猜测）。G-1 影响面（泄漏 vs 空结果）已在报告中标注为待实测，未过度断言。 |

**自审结论：PASS（可放行至 B 阶段；G-1 租户闭环应优先于其余 B 项）。**
