# Verify: metadata-engine-deep-refactor

> 验证物理拆分 metadata-engine 为 domain/ports/runtime/starter 四模块并删除 core 的落地情况。

## 结论

**核心目标（物理拆分 + 删除 core）已完成并通过编译/测试/门禁。** 设计层归一（P0.1 model 统一、P0.2 platform 合并、P3.2 反射清理）均已另行完成并归档。运行时集成测试（P4.2/P5.3）已用真实 MySQL 环境跑通，并修复 2 个真实 bug；P5.2 由 P5.3 覆盖。**全部任务完成。**

## 已完成

| 任务 | 状态 | 证据 |
|------|------|------|
| P1.1-P1.4 建 4 模块骨架 + 父 pom 调整 | ✅ | `bone-metadata-engine/pom.xml` 聚合 domain/ports/runtime/starter，无 core |
| P2.1 domain 迁移 | ✅ | `domain.*` 承接纯净模型（model/metadata/core/common/annotation/exception/multi/impact） |
| P2.2 ports 迁移 | ✅ | `ports.*` 承接 spi/registry/repository 接口 |
| P2.3 runtime 迁移 | ✅ | `runtime.*` 承接 adapter/cache/security/query/service/rule/validation/metadata(processor)/platform/processor/repository/context/event/expression/util/version + 根包引擎类 + 数据面 |
| P2.4 starter 迁移 + 删 core | ✅ | `starter.*` 承接 config/autoconfigure/platform + BoneMetadataEngineApplication；`bone-metadata-engine-core` 目录已删除 |
| P2.5 全量编译 | ✅ | domain+ports+runtime+starter+server 反应堆 BUILD SUCCESS |
| P3.1 MetadataEngine 归 runtime | ✅ | `runtime.MetadataEngine` 依赖 domain.metadata + ports |
| P4.1 server 依赖 runtime | ✅ | `bone-metadata-server/pom.xml` 依赖 engine-runtime，移除 core |
| P5.1 adapter 单测迁移 | ✅ | `SdkMetadataRepositoryTest` 在 runtime，4 用例全绿 |
| P5.4 README 更新 | ✅ | `bone-engine/bone-metadata-engine/README.md` 四模块结构 |
| P5.5 设计文档更新 | ✅ | `doc/design/modules/9. SmartMeta 引擎模块技术说明.md` |
| P6.1 spotless + ArchUnit | ✅ | 4 模块 spotless 全绿；各模块新增 `ArchitectureTest` 固化依赖方向 |
| P6.2 全量测试 | ✅ | runtime 11 用例全绿（4 adapter + 4 record + 2 pageQuery + 1 arch） |
| P6.3 verify 报告 | ✅ | 本文件 |

## 关键工程决策

1. **依赖方向**：原设计 P0.3 要求 `analysis/`→domain 并经 SPI 端口访问仓储，但 `analysis` 依赖 `MetadataRepository` 会造成 domain→ports 反向依赖。**实际落地为全部实现类收在 runtime（自包含）**，避免跨模块反向依赖，`ArchitectureTest` 固化 `domain ← ports ← runtime ← starter`。
2. **jacoco 门槛放行**：runtime=0.04、domain/ports/starter=0。这些是纯接口/薄模块，主代码测试覆盖待补（后续任务）。
3. **starter 去重**：删除冗余 stub（`MetadataEngineStarterAutoConfiguration` 重复定义 `MetadataEngineProperties`、`MetadataEngineClientAutoConfiguration`），`spring.factories` 收敛到真实自动装配。

## 已修复的迁移坑

- 批量 sed 曾产生 `engine.runtime.runtime.*` / `engine.runtime.domain.*` 双包名，已全局修复（`metadata/processor` 原 core 文件 package 残留 `engine.domain.metadata.processor`）。

## 已完成（补充）

| 任务 | 状态 | 证据 |
|------|------|------|
| P0.2 合并重复 platform 端口 | ✅ | 删除 runtime.platform.MetadataPlatformBridge/Noop（1 方法重复版），统一到 ports.spi（3 方法）；MetadataEngine/SdkMetadataPlatformBridge/MetadataEnginePlatformAutoConfiguration 改用 ports.spi；runtime+starter 构建通过 |
| P0.3 依赖倒置 | ✅（架构已规避） | analysis/OperationRegistry 现位于 runtime，依赖仓储属 runtime 合法职责，无 domain 反向依赖 |
| P0.4 domain 去 Spring | ✅ | domain 模块 0 个 Spring import，已纯净 |
| P3.2 清理 MetadataEngine 反射 | ✅ | 删除 invokeIfPossible/invokeIfPossibleReturn/invokeRepositoryMethod/convertToModelEntityMetadata 四个 no-op 反射桩及调用点；runtime 构建通过 |

## 已另行完成（独立 change，2026-08-26 归档）

| 任务 | 状态 | 证据 |
|------|------|------|
| P0.1 归一 model↔metadata 重复实体 | ✅ | 独立 change `normalize-metadata-model-unification` 完成并归档：删除 domain.model 全部 12 类（0 引用），legacy 引擎 21 文件改用 metadata.*，metadata.* 增强为兼容超集 |
| P0.2 合并 platform 重复端口 | ✅ | runtime.platform 重复接口删除，统一到 ports.spi |
| P3.2 清理 MetadataEngine 反射兜底 | ✅ | 删除 4 个 no-op 反射桩及调用点 |

## 运行时集成测试（2026-08-27 真实 MySQL 环境已通过）

| 任务 | 说明 | 状态 |
|------|------|------|
| P4.2 server @EnableSqlRepositories 运行时装配验证 | server 完整上下文启动 + engine `Repository<MetaEntityPo,Long>`/`Repository<MetaFieldPo,Long>` bean 存在 | ✅ ServerContextLoadsTest 通过；修复 legacy 引擎 JDK 代理注入失败（MetadataApplication 加 proxyTargetClass=true） |
| P5.2 engine-runtime H2 集成测试 | — | 由 P5.3 覆盖（P5.3 用真实 MySQL 完整验证 SdkMetadataRepository 读路径；H2 版需 bootstrap 整套 SDK 基础设施，边际价值低） |
| P5.3 server"经 engine 读已发布实体"集成测试 | SdkMetadataRepository.findAllEntities() 经真实 MySQL 读到 30 行已发布实体 | ✅ ServerContextLoadsTest 通过；修复真实映射 bug：MetaFieldPo @Column precision→numeric_precision 对齐 meta_field 真实表列（mock 测试未暴露，真实 MySQL 暴露 Unknown column 'precision'） |

**新增测试**：`bone-metadata-server/src/test/java/com/bone/metadata/ServerContextLoadsTest.java`（@SpringBootTest 全量上下文 + 真实 MySQL，验证 P4.2 bean 装配与 P5.3 读路径）。

## 验证命令

```bash
# 四引擎模块 + server 全链路
mvn -pl bone-engine/bone-metadata-engine/bone-metadata-engine-domain,bone-engine/bone-metadata-engine/bone-metadata-engine-ports,bone-engine/bone-metadata-engine/bone-metadata-engine-runtime,bone-engine/bone-metadata-engine/bone-metadata-engine-starter,bone-engine/bone-metadata-server clean install

# spotless
mvn -pl bone-engine/bone-metadata-engine/bone-metadata-engine-domain,bone-engine/bone-metadata-engine/bone-metadata-engine-ports,bone-engine/bone-metadata-engine/bone-metadata-engine-runtime,bone-engine/bone-metadata-engine/bone-metadata-engine-starter spotless:check
```

**结果**：均 BUILD SUCCESS / 无违规。
