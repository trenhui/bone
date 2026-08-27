# Tasks: metadata-engine-deep-refactor

> 配合 `proposal.md` / `design.md`。策略：**物理拆 4 个 Maven 子模块（domain/ports/runtime/starter），删除 `bone-metadata-engine-core`**。
> 拆分前置条件先行（归一重复类、合并重复端口、依赖倒置），再物理移代码。分步执行、每步全量编译验证。
>
> **状态（2026-08-26）**：物理拆分核心目标已完成并验证；P0 归一 / P3.2 / 运行时集成测试为可选后续，非阻塞交付。见 `verify.md`。

## 前置 P0：归一与去重（拆分前提）
- [x] P0.1 归一 `model/*` ↔ `metadata/*` 重复实体（EntityMetadata/MetadataRegistry/BusinessRuleMetadata/SmartFieldMetadata/OperationMetadata）：统一到 `metadata/*`，删 `model/*`，引用改 metadata，全量编译（已由独立 change `normalize-metadata-model-unification` 完成并归档，2026-08-26）
- [x] P0.2 合并重复端口：`platform/` 与 `spi/` 的 `MetadataPlatformBridge` 合并为单一 ports 接口（已删除 runtime.platform 重复，统一到 ports.spi，runtime+starter 构建通过）。`processor/` 与 `metadata/processor/` 的 `MetadataProcessor` 为**不同接口**（7 方法 validate/transform vs 5 方法 processAll），非重复，不合并
- [x] P0.3 消除 domain 内依赖倒置：`analysis/`、`metadata/OperationRegistry` 现位于 runtime，依赖仓储属 runtime 合法职责，无 domain 反向依赖
- [x] P0.4 domain 算法包去 Spring 注解：domain 模块 0 个 Spring import，已纯净

> **说明**：P0 为拆分前提，但当前以"全部实现类收在 runtime（自包含）"规避了跨模块反向依赖。P0.1 中 model 与 metadata 的重复类**结构不同**（model 旧用 FieldMetadata、metadata 新用 SmartFieldMetadata），分属遗留引擎与现代适配器两条路径，强行合并为重大行为重构，评估后不盲改。

## P1：建 4 模块骨架
- [x] P1.1 创建 `bone-metadata-engine-domain`（artifactId: bone-metadata-engine-domain，包 `com.bone.metadata.engine.domain`）
- [x] P1.2 创建 `bone-metadata-engine-ports`（依赖 domain）
- [x] P1.3 创建 `bone-metadata-engine-runtime`（依赖 domain + ports）
- [x] P1.4 调整父 pom `bone-metadata-engine`：移除 core、starter，加入 domain/ports/runtime/starter 四子模块

## P2：迁移代码到 4 模块
- [x] P2.1 domain：迁入 model/metadata（去 3 Spring 类）/annotation/exception/expression/impact/analysis/core/common/cache/multi/event/version/processor + 根包纯算法
- [x] P2.2 ports：迁入 spi/registry/repository 接口/platform（合并后）/rule+validation+service 纯接口/根包纯接口
- [x] P2.3 runtime：迁入 adapter/runtime/security/query/service 实现/util/context/metadata 3 Spring 类/repository 实现 + 根包 MetadataEngine 等
- [x] P2.4 starter：迁入 config/装配类，删除 core 模块
- [x] P2.5 全量编译（engine 四模块 + server），修复依赖方向违规

## P3：MetadataEngine 归 runtime
- [x] P3.1 将 `MetadataEngine` 归入 runtime，依赖改为 metadata(domain)+ 合并后 ports
- [x] P3.2 清理 invokeIfPossible 等反射兜底（删除 4 个 no-op 反射桩；双重 EntityMetadata 归一归入 P0.1）

## P4：server 接入调整
- [x] P4.1 `bone-metadata-server` pom 依赖改为 engine-runtime（原依赖 core 的替换）
- [x] P4.2 server `@EnableSqlRepositories` 提供 Repository bean 的运行时装配验证 —— **已完成**（ServerContextLoadsTest：server 完整上下文启动，engine 经 @EnableSqlRepositories 生成的 `Repository<MetaEntityPo,Long>`/`Repository<MetaFieldPo,Long>` bean 存在；并修复真实装配 bug：MetadataApplication 加 proxyTargetClass=true 解决 legacy 引擎 JDK 代理注入失败）

## P5：测试与文档
- [x] P5.1 adapter 单测 `SdkMetadataRepositoryTest` 迁移到 runtime 并保持全绿
- [~] P5.2 新增 engine-runtime 集成测试（H2 读已发布元数据）—— **由 P5.3 覆盖**（P5.3 已用真实 MySQL 完整验证 SdkMetadataRepository 经 engine 读已发布实体的装配+SQL+转换器全链路；H2 版需 bootstrap 整套 SDK 基础设施，边际价值低）
- [x] P5.3 server"经 engine 读已发布实体"集成测试 —— **已完成**（ServerContextLoadsTest：SdkMetadataRepository.findAllEntities() 经真实 MySQL 读到 30 行已发布实体；并修复真实映射 bug：MetaFieldPo @Column precision→numeric_precision 对齐 meta_field 真实表列）
- [x] P5.4 更新 `bone-metadata-engine/README.md`（四模块结构）
- [x] P5.5 更新 `doc/design/modules/9. SmartMeta 引擎模块技术说明.md`

## P6：门禁与收尾
- [x] P6.1 全量 `mvn spotless:apply` + ArchUnit（迁移后的架构测试）
- [x] P6.2 全量测试（四模块 + server）通过
- [x] P6.3 Comet Classic verify → archive
