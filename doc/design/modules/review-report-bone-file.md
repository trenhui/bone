# 文件服务（bone-file）设计方案复核报告

> **执行时间**：2026-09-29（夜间自动化）｜**轮换序号 #8**｜**对应设计稿**：无（锚定 `design_doc: null`，本晚产出 v1）
> **锚定版本**：`doc/design/_global-contracts.yaml` `generated_at=2026-09-26T23:40`（距本晚 3 天，≤7 天，前提 3 满足）
> **代码快照**：分支 `dev`，HEAD `332c1b925`
> **对标基线**：锚定 `hc_semantics` + `doc/architecture/多租户数据隔离方案设计.md`（§3.6 / §5 R4、通则 T1）+ `Bone-API-规范.md` + AIP-133/134/151
> **本晚产物**：`doc/design/modules/bone-file-设计方案.md`（v1，从零）

---

## 〇、运行前提与模块选择说明（本晚特殊，先交代）

| # | 前提 | 实测 | 处置 |
|---|------|------|------|
| 1 | 工作树洁净（`git status --porcelain` 空） | **不满足**：48 项在途改动，其中 `bone-engine/studio-generator/**` 15 改 + 20 新文件、`bone-platform/bone-iam/Tenant.java`、`bone-platform/bone-system/SysDictItemRepository.java`、`bone-init.sql`、`bone-frontend/apps/bone-generator-app/**` | 按 §0「禁止把他人未提交改动一起提交」与仓库既有并发约定（commit `5f7e77f90`「保全并发 nightly 在途改动」）：**本晚不碰任何在途文件、不做 `git add -A`、不把他人 WIP 卷入提交** |
| 2 | 不在主干上改 | 当前分支 `dev`（非 `main` / `master` / `release/*`） | 满足；本晚仅新增文档，**不建特性分支、不提交代码** |
| 3 | 锚定新鲜 | `generated_at=2026-09-26T23:40` | 满足 |
| 4 | 契约基线可读 | `modules` 非空、`hc_semantics` 存在 | 满足 |
| 5 | 无显式叫停 | `doc/design/approvals/bone-file.yaml` 不存在 | 满足（= `auto`） |

### 为什么本晚是 `bone-file` 而不是 `studio-generator`

轮换序 #7 `studio-generator` 状态为 `design_ready`，按 §1.3 本应进 B 段（G-1 租户闭环）。但**开工前盘点在途改动发现该模块正被并发会话占用**：

- `git diff --stat bone-engine/studio-generator/` → 15 个文件、691 增 / 296 删；
- 新增 7 个模板生成器（`CreateCommandGenerator` / `UpdateCommandGenerator` / `QueryDtoGenerator` / `CreateRequestGenerator` / `UpdateRequestGenerator` / `PageQueryGenerator` / `AssemblerGenerator`）+ 7 个 `.ftl`；
- `CodeGeneratorServiceImpl.java:33-47` 的 `BUILT_IN_TEMPLATE_TYPES` 由 5 类扩到 12 类——**这正是本模块 A 段 finding G-4（模板 5/7）的 B 段实现**；
- 最后修改时间 `2026-09-29 03:08–03:41`，距本次运行约 2.5 小时。

**裁决**：`studio-generator` 处于他人 B 段在途状态，此时介入会与并发会话争抢同一批文件（且 `CodeGeneratorServiceImpl.java:142,146` 的 `0L` 租户默认值就落在其在途改动的文件里）。按「保护他人 WIP」优先于「轮换序字面顺序」的原则，**本晚取下一个未被占用的模块 `bone-file`（#8）**，并在状态文件 `warnings` 中留痕。下一轮如 `studio-generator` 在途改动已落库，应回到 #7 继续 B 段。

> 按 §1.7：`design_doc == null` 的模块本晚**只产出 v1 设计稿（功能 + UI），状态停在 `design_ready`，不进实现**。故本报告第六（B'）、第七（C）、第八（D）章本晚为 N/A。

---

