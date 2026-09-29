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

---

## 六、B 段实现（2026-09-29，轮换 #7 续跑）

### 6.1 G-1 租户闭环（阻断级，优先落地）

| # | 落地内容 | 文件 | 修复前 |
|---|---|---|---|
| 1 | 新增租户端口（domain 层，零框架依赖） | `domain/gateway/TenantProvider.java` | 无 |
| 2 | 适配 `TenantContext` 的实现 | `infrastructure/gateway/TenantProviderGatewayAdapter.java` | 无 |
| 3 | 领域侧去 `0L` 回落 → **失败关闭** | `domain/model/data/DataSource.java`（`create`） | `tenantId == null ? 0L : tenantId` |
| 4 | 应用侧去硬编码 `0L` → 取上下文，缺头抛稳定码 | `application/CreateDataSourceApplicationService.java` | `DataSource.create(null, 0L, ...)` |
| 5 | 网关侧去 `1L` 回落 → 先回落上下文、仍 null 才失败关闭 | `infrastructure/gateway/CatalogMetadataGatewayAdapter.java` | `tenantId != null ? tenantId : 1L` |
| 6 | 生成链路两处调用统一走 `resolveTenant()` | `infrastructure/service/CodeGeneratorServiceImpl.java` | 直传可能为 null 的入参 |
| 7 | 接口 javadoc 同步（不再声称「null 时默认 1」） | `domain/gateway/CatalogMetadataGateway.java` | 文档与实现不符 |

**原则**：写入侧与查询侧一律「取不到租户就拒绝」，不再用魔法默认值把数据静默落到平台租户 / 恒按租户 1 过滤。

### 6.2 G-2 响应信封收敛

- `adapter/web/controller/CapabilityController.java` 改用 core `com.bone.core.model.ApiResponse`（全模块 8 个 Controller 至此**完全一致**）。
- core 信封是本地类的**超集**（多 `code` / `timestamp`），属向后兼容加法，不破坏前端契约。
- 模块本地 `common/result/ApiResponse.java` **未删除** —— 删码属 L3，见 §10 待审批 #5。

### 6.3 G-3 错误码收敛

| 构件 | 说明 |
|---|---|
| `common/GeneratorErrorCodes.java` | 7 个稳定码常量（`GEN_TENANT_CONTEXT_MISSING` / `GEN_TEMPLATE_NOT_FOUND` / `GEN_GENERATION_FAILED` 等） |
| `common/GeneratorErrors.java` | 「码 → HTTP 状态」唯一表 + 反射 fail-fast（漏登记的码在 `httpStatusOf` 处立即抛 `IllegalStateException`，**不兜底成 400/500**） |

替换的裸串 / 三参构造：

- `GetCodeTemplateDetailQueryApplicationService`：`new BizException(404, "GEN_TEMPLATE_NOT_FOUND: " + id)` → `GeneratorErrors.of(TEMPLATE_NOT_FOUND, id)`。**修复前 `errorCode` 恒为 `null`**，前端 `i18n.t('errors.' + errorCode)` 的分支永不命中。
- `GenerationTaskOperationApplicationService`：裸串 `"GEN_GENERATION_FAILED"` → 常量引用。
- 登记与 i18n：`doc/architecture/Bone-错误码登记.md` 的 `GEN_` 段与前端 `GEN_*` 译文**复核时均已在位**，本轮仅需对齐常量值，无需新增。

### 6.4 同期并入的其它改动（非本轮发起，已随 `38af74db0` 入库）

| 项 | 内容 | 级别 |
|---|---|---|
| G-4 内置模板收敛 | 12 类骨架模板 + `OptionalArtifactType`（单测 / 文档开关产物）+ `BuiltInTemplateGateway` classpath 回落 | L2，但**超出报告 §4.3「本任务不执行」的口径** → 见 §10 待审批 #8 |
| 种子 / 迁移 | `bone-init.sql` 种子 `content=NULL`（正文真源改为 classpath `.ftl`）+ `scripts/migration/0009_...sql` | **L4 / L3** |
| 租户可见口径 | `CodeTemplateRepository.findPageByTenant` | L2 |
| 历史实体 | `CodeGenerationHistory.create` 新增 `tenantId` 首参 | L2 |
| CI 加固 | `.github/workflows/ci.yml` 接入 Gitleaks、覆盖率门槛 10%→15%；`bone-parent/pom.xml` 依赖变更 | L3 |

### 6.5 本轮修复的编译 / 门禁破损

