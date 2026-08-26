# Superpowers Plan: metadata-engine-deep-refactor

> change：`metadata-engine-deep-refactor`
> build_mode：executing-plans ｜ tdd_mode：tdd ｜ review_mode：standard
> 策略：包内重划，不物理拆 Maven 子模块；分步执行、每步全量编译验证；重构以既有/新增测试兜底（TDD）。

## 实施目标

完成 `bone-metadata-engine` 的三类重度重构：
1. **T2**：`model/*` 与 `metadata/*` 重复类**彻底合并**（删除 model/*，引用统一到 metadata/*）。
2. **T5**：`MetadataEngine`（695 行 @Component）拆为 `application.MetadataEngineService`，清理反射兜底、统一双重 EntityMetadata。
3. **T1.3**：领域算法包去 Spring 注解。T6.1：starter 装配收敛。T7：测试与文档。

## 阶段划分

### 阶段 A：领域模型归一（T2）
- A1 盘点 model/* 与 metadata/* 同名类字段差异，补缺失字段到 metadata/*。
- A2 迁移引用少类（FieldMetadata/BusinessRuleMetadata/OperationMetadata/SmartFieldMetadata）：删 model 版、引用改 metadata，全量编译。
- A3 迁移引用最多类（model.EntityMetadata，14+ 引用）：删 model 版、引用改 metadata，全量编译。
- A4 收敛剩余 model/* 独有类，删除空 model 包。

### 阶段 B：MetadataEngine → application service（T5）
- B1 新增 `application.MetadataEngineService`，迁移用例编排方法（TDD：先迁移一个方法+测试再继续）。
- B2 依赖改 spi 端口 + MetadataCacheManager；清理 invokeIfPossible 等反射兜底。
- B3 `MetadataEngine` 改轻量门面委托 service，保持对外 API。

### 阶段 C：领域算法去 Spring 注解（T1.3）
- C1 逐包移除 @Component/@Autowired，装配改 application/starter。
- C2 EngineArchitectureTest 扩展 domain 零 Spring 断言。

### 阶段 D：starter 装配收敛 + server 接入（T6）
- D1 MetadataEngineAutoConfiguration 收敛为 kernel + 真实 adapter。
- D2 server @EnableSqlRepositories 提供 Repository bean 的运行时装配验证。

### 阶段 E：测试与收尾（T7/T8）
- E1 engine 集成测试（H2 读已发布元数据）；server 集成测试。
- E2 README/设计文档更新；全量 spotless + ArchUnit + 测试。

## 验证策略（TDD）
- 每个迁移/删除步骤前，先确保既有测试绿（兜底）。
- 新增重构方法/类时，先写对应测试（red）再实现（green）。
- 阶段 E 全量回归：engine-core 既有测试 + 新增集成测试 + server 测试 + ArchUnit。