## 一、阻断级问题（必须先改设计/实现才能达标）

> 本模块无既有设计稿，故「阻断级」定义为：**当前代码存在的、会使文件服务在多租户平台上不可安全使用的缺陷**。

| # | 问题 | 违反项 | 修复建议 | 证据（命令 + 结果 + 文件:行） |
|---|------|--------|----------|------------------------------|
| **FL-1** | **越权下载/删除**：对象寻址完全依赖客户端提供的 `objectName`，无任何归属校验——任何通过网关认证的用户猜到/拿到对象名即可删除任意文件 | 多租户方案 §5 R4（行级隔离）、AGENTS.md §一.5（租户与审计）、OWASP API1（BOLA） | 改为按库表主键 `id` 寻址；下载/删除前校验 `file_object.tenant_id == TenantProvider.currentTenantIdOrNull()`，不符即 403 | `grep -rn "PreAuthorize" src/main/java` → 仅 `SecurityConfig.java:29` **javadoc**（0 处真实注解）；`FileController.java:44-58`（`download` 直接 `fileStorageService.download(bucket, objectName)`）；`FileController.java:61-68`（`delete` 同） |
| **FL-2** | **零租户维度**：全模块不读租户上下文，对象键无租户前缀，无法按租户治理/清理/计量 | 多租户方案 §5 R1/R4、通则 T1、E-2（租户取值须经 `TenantProvider` 端口） | 新增 `domain/gateway/TenantProvider` + `infrastructure/gateway/TenantProviderGatewayAdapter`（对齐 `bone-iam` 实现，无默认值兜底）；对象键改为 `{tenantId}/{yyyy}/{MM}/{dd}/{uuid}.{ext}` | `grep -rn "TenantContext\|TenantProvider" src/main/java \| wc -l` → **0**；`FileController.java:32`（`UUID.randomUUID() + "-" + originalFilename`） |
| **FL-3** | **路径遍历 / 对象键注入**：`objectName` 直接来自 `@PathVariable`，未校验即可含 `../`、绝对路径等，拼接进 MinIO 键与响应头 | OWASP API1/API8、CWE-22 | 服务端生成键；客户端文件名只入库展示；响应头转义或改用 RFC 5987 `filename*=UTF-8''` | `FileController.java:45`（`@PathVariable String objectName`）；`FileController.java:52`（`"attachment; filename=\"" + objectName + "\""`） |
| **FL-4** | **文件资产不可治理（零元数据表）**：`bone-init.sql` 无 `file_` 前缀表，对象只存在于 MinIO ⇒ 无归属、无容量、无审计、无法回收孤儿对象 | 多租户治理基线、可观测性规范 | 新增 `file_object` 表（**L3，需架构师审批**），字段对齐 HC-008（`tenant_id` + 审计列 + `deleted` + `version`） | `grep -n "file_" bone-init.sql` → 命中全为**其它表的字段**：`exts_artifact.file_path:692`、`file_size:693`、`gen_code_generation_history.file_count:915`；`CREATE TABLE` 共 55 张，无 `file_` |
| **FL-5** | **错误码与 i18n 双缺失**：5 处 `IllegalStateException(裸中文)`，无 `FILE_` 常量类、无 errorCode 字段、无 i18n 键，全部兜底 500 | 错误码登记 §3.1（新接口强制字符串业务码）、B7 维度 | 建 `FileErrorCodes` 常量类 + `FileErrors`（码→状态真源，四参 `BizException(status,msg,errorCode,cause)`）；登记 §6 `FILE_` 段 + 双语语言包 | `grep -rn "IllegalStateException" src/main/java` → `FileController.java:38,55`；`MinioFileStorageService.java:64,75,86`；`grep -rn "FILE_" src/main/java` → 仅 `FileController.java:21` 的路径常量 |

## 二、建议级优化