| # | 症状 | 根因 | 处置 |
|---|---|---|---|
| 1 | `CodeGeneratorServiceImpl:61` 编译失败 | 缺 `java.util.function.Predicate` import | 补 import |
| 2 | `CodeTemplateControllerTest` 2 errors：`UndefinedFieldException` / NPE | **SDK `Criteria.or(Consumer)` 缺陷**：OR 组被塞成 `fieldName == null` 的原生条件（`Criteria#addNativeCondition`），`BaseRepository#validateCriteriaFields` 无条件校验即炸 | 改走 `QueryBuilder`（详见 §6.6） |
| 3 | `TemplateTypeConsistencyTest` 2 errors：`NoSuchField OPTIONAL_TEMPLATE_TYPES` | 常量已从 `Map` 改为 `List<String>`，测试仍按 `.keySet()` 调用 | 测试同步为 `List` |
| 4 | `CodeTemplateRepository.java:[29,9] 对or的引用不明确` | lambda 同时匹配 `or(SFunction)` 与 `or(Consumer<FluentQuery<T>>)` | 显式转型 `(Consumer<FluentQuery<CodeTemplate>>)` |

### 6.6 SDK `Criteria.or()` 缺陷（本轮最重要发现）

```
Criteria#or(Consumer) → addNativeCondition("(a OR b)")
                      → mainConditions.add(new Condition(null, sql, null, Operator.EQ))
                                                          ↑ fieldName == null
BaseRepository#validateCriteriaFields → FieldCache.getFieldByName(entityClass, null)
                                      → UndefinedFieldException
```

**危险点**：`toSql()` 层看起来完全正常，所以**基于它写的单测会假绿**，一到真实查询就炸。本轮已有两份测试踩中：

- `CodeTemplateRepositoryCriteriaTest` 原本断言 `Criteria.toSql()` 含 ` OR ` —— 绿，但运行期 NPE；
- 实际生效的 `CodeTemplateControllerTest`（真实 HTTP + H2）才暴露问题。

**处置**：运行期一律走 `QueryBuilder`；口径同时**收紧**为

```
tenant_id = :t OR (tenant_id = 0 AND created_by IS NULL)
```

比原口径多一层保护：平台租户（0）下的内置模板种子可见，但**平台租户下若有用户自建的孤儿数据（`created_by` 非空），不得公开给所有租户**。并在 `CodeTemplateRepositoryCriteriaTest` 增补两条回归防护（`platformRowsMustBeLimitedToSeedRowsOnly` / `sdkCriteriaOrIsBroken_mustNotBeUsedAtRuntime`），把该缺陷钉死，防止后人改回。

---

## 七、B' 代码复核（7 项）

| # | 复核项 | 结论 | 证据 |
|---|---|---|---|
| 1 | **分层依赖**（`adapter → application → domain ← infrastructure`） | ✅ 通过 | `TenantProvider` 端口在 `domain/gateway`，实现在 `infrastructure/gateway`；application 层只注入端口，不直连 infrastructure |
| 2 | **持久化唯一**（`bone-metadata-sdk`） | ✅ 通过 | 无 MyBatis / JPA 引入 |
| 3 | **并发控制**（禁 `FOR UPDATE`） | ✅ 通过 | 本轮改动无悲观锁；沿用 `@Version` 乐观锁 |
| 4 | **契约**（统一 `ApiResponse`） | ✅ 通过 | 8 个 Controller 全量 core 信封；`GeneratorHttpContractTest` 以真实 HTTP 断言 `success` + `timestamp` |
| 5 | **范围 creep** | ⚠️ 有 | G-4 模板补齐属报告 §4.3 明写「本任务不执行」的 L3 项，同期会话已实施并扩到 12 类；`bone-init.sql` / `ci.yml` / `pom.xml` / `scripts/migration/0009` 均被改动 → 全部登记 §10 待审批，**未自行回滚** |
| 6 | **删码** | ✅ 合规 | 本地 `ApiResponse` 保留未删（L3 事项，未擅自执行） |
| 7 | **门禁** | ✅ 通过 | `mvn -o -pl bone-engine/studio-generator test`：**105 tests, 0 failures, 0 errors**，含 `ArchitectureTest` 26 项；spotless 格式门禁随构建通过 |

**并发说明（须留档）**：本轮全程与另一会话共用同一工作树，出现 4 次「改完即被覆盖 / 编译被打破」的竞争。收敛方式为「只改必要行 + 每次改完立刻编译验证 + 不擅自回滚对方改动」。最终产物已由该会话提交为 `38af74db0`（`dev`，已推 `origin/dev`）。

---

## 八、C 段联调（真实 HTTP）

