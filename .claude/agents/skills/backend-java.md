---
name: backend-java
description: Java Spring Boot 后端开发技能包
---

## 技术栈约束
- Java 17+，Spring Boot 3.2.x
- MyBatis Plus（不用 Spring Data JPA）
- Repository 模式：接口在 `domain/repository`，实现在 `infrastructure/persistence`
- Velocity 模板用于代码生成
- JUnit 5 单元测试

## 编码规范
- 三层架构：adapter（controller）→ application（service）→ domain（entity/repository）→ infrastructure（persistence）
- 依赖注入使用构造函数，`final` 修饰依赖
- 接口定义在 domain，实现在 infrastructure
- 异常使用 `RuntimeException` 或业务异常
- 接口方法参数验证使用 `jakarta.validation`
- 接口返回统一响应格式
- `@Slf4j` 打日志，不用 `System.out.println`

## SQL 最佳实践
- 逻辑删除字段 `deleted boolean`
- 审计字段 `createTime/updateTime/createBy/updateBy`
- 分页使用 `PageResult<T>`
- 参数条件查询不用 `*` 野查询

## SpotBugs 处理
- 消除 `DLS_DEAD_LOCAL_STORE → 删除无用赋值
- 消除 `RCN_REDUNDANT_NULLCHECK` → 删除冗余空检查
- 消除 `NP_NULL_ON_SOME_PATH` → 处理空指针

## 包命名
- `com.bone.{module}.adapter → HTTP 适配器（controller）
- `com.bone.{module}.application.service` → 应用服务
- `com.bone.{module}.application.dto` → 数据传输对象
- `com.bone.{module}.application.converter` → 转换器
- `com.bone.{module}.domain.entity → 领域实体
- `com.bone.{module}.domain.repository → 仓储接口
- `com.bone.{module}.domain.enums → 枚举
- `com.bone.{module}.domain.exception → 异常
- `com.bone.{module}.infrastructure.persistence → 仓储实现
- `com.bone.{module}.config → 配置