| # | 当前设计/实现 | 业界对标 | 优化方案 | 证据 |
|---|--------------|----------|----------|------|
| **FL-6** | 出站端口 `FileStoragePort` 放在 `application/port/out` | 仓库其余模块均为 `domain/gateway/*`（`CatalogMetadataGateway`、`TenantProvider`） | 迁移至 `domain/gateway/FileStorageGateway`（L2 重命名，属 L3 删码需审批，可先保留并新增门面） | `bone-engine/studio-generator/.../domain/gateway/CatalogMetadataGateway.java:13`；`bone-platform/bone-iam/.../domain/gateway/TenantProvider.java:14` |
| **FL-7** | 上传返回 `ApiResponse<String>`（仅 objectName） | AIP-133 创建应回传资源表示 | 改 `ApiResponse<FileObjectResp>`（含 id/name/size/contentType） | `FileController.java:29-42` |
| **FL-8** | `@EnableSqlRepositories("com.bone.file.domain.repository")` 指向**不存在**的包 | 配置应有实际落点 | 随 FL-4 建包生效；未落库前建议移除该扫描（避免误导） | `FileApplication.java:17`；`ls src/main/java/com/bone/file/` → 无 `domain` |
| **FL-9** | 无 OpenAPI spec；锚定记 `HC-007: violated` | HC-007 语义 =「PR 的 OpenAPI spec 不得引入 breaking change」 | **锚定误判**：无 spec 属「无契约可校验」，应记 `not_applicable` 而非 `violated`；同时建议补 `file-v1.yaml` | `ls doc/architecture/openapi/` → 无 `file-v1.yaml`；锚定 `_global-contracts.yaml:485` |
| **FL-10** | `/files/test` 以业务端点暴露存储连通性 | Actuator 契约 | 收敛为 `FileStorageHealthIndicator`；`/test` 标 `Deprecated` 保留兼容 | `FileController.java:70-73`；`application.yml:34`（仅暴露 `health,info`） |
| **FL-11** | CORS 硬编码 8 个 localhost 源 + `allowCredentials(true)` | 环境配置外置 | 改配置项注入，生产按域名白名单 | `SecurityConfig.java:73-90`（`setAllowedOrigins` 8 个 localhost + `setAllowCredentials(true)`） |
| **FL-12** | 无类型/大小白名单，仅 Servlet `max-file-size: 50MB` | 存储型攻击防护 | 扩展名白名单 + MIME 嗅探 + `X-Content-Type-Options: nosniff` | `application.yml:13-15` |
| **FL-13** | IAM 另有一份 `MinioClient.builder()`（`audit-logs` 桶） | 单一实现 | 属 IAM 内部归档，**不并入门面**；建议复用客户端工厂配置口径 | `bone-platform/bone-iam/.../MinioStorageClientImpl.java:31`（第二份 `MinioClient.builder()`，桶 `audit-logs`） |

## 三、参考级对标（亮点）

- **响应信封合规（HC-003 implemented）**：4 个端点中 3 个返回 `ApiResponse<T>`（`FileController.java:29,61,70`），唯一例外 `download` 返回裸流（`void` + `HttpServletResponse`），属**文件下载的合法例外**——与本轮已复核的 `bone-metadata-server` / `bone-system` / `bone-extension-studio` / `studio-generator` 的同源判据一致。
- **无 ORM 违规（HC-001 / HC-006 implemented）**：模块无 `JdbcTemplate` / MyBatis 依赖；虽声明 `bone-metadata-sdk` 但当前零实体，不存在绕过。
- **出站端口抽象到位**：`FileStoragePort` 已把 MinIO 隔离在 `infrastructure/storage`，换 OSS/COS 只需新增实现——这是本模块**最好的一处设计**，v1 予以保留。
- **JWT 复用框架抽象类**：`JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter`，无自造鉴权。
- **无硬编码密钥字面量**（HC-004 判定以 `scripts/scan-secrets.sh` 为准）：`minioadmin` 仅为 `@Value` 默认值，生产可注入覆盖。

## 四、v1 设计稿

已落盘：**`doc/design/modules/bone-file-设计方案.md`**（约 10 章 + 附录 A 证据表 13 条），覆盖：

- §0 可观测性契约（SLI/SLO，含 As-Is 无埋点的实测判据）
- §1 模块概述 + **三处对象存储的边界裁决**（本模块 / IAM 归档 / integration 协议客户端）
- §2 功能设计（上传 / 下载预览 / 删除生命周期 / 元数据检索 / 配额 / 权限租户隔离）
- §3 **UI 设计**（U1–U5 页面清单、列表与上传交互线框、五种状态覆盖、`file.*` i18n 键清单）
- §4 数据模型（零表现状 + `file_object` DDL 提案 + 对象键命名规范）
- §5 API 设计（横切约定 + 5 组端点 + `FILE_` 错误码表 8 码）
- §6 技术实现（目标分层树、配置项、部署与集成）
- §7 性能与安全（S1–S10 风险表）
- §8 测试计划 / §9 监控告警 / §10 总结

## 五、实现计划（逐项标 L 级）

> **本晚不执行**（§1.7：`design_doc == null` 只产出设计稿，状态停 `design_ready`，需人工先看稿）。以下为下一轮 B 段候选清单。

### 后端文件清单

- [ ] **L2** | `bone-platform/bone-file/.../domain/gateway/TenantProvider.java`（新） | 租户端口，无默认值兜底（对齐 `bone-iam`）
- [ ] **L2** | `bone-platform/bone-file/.../infrastructure/gateway/TenantProviderGatewayAdapter.java`（新） | 唯一读 `TenantContext` 处
- [ ] **L2** | `bone-platform/bone-file/.../domain/model/FileObject.java`（新） | 聚合：`id`/`tenantId`/`bucket`/`objectName`/`originalName`/`contentType`/`size` + `version`
- [ ] **L2** | `bone-platform/bone-file/.../common/FileObjectKeyGenerator.java`（新） | 对象键 `{tenantId}/{yyyy}/{MM}/{dd}/{uuid}.{ext}` + 扩展名白名单
- [ ] **L2** | `bone-platform/bone-file/.../common/FileErrorCodes.java` + `FileErrors.java`（新） | 8 个 `FILE_*` 码 + 码→状态真源 + 反射 fail-fast
- [ ] **L2** | `bone-platform/bone-file/.../adapter/web/controller/FileController.java` | 5 处裸 `IllegalStateException` → `FileErrors`；返回 `FileObjectResp`；`@PreAuthorize`
- [ ] **L2** | `bone-platform/bone-file/.../infrastructure/storage/MinioFileStorageService.java` | 3 处裸异常换码；键校验与转义
- [ ] **L1** | `bone-frontend` 新增 `apps/bone-file-app`（微应用 + 页面 U1/U2） | 列表 + 上传，`id` 用 `string`
- [ ] **L1** | 双语语言包 `file.*` + `errors.FILE_*` | 过 `scripts/check-i18n-sync.py`
- [ ] **L1** | `doc/architecture/openapi/file-v1.yaml`（新） | 补 HC-007 可校验面
- [ ] **L1** | `doc/architecture/Bone-错误码登记.md` §6 新增 `FILE_` 段 | 8 行

### 待审批清单（L3 / L4，本任务**不执行**）

- [ ] **L3** | `bone-init.sql` 新增 `file_object` 表 | 理由：文件资产可治理的前置；影响面：新表 + 全量 DDL 基线 + `ddl-required-columns-baseline.json` 登记
- [ ] **L3** | 删除 `application/port/out/FileStoragePort` 旧包路径（迁移至 `domain/gateway`） | 理由：与仓库通则对齐；影响面：删码，需确认无跨模块引用（实测仅本模块 1 处实现）
- [ ] **L3** | `bone-file/pom.xml` 移除未使用的 `bone-metadata-sdk`（若 FL-4 未获批） | 理由：依赖瘦身；影响面：需确认 `MetadataAutoConfiguration` 是否为启动必需
- [ ] **L3** | 回收站/软删策略与保留期裁定 | 理由：涉及数据保留合规

### 联调前置条件

1. 网关 8888 可达，且**反向代理 body 上限 ≥ 60MB**（否则大文件在网关侧被截断）；
2. MinIO 可用 + `BONE_MINIO_*` 已注入；
3. `BONE_IAM_JWT_SECRET_KEY` 显式注入（该模块无默认密钥，见 `scripts/dev/restart-mvp-services.sh:49`）；
4. 若 FL-4 未获批，则「列表 / 配额 / 回收站」类联调**不可做**，只能验证上传-下载-删除链路。

## 六、代码复核结果（B' 段）

**N/A** —— 本晚未进入 B 段（§1.7）。

## 七、联调验证结果（C 段）

**N/A** —— 本晚未进入 C 段。

## 八、验收测试结果（D 段）

**N/A** —— 本晚未进入 D 段。

## 九、AI 自审结论（A' 三问）

| 问 | 结论 |
|----|------|
| **Q1 证据可复现？** | 是。全部 13 条 As-Is 证据（附录 A）与 5 条阻断级 finding 均带可重跑命令 + `文件:行`。反向扫描（0 命中）类结论已注明命令本身，避免「单点猜测」。FL-1 的「可越权」由「`objectName` 来自 `@PathVariable` 且无 `@PreAuthorize`、无租户字段」三点共同支撑，未做未验证的运行时断言。 |
| **Q2 分级正确？** | **有 1 例已纠正（锚定误报）**：锚定 `_global-contracts.yaml:485` 记 `HC-007: violated`，理由是「`doc/architecture/openapi/` 下无 file spec」。但 HC-007 语义为「**PR 的** OpenAPI spec 不得引入 breaking change」——无 spec 属「无契约可校验」，应记 `not_applicable`/`unknown`，而非 `violated`。**已降级为建议级 FL-9**（补 spec 仍建议做）。<br>**有 1 例已升级**：锚定记 `HC-008: implemented`（因无新增表），判定**本身正确**；但运行时租户缺口（FL-2）与 `bone-extension-studio` X-1、`studio-generator` G-1 同族，故在报告中列为阻断级 FL-2，**不改变 HC-008 的 implemented 判定**（HC-008 只约束「新增表」）。<br>无「为好看而降级」的项。 |
| **Q3 v1 稿安全？** | 是。v1 覆盖全部 5 条阻断级 + 8 条建议级；未引入新的硬约束违反（无 ORM、无裸 ResponseEntity、无硬编码密钥、无 DDL 落地）；对象键规范与错误码表与锚定 `shared_dtos`（`ApiResponse`、`PlatformApiPaths.FILE_V1`）一致；**L3 项全部单列待审批**，实现清单不含 DDL / 删码 / 依赖 / CI。 |

**自审结论：✅ PASS（产出 v1 设计稿，状态 `design_ready`；按 §1.7 本晚不进 B 段）。**

---

## 附：本晚状态迁移与遗留

| 项 | 值 |
|----|----|
| 模块 | `bone-file`（轮换 #8） |
| 状态迁移 | `pending` → `design_ready`（`stages_done: ["A", "A'"]`） |
| 产物 | `doc/design/modules/bone-file-设计方案.md`（v1）、本报告 |
| 阻断级 | 5 条（FL-1 越权 / FL-2 零租户 / FL-3 路径遍历 / FL-4 零元数据表 / FL-5 错误码 i18n） |
| 建议级 | 8 条（FL-6 ~ FL-13） |
| L3 待审批 | 4 条 |
| 下一步 | 人工先看 v1 稿；通过后进入 B 段（优先 FL-2 租户端口 + FL-5 错误码，二者与其它模块已落地项同构、可复用 `TenantProvider` / `*Errors` 模式） |
| 阻塞 | 无。**但轮换 #7 `studio-generator` 因并发会话在途改动被本晚跳过**，需在途改动落库后回补 B 段 |
