# Bone SmartMeta 企业级元数据操作系统

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://img.shields.io/badge/build-passing-green)](https://github.com/bone-engine/bone-smartmeta)

Bone SmartMeta 是一个融合了Salesforce、Workday和Coupa最佳实践的企业级元数据操作系统，提供智能元数据管理、动态数据模型、AI增强功能等核心能力。通过动态定义和管理元数据，实现业务模型的敏捷迭代与数据一致性保障。

## 项目价值

- **业务敏捷性**: 支持业务模型的动态调整，无需修改代码即可适配业务变化
- **数据一致性**: 集中化元数据管理，确保跨系统数据模型的一致性
- **开发效率提升**: 减少样板代码，加速应用开发和迭代周期
- **系统集成能力**: 提供标准化的数据访问和转换机制，简化系统集成
- **AI赋能**: 集成AI能力，提供智能数据分析和元数据优化建议

## 核心功能

### 1. 智能元数据管理
- 实体元数据注册、查询、更新和删除
- 按业务域分组管理元数据
- 支持计算字段和虚拟字段
- 元数据缓存和变更通知机制

### 2. 数据验证引擎
- 实体数据验证
- 必填字段验证
- 字段类型验证
- 自定义验证规则支持

### 3. 表达式引擎
- SpEL表达式支持
- 动态字段计算
- 条件表达式评估
- 表达式缓存优化

### 4. 数据转换引擎
- 实体数据格式转换
- 字段映射和转换
- 数据过滤和排除
- 自定义转换器支持

### 5. AI增强功能
- AI元数据支持：智能生成和优化元数据定义
- 智能字段建议：基于业务场景提供字段设计建议
- 自动元数据分析：识别潜在的元数据问题和优化机会
- 预测性元数据管理：基于历史数据分析元数据变更趋势

## 快速开始

### Maven依赖

```xml
<!-- 使用Starter快速集成 -->
<dependency>
    <groupId>com.bone</groupId>
    <artifactId>bone-smartmeta-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 基本使用

#### 1. 注册实体元数据

```java
@Autowired
private MetadataEngine metadataEngine;

public void registerEntity() {
    // 创建实体元数据
    EntityMetadata entityMetadata = new EntityMetadata();
    entityMetadata.setEntityName("Customer");
    entityMetadata.setDescription("Customer Entity");
    entityMetadata.setBusinessDomain("Sales");
    
    // 添加字段
    Map<String, FieldMetadata> fields = new HashMap<>();
    
    FieldMetadata idField = new FieldMetadata();
    idField.setFieldName("id");
    idField.setFieldType("Long");
    idField.setRequired(true);
    fields.put("id", idField);
    
    FieldMetadata nameField = new FieldMetadata();
    nameField.setFieldName("name");
    nameField.setFieldType("String");
    nameField.setRequired(true);
    fields.put("name", nameField);
    
    // 添加计算字段
    FieldMetadata fullNameField = new FieldMetadata();
    fullNameField.setFieldName("fullName");
    fullNameField.setFieldType("String");
    fullNameField.setCalculated(true);
    fullNameField.setExpression("firstName + ' ' + lastName");
    fields.put("fullName", fullNameField);
    
    entityMetadata.setFields(fields);
    
    // 注册元数据
    metadataEngine.registerEntity(entityMetadata);
}
```

#### 2. 验证数据

```java
@Autowired
private ValidationEngine validationEngine;

public void validateData() {
    Map<String, Object> data = new HashMap<>();
    data.put("id", 1L);
    // name字段缺失，应该验证失败
    
    List<ValidationError> errors = validationEngine.validateEntity("Customer", data);
    if (!errors.isEmpty()) {
        // 处理验证错误
        for (ValidationError error : errors) {
            System.out.println(error.getFieldName() + ": " + error.getMessage());
        }
    }
}
```

#### 3. 计算字段值

```java
@Autowired
private MetadataEngine metadataEngine;

public void calculateFields() {
    Map<String, Object> data = new HashMap<>();
    data.put("firstName", "John");
    data.put("lastName", "Doe");
    
    // 计算所有计算字段
    Map<String, Object> result = metadataEngine.processCalculatedFields("Customer", data);
    
    // 输出计算结果
    System.out.println("Full Name: " + result.get("fullName")); // 应该输出 "John Doe"
}
```

### 配置选项

在application.properties中配置SmartMeta引擎：

```properties
# 基础配置
bone.smartmeta.enabled=true

# AI增强配置
bone.smartmeta.ai.enabled=true
bone.smartmeta.ai.model=default
bone.smartmeta.ai.api-key=your-api-key

# 缓存配置
bone.smartmeta.cache.enabled=true
bone.smartmeta.cache.ttl=3600

# 热加载配置
bone.smartmeta.hot-reload.enabled=true
bone.smartmeta.hot-reload.interval=60

# 日志配置
bone.smartmeta.log.level=INFO
bone.smartmeta.log.file=smartmeta.log
```

## 架构设计

### 系统架构图

```
+-------------------+     +-------------------+     +-------------------+
|                   |     |                   |     |                   |
|   业务应用层      | --> |  Bone SmartMeta   | --> |   数据存储层      |
|                   |     |    核心引擎       |     |                   |
+-------------------+     +-------------------+     +-------------------+
                               |         |
                        +------+         +------+
                        |                       |
               +-------------------+    +-------------------+
               |                   |    |                   |
               |   验证引擎        |    |   转换引擎        |
               |                   |    |                   |
               +-------------------+    +-------------------+
                        |                       |
                        +------+         +------+
                               |         |
                        +-------------------+
                        |                   |
                        |   表达式引擎      |
                        |                   |
                        +-------------------+