**前置说明**：本机 MySQL / Nacos 未运行，8086 无进程监听，故**无法对外部进程做真实端口联调**。C 段改用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` —— 内嵌 Tomcat 起真实端口、走完整 Servlet 栈与全局异常翻译（非 MockMvc 短路），HTTP 状态码为真实值。

| # | 端点 | 方法 | 真实 HTTP 码 | 响应摘要 / 断言 |
|---|---|---|---|---|
| 1 | `/api/v1/generator/capabilities` | GET | **200** | body 含 `"success"` 与 `"timestamp"` → 证明已是 **core 信封**（G-2） |
| 2 | `/api/v1/generator/templates/9999999` | GET | **404** | body 透出 `GEN_TEMPLATE_NOT_FOUND` → 证明 **errorCode 独立字段**已落到响应体（G-3）；修复前 `errorCode` 恒 null |
| 3 | `/api/v1/generator/templates/9999999` | GET | **404**（非 500） | `GlobalExceptionHandlerTranslationTest`：业务异常按自带 code 翻译，未被兜底成 500 |
| 4 | `/api/v1/generator/templates/1` | POST | **405**（非 500） | 方法不被支持，未被 catch-all 吞掉 |
| 5 | `/api/v1/generator/templates` | GET | **200** | `CodeTemplateControllerTest`（3 用例）：`records` 非空 → 租户可见口径在 H2 上跑通，未退化成空列表也未跨租户 |
| 6 | 租户缺失（用例层） | — | **400**（`GEN_TENANT_CONTEXT_MISSING`） | `GeneratorTenantClosureTest`（6 用例）：HTTP 链路无法复现（测试态过滤器会补平台租户），故在用例层把 `TenantProvider` 换成返回 null 的桩 |

**新增 C 段门禁**：`GeneratorHttpContractTest`（真实 HTTP，2 用例）——信封与错误码这两条最易静默回退的契约从此有回归防线。

---

## 九、D 段验收

| 步骤 | 内容 | 结果 |
|---|---|---|
| 1 构造数据 | H2 内存库 + `bone-init.sql` / schema；种子模板行 `content=NULL`、正文真源为 classpath `.ftl`（12 类 + 2 开关类） | ✅ |
| 2 模拟人工操作 | ① 打开模板列表 → 看到「自己的模板 + 平台内置种子」② 点开不存在的模板 → 404 + `GEN_TEMPLATE_NOT_FOUND` ③ 打开能力列表 → core 信封 ④ 创建数据源（无租户上下文）→ 400 `GEN_TENANT_CONTEXT_MISSING` 且**未落库**（`verify(repository, never()).insert(...)`） | ✅ |
| 3 清理自证 | 数据落在内存 H2，随 JVM 销毁，无残留；并发会话的临时 dump 测试 `TempDumpAllTest.java` 已删除，不入库 | ✅ |
| 4 门禁自证 | `mvn -o -pl bone-engine/studio-generator test` → **105 tests, 0 failures, 0 errors, 0 skipped**，BUILD SUCCESS（含 spotless 与 `ArchitectureTest` 26 项） | ✅ |
| 5 一致性门禁 | `TemplateTypeConsistencyTest` 5 用例：classpath 模板 / 生成器清单 / 初始化种子 / 迁移脚本 0009 **四处一致**，且种子不复制模板正文 | ✅ |

---

## 十、结论与 L3/L4 待审批清单

**本轮结论**：G-1（租户闭环，阻断级）、G-2（信封收敛）、G-3（错误码收敛）**全部落地并通过 B' 复核 + C 联调 + D 验收**；模块门禁由 85 → **105 测试全绿**。studio-generator 六段闭环完成，状态可置 `done`（pending_approvals 保留）。

**待审批（本任务不执行，需架构师 / 你裁定）：**

| # | 事项 | 级别 | 说明 |
|---|---|---|---|
| 1 | `bone-init.sql` 种子 `content=NULL` | **L4** | 禁碰文件。语义正确（正文真源改 classpath `.ftl`，避免库里复制一份随模板改造漂移），但属 DDL 变更，须人工确认 |
| 2 | `scripts/migration/0009_generator_builtin_template_convergence.sql` | L3 | 新增存量迁移脚本（已带 `NOT EXISTS` 幂等兜底，12 条补齐语句） |
| 3 | `.github/workflows/ci.yml` | L3 | Gitleaks 接入 + 覆盖率门槛 10%→15% + failsafe 补齐 |
| 4 | `bone-parent/pom.xml` | L3 | 依赖变更 |
| 5 | 删除模块本地 `common/result/ApiResponse.java` | L3 | `CapabilityController` 已不再引用，属死代码；删码需审批 |
| 6 | `gen_type_mapping` 下线（DDL 移除）或补实现 | L3 | G-6：表已建但全模块零引用 |
| 7 | doc8 领域事件定位裁定 | L3 | G-5：`DataSourceCreatedEvent` 等 0 命中 —— 标 `[Target]` 还是排期实现 |
| 8 | doc8 #L68 模板承诺 7 类的口径订正 | L3 | G-4：现实现为 12 类骨架 + 2 类开关产物，与承诺的 7 类不一致，二者必居其一 |

