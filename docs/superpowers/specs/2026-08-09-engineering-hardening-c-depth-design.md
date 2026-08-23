# Bone 工程硬化（方案 C · 深度）设计文档

> 状态：待评审  
> 日期：2026-08-09  
> 依据：架构审查结论 + 业界实践（12-factor config、单一 API 契约、CQRS 边界、ArchUnit 门禁、文档=真源）

---

## 1. 目标与成功标准

| 目标 | 成功标准 |
|------|----------|
| 本地/网关路由正确 | Gateway 默认 URI 与 AGENTS 端口表一致；未设 env 时 generator≠integration |
| 密钥安全默认 | 非 `dev`/`test` profile：JWT/DB 无静默弱默认；缺省则启动失败或明确空值策略 |
| 单一响应契约 | 后端仅 `com.bone.core.model.ApiResponse`；前端分页统一 `records/page/size` |
| CQRS 边界可执行 | 10 个违规 Controller 改为 Handler-only；ArchUnit 规则可拦截回潮 |
| 文档真源 | CODE_WIKI/AGENTS 不再宣称未落地的 SA-Token/Seata/Flyway/全表 `biz_identity_code` |
| 领域纯度分阶段 | Phase 3：`AbstractEntity` 去掉 Spring/Swagger/Jackson 注解；generator 停止直接返回 domain |

**非目标（本设计明确不做）**

- 引入 Flyway 全量迁移体系（另立项）
- 改 `bone-init.sql` 增加 `biz_identity_code`（L3 DDL，需架构师另批）
- Wasm 扩展运行时、TPA 行业包
- 一次性拆分共享 DB 为多库微服务（拓扑重构）

---

## 2. 分阶段交付（可独立合入）

### Phase 0 — 运维止血（0.5–1d）

1. **Gateway** `application.yml`：  
   - `BONE_MASTERDATA_URI` 默认 `http://localhost:8084`  
   - `BONE_GENERATOR_URI` 默认 `http://localhost:8086`
2. **masterdata** `application-prod.yml`：端口与主配置对齐为 `8084`（或文档明确 prod 必须设 `BONE_MASTERDATA_URI`；推荐代码对齐 8084）。
3. **密钥**：  
   - 主 `application.yml`：JWT/DB 保持无默认（已部分到位）。  
   - `application-prod.yml`：禁止 `${ENV:weakDefault}`；空默认 `${ENV:}` 改为无默认或启动校验 Bean。  
   - `studio-generator` 主 YAML：去掉 `mysql123` 默认，改 `${BONE_DB_PASSWORD}` 无默认；仅 `application-dev.yml` 保留本地默认。  
   - `extension-studio` 主 YAML：去掉 JWT 弱默认，仅留在 `-dev.yml`。  
   - MinIO：主 YAML 去掉 `minioadmin` 默认，仅 `-dev.yml` 保留。
4. **文档**：AGENTS / CODE_WIKI 同步端口、认证栈、Seata/Flyway/biz_identity 现状。

### Phase 1 — 后端契约与 CQRS（2–3d）

1. **删除** `studio-generator/.../common/result/ApiResponse.java`；`CapabilityController` 改用 `com.bone.core.model.ApiResponse`。
2. **CQRS 补 Handler（10 Controller）**，按模块：

| 模块 | 工作项 |
|------|--------|
| masterdata | `DeleteMasterDataEntityCommandHandler`、`DeleteMasterDataRecordCommandHandler`；`QualityCheckListQueryHandler` / `QualityReportListQueryHandler`；Controller 去掉 Repository |
| iam | `LogoutCommandHandler`（JWT 解析 + blacklist）；AuthController 去掉 Service 注入 |
| metadata-server | `Metadata*` Handlers 包装 SDK；`RuntimeRecord*Handler` 包装 `JdbcRuntimeRecordService`；幂等迁入 Publish Handler |
| studio-generator | `GetGeneratorOperationQueryHandler`、`CodeGenerationHistoryListQueryHandler`、`SubmitCodeGenerationCommandHandler`、`GetGenerationTaskStatusQueryHandler`；Controller Handler-only |

3. **ArchUnit**：在 `BoneDddArchRules` 强化/曝光已有 `adapterControllersMustNotDependOnApplicationService` / `...DomainRepository`；违规模块先用 `FreezingArchRule`，修完后改为 strict（对标 blueprint）。

### Phase 2 — 前端契约统一（2–3d）