```

### 核心组件

1. **MetadataRegistry**: 元数据注册中心，负责元数据的存储和管理
   - 元数据持久化和缓存
   - 元数据版本管理
   - 变更事件发布

2. **MetadataEngine**: 元数据核心引擎，提供元数据操作的高级API
   - 实体元数据CRUD操作
   - 字段元数据管理
   - 业务域分组管理

3. **ValidationEngine**: 验证引擎，负责数据验证和规则执行
   - 实体级验证
   - 字段级验证
   - 自定义验证规则

4. **ExpressionEngine**: 表达式引擎，负责表达式解析和计算
   - SpEL表达式解析
   - 计算字段求值
   - 表达式缓存优化

5. **TransformationEngine**: 转换引擎，负责数据格式转换和映射
   - 数据格式转换
   - 字段映射关系管理
   - 自定义转换器支持

### 数据流程

1. 应用通过MetadataEngine注册实体元数据
2. MetadataRegistry存储元数据并触发变更事件
3. 应用提供数据，ValidationEngine进行验证
4. ExpressionEngine计算动态字段值
5. TransformationEngine进行数据转换和格式化
6. 处理后的数据返回给应用或存储到目标系统

## 最佳实践

### 元数据设计

1. **业务域分组**：为每个业务域创建独立的实体组，避免命名冲突和管理混乱
2. **分层设计**：采用实体继承或组合的方式实现公共字段的复用
3. **字段标准化**：建立统一的字段命名规范，提高元数据的可读性和维护性
4. **计算字段策略**：使用计算字段减少冗余数据，提高数据一致性
5. **验证规则分级**：为不同重要程度的字段设置相应级别的验证规则

### 性能优化

1. **缓存策略**：利用元数据缓存机制，减少元数据查询开销
2. **表达式预编译**：预编译常用的表达式，提高计算性能
3. **批量处理**：对大量数据操作采用批量处理模式
4. **异步处理**：非关键路径的元数据变更采用异步处理方式

### 运维管理

1. **元数据版本控制**：建立元数据变更的版本控制机制
2. **变更审计**：记录元数据变更的审计日志，便于问题追溯
3. **定期备份**：定期备份元数据配置，防止数据丢失
4. **灰度发布**：重要的元数据变更采用灰度发布策略

## 高级特性

### 元数据版本管理
Bone SmartMeta支持元数据的完整版本控制，包括：
- 版本历史记录
- 版本回滚
- 差异比较
- 变更审批流程

### 多环境支持
- 开发环境、测试环境和生产环境的元数据隔离
- 环境间元数据同步机制
- 环境特定配置支持

### 权限管理
- 基于角色的访问控制
- 元数据操作权限精细控制
- 变更审批权限管理

### 扩展性设计
- 自定义元数据处理器接口
- 插件化架构支持
- 事件驱动设计，支持自定义监听器

## 部署与运维

### 部署架构

#### 单实例部署
适用于开发和测试环境：
```bash
# 使用Spring Boot内置Tomcat启动
java -jar bone-smartmeta-starter.jar

# 或使用外部应用服务器
# 将构建产物部署到Tomcat、Jetty等容器
```

#### 集群部署
适用于生产环境：
- 多实例部署，通过负载均衡分发请求
- 使用Redis作为分布式缓存
- 配置数据库主从复制确保数据可靠性

### 配置管理

#### 环境变量配置
```bash
# 设置必要的环境变量
export BONE_SMARTMETA_DB_URL=jdbc:mysql://localhost:3306/smartmeta
export BONE_SMARTMETA_DB_USERNAME=admin
export BONE_SMARTMETA_DB_PASSWORD=password
export BONE_SMARTMETA_REDIS_URL=redis://localhost:6379/0

# 启动应用
java -jar bone-smartmeta-starter.jar
```

#### 配置文件优先级
1. 命令行参数
2. 环境变量
3. application-{profile}.yml/properties
4. application.yml/properties

### 监控与日志

#### 健康检查端点
```
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
```

#### 日志配置
推荐配置ELK或Prometheus+Grafana进行日志和指标监控：
- 系统日志：记录核心引擎运行状态
- 业务日志：记录元数据操作审计
- 性能日志：记录关键操作的性能指标

## 常见问题解答

### Q: 如何处理元数据版本冲突？
A: Bone SmartMeta采用乐观锁机制处理并发更新，当检测到版本冲突时会抛出异常，应用可以捕获并实现冲突解决策略，如自动合并或人工干预。

### Q: 支持哪些数据库？
A: 默认支持MySQL、PostgreSQL、Oracle等主流关系型数据库，可通过配置数据源适配器支持其他类型的存储。

### Q: 如何实现自定义验证规则？
A: 实现ValidationRule接口并注册到ValidationEngine中，或通过表达式引擎定义复杂的验证规则。

### Q: 性能如何保障？
A: 通过多级缓存策略、表达式预编译、异步处理和集群部署等机制保障系统性能，适合高并发场景。

### Q: 元数据如何与实际数据存储同步？
A: 提供事件监听器接口，可在元数据变更时触发相应的数据库结构调整操作，或通过定时任务进行同步。

## 开发环境要求

- JDK 1.8+
- Maven 3.6+
- Spring Boot 2.7.x
- 数据库：MySQL 8.0+ 或 PostgreSQL 11.0+

## 构建和测试

```bash
# 构建项目
mvn clean install

# 运行测试
mvn test

# 运行集成测试
mvn verify

# 生成代码覆盖率报告
mvn jacoco:report
```

## 贡献指南

我们欢迎社区贡献！请按照以下流程参与：

1. Fork项目仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开Pull Request

## 许可证

本项目采用Apache 2.0许可证。详情请参阅项目根目录下的LICENSE文件。