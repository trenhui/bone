---
name: backend-java
description: Java Spring Boot 后端开发技能包（Bone DDD 架构）
---

## 技术栈约束
- Java 17/21，Spring Boot 3.2.x+
- MyBatis Plus（不用 Spring Data JPA）
- DDD 四层架构：domain → application → adapter → infrastructure
- JUnit 5 + Mockito 单元测试
- JaCoCo 覆盖率统计
- 参数校验：Jakarta Validation

## DDD 分层约定

### 1. Domain 层（领域）
- `com.bone.{module}.domain.entity` - 聚合根/实体
- `com.bone.{module}.domain.repository` - 仓储**接口**
- `com.bone.{module}.domain.enums` - 枚举定义
- `com.bone.{module}.domain.exception` - 领域异常
- `com.bone.{module}.domain.service` - 领域服务（如有）

### 2. Application 层（应用）
- `com.bone.{module}.application.service` - 应用服务
- `com.bone.{module}.application.dto` - 输入/输出 DTO
- `com.bone.{module}.application.converter` - 实体 ↔ DTO 转换器

### 3. Adapter 层（适配器）
- `com.bone.{module}.adapter.web.controller` - Web 控制器
- `com.bone.{module}.adapter.web.request` - 请求对象
- `com.bone.{module}.adapter.web.response` - 响应对象
- `com.bone.{module}.adapter.rpc` - RPC 适配器

### 4. Infrastructure 层（基础设施）
- `com.bone.{module}.infrastructure.persistence` - 仓储**实现**
- `com.bone.{module}.infrastructure.persistence.mapper` - MyBatis Mapper 接口
- `com.bone.{module}.infrastructure.config` - 配置类
- `src/main/resources/mapper/` - MyBatis XML 文件

## 编码规范
- 依赖注入：构造函数注入，`final` 修饰依赖
- 返回值：禁止返回 `null`，使用 `Optional<T>` 或空集合
- 异常：使用 `BusinessException`，错误码统一管理
- 日志：使用 `@Slf4j`，不使用 `System.out.println`
- 命名：遵循 Java 驼峰命名，接口不前缀 `I`
- 测试：每个服务有单元测试，测试方法名 `should_<behavior>_when_<condition>`

## 响应格式
统一返回 `Result<T>`：
```java
Result.success(data);
Result.error(code, message);
```

## 分页格式
统一返回 `PageResult<T>`：
```java
PageResult.of(list, total, page, size);
```

## 数据库约定
- 主键：`Long id`，自增
- 逻辑删除：`Integer deleted` (0-未删除, 1-已删除)
- 审计字段：`LocalDateTime createTime`, `LocalDateTime updateTime`
- 状态：`Integer status` (1-正常, 0-禁用)

## 切片检查命令
```bash
# api 层检查
mvn compile -pl {module}

# service 层检查
mvn compile -pl {module}

# mapper 层检查
mvn compile -pl {module}

# 测试
mvn test -pl {module}
```

## 质量门禁
- L1：覆盖率 ≥ 80%，最大圈复杂度 ≤ 10
- L2：覆盖率 ≥ 85%，最大圈复杂度 ≤ 8
- 方法行数：≤ 50（L1）/ ≤ 40（L2）
- 类行数：≤ 500（L1）/ ≤ 400（L2）