1. **增强** `@bone/shared-types`：`ApiResponse` 增加可选 `success?` / `timestamp?`（与 bone-core 对齐，不破坏现有）。
2. **迁移顺序**：iam → generator → system → integration → masterdata → metadata → extension → shell。  
   - 删除本地重复类型或 `export type { ApiResponse, PageResult } from '@bone/shared-types'`。  
   - UI 从 `.list` / `pageNum` 改为 `.records` / `page` / `size`；去掉或反转 `normalizePage`。
3. **`.env.example`**：一律说明「开发默认走 Vite proxy → gateway `:8888`」；`VITE_API_BASE_URL` 留空或示例注释；端口表对齐 AGENTS。
4. **baseURL**：推荐统一「client 前缀含 `/api/v1/{module}`」（iam/integration 模式）；metadata/masterdata/generator 逐步收口（本 Phase 至少统一类型；baseURL 可第二 PR）。

### Phase 3 — 领域纯度（2–4d，可拆 PR）

1. **generator**：所有 Controller 返回 `*Resp`/`*DTO`，禁止直接序列化 `domain.data.*`。
2. **`AbstractEntity` / `Entity`**：移除 `@Schema`、`@JsonFormat`、`@DateTimeFormat`、`@JsonSerialize`；序列化关注点留在 `AbstractDTO` / adapter Resp。  
   - 前置条件：全仓库 Controller/测试不再把 `AbstractEntity` 子类当 JSON 根返回。  
   - 全局 Jackson 配置补 `Date` → `yyyy-MM-dd HH:mm:ss`（GMT+8）作为兜底。
3. **租户上下文**：文档声明唯一真源为 `com.bone.core.tenant.context.TenantContext`；metadata-engine `MultiTenantContextHolder` 改为委托到 core（或标注 deprecated + 适配桥）。

### Phase 4 — 质量门禁与依赖卫生（1–2d）

1. HC-003：实现真实 `controllerMustReturnApiResponse` ArchUnit（文档已写、代码缺失）。
2. 平台模块逐步挂 JaCoCo（先 iam / integration，阈值可先 50% 再升 70%）。
3. **JJWT**：blueprint 升到与平台一致的 `0.12.x`。
4. **blueprint RocketMQ**：要么补 pom 依赖并对齐 integration，要么删除/隔离无法编译的 MQ 源码到 profile+optional 模块（推荐：补依赖或 `@Profile("mq")` + 明确 pom）。
5. **空壳模块**：`bone-file` 从 reactor 移除或标注 stub；AGENTS 修正 `bone-notification` 为库而非服务。

---

## 3. 风险与回滚

| 风险 | 缓解 |
|------|------|
| Gateway 端口变更破坏已手写 env 的环境 | 仅改**默认值**；显式 env 不受影响 |
| prod 去掉密码默认导致启动失败 | 预期行为；Compose/K8s 清单同步补 env |
| 前端 `list`→`records` 漏改页面空白 | 按 app 迁移 + 冒烟脚本；临时兼容层最多保留一个 release |
| ArchUnit freeze 文件噪音 | 先 freeze，修完删 store |
| AbstractEntity 去注解破坏隐藏序列化路径 | Phase 3 前先 grep 返回类型；generator 先行 DTO |

---

## 4. 验证计划

- `mvn -pl bone-platform/bone-gateway,bone-platform/bone-masterdata,bone-engine/studio-generator,bone-platform/bone-iam -am test -DskipITs`（按 Phase 扩大）
- 各模块 `ArchitectureTest`
- 前端：`npm run build`（受影响 app）+ 关键列表页分页冒烟
- 人工：启动 gateway + iam + masterdata + generator，curl `/api/v1/masterdata/**` 与 `/api/v1/generator/**` 确认上游

---

## 5. 建议本会话执行批次

深度 C 体量约 **8–12 人日**。建议本会话落地：

1. **Phase 0 全部**  
2. **Phase 1**：ApiResponse 合并 + masterdata/iam 删除/Logout Handler + ArchUnit 规则接线  
3. **Phase 2**：shared-types 增强 + `.env.example` + iam/system/integration/masterdata/metadata 类型迁移（extension 若冲突则 alias）  
4. Phase 3–4 开独立 PR（本会话写清任务清单到 plan，能做多少做多少）

---

## 6. 待你确认

请确认：

1. 是否按 **§5 本会话批次** 执行（Phase 0+1 核心 + Phase 2 主路径）？  
2. Phase 3（AbstractEntity 去注解）是否纳入本会话，还是单独 PR？  
3. `biz_identity_code`：本设计选择 **文档对齐（不改 DDL）** — 是否同意？
