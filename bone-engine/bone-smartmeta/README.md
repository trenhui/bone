# Bone SmartMeta 企业级元数据操作系统

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://img.shields.io/badge/build-passing-green)](https://github.com/bone-engine/bone-smartmeta)
[![Version](https://img.shields.io/badge/version-1.0.0--SNAPSHOT-blue.svg)]()

Bone SmartMeta 是一个融合了Salesforce、Workday和Coupa最佳实践的企业级元数据操作系统，提供智能元数据管理、动态数据模型、AI增强功能等核心能力。通过动态定义和管理元数据，实现业务模型的敏捷迭代与数据一致性保障，赋能企业快速响应业务变化。

## 项目价值

- **业务敏捷性**: 支持业务模型的动态调整，无需修改代码即可适配业务变化，实现低代码/无代码业务建模
- **数据一致性**: 集中化元数据管理，确保跨系统数据模型的一致性，避免数据孤岛
- **开发效率提升**: 减少样板代码，加速应用开发和迭代周期，降低系统维护成本
- **系统集成能力**: 提供标准化的数据访问和转换机制，简化系统集成和数据交换
- **AI赋能**: 集成AI能力，提供智能数据分析和元数据优化建议，提升决策质量
- **多租户支持**: 内置多租户隔离机制，支持大规模企业级应用场景

## 核心功能

### 1. 智能元数据管理
- 实体元数据注册、查询、更新和删除
- 按业务域分组管理元数据
- 支持计算字段和虚拟字段
- 元数据缓存和变更通知机制
- 多版本元数据管理和回滚
- 元数据导入导出和差异比较

### 2. 数据验证引擎
- 实体数据验证
- 必填字段验证
- 字段类型验证
- 自定义验证规则支持
- 业务规则引擎集成
- 批量数据验证和错误报告

### 3. 表达式引擎
- SpEL表达式支持
- 动态字段计算
- 条件表达式评估
- 表达式缓存优化
- 安全表达式执行沙箱
- 表达式调试和性能分析

### 4. 数据转换引擎
- 实体数据格式转换
- 字段映射和转换
- 数据过滤和排除
- 自定义转换器支持
- 数据迁移和同步
- 批量转换处理

### 5. AI增强功能
- AI元数据支持：智能生成和优化元数据定义
- 智能字段建议：基于业务场景提供字段设计建议
- 自动元数据分析：识别潜在的元数据问题和优化机会
- 预测性元数据管理：基于历史数据分析元数据变更趋势
- 自然语言查询转换为表达式
- 异常模式检测和智能告警

### 6. 动态对象管理
- 业务人员可视化建模界面
- RESTful API动态创建和管理模型
- 运行时模型热更新
- 模型继承和组合机制
- 自定义业务逻辑注入
- 字段级权限控制
- 动态UI渲染支持

## 业务人员建模指南

### 动态添加领域对象流程

Bone SmartMeta提供了两种主要方式让业务人员动态添加领域对象：

#### 1. Web可视化建模界面

业务人员可以通过直观的Web界面进行模型设计：

1. **创建新模型**：
   - 访问建模控制台：`/admin/model-designer`
   - 点击「新建模型」按钮
   - 输入模型名称、显示标签和描述
   - 选择所属业务域

2. **添加字段**：
   - 点击「添加字段」按钮
   - 配置字段属性（名称、类型、标签、约束等）
   - 支持的字段类型：字符串、数字、日期、布尔值、JSON、枚举等
   - 设置字段验证规则和默认值

3. **配置业务规则**：
   - 定义必填字段和唯一性约束
   - 设置计算字段表达式
   - 配置业务验证规则

4. **保存并部署**：
   - 点击「保存」按钮保存模型定义
   - 点击「部署」按钮使模型立即生效
   - 系统自动处理元数据注册和缓存更新

#### 2. RESTful API方式

业务人员也可以通过调用API动态创建模型：

```bash
# 创建采购订单模型示例
curl -X POST http://localhost:8080/api/dynamic-models \
  -H "Content-Type: application/json" \
  -d '{
    "name": "PurchaseOrder",
    "label": "采购订单",
    "description": "企业采购订单模型",
    "domain": "procurement",
    "fields": {
      "orderNumber": {
        "type": "string",
        "label": "订单编号",
        "required": true,
        "maxLength": 50
      },
      "supplierId": {
        "type": "string",
        "label": "供应商ID",
        "required": true
      },
      "amount": {
        "type": "double",
        "label": "订单金额",
        "required": true,
        "minValue": 0
      },
      "taxAmount": {
        "type": "double",
        "label": "税额",
        "calculated": true,
        "expression": "amount * 0.13"
      }
    }
  }'
```

### 7. 多租户支持
- 租户隔离的数据模型
- 租户特定的元数据扩展
- 租户级权限控制
- 跨租户数据安全策略

### 8. 工作流集成
- 实体生命周期管理
- 状态机定义和执行
- 业务流程自动化
- 事件驱动的工作流触发

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

### 元数据引擎执行机制

#### 1. 元数据注册与加载流程

```
业务人员/Web UI/API → 模型定义 → DynamicModelManager → MetadataEngine → MetadataRegistry → 元数据存储
                                                                  ↓
                                                            元数据缓存 → 事件发布
```

#### 2. 动态CRUD操作流程

```
应用请求 → DynamicModelController → DynamicModelDataService → 数据验证 → 
MetadataEngine处理 → 表达式计算 → 数据存储 → 返回结果
```

#### 3. 业务规则执行流程

```
数据输入 → ValidationEngine → 基础验证 → 表达式规则验证 → 自定义规则验证 → 
验证结果 → 错误处理或通过
```

## 与主流SaaS平台对比

| 特性 | Bone SmartMeta | Salesforce | Workday | Coupa |
|------|----------------|------------|---------|-------|
| 元数据驱动设计 | ✅ | ✅ | ✅ | ✅ |
| 多租户支持 | ✅ | ✅ | ✅ | ✅ |
| 自定义对象扩展 | ✅ | ✅ | ✅ | ✅ |
| 计算字段支持 | ✅ | ✅ | ✅ | ⚠️ 有限支持 |
| 业务规则引擎 | ✅ | ✅ | ✅ | ✅ |
| AI增强功能 | ✅ | ✅ | ✅ | ⚠️ 部分支持 |
| 开源可定制 | ✅ | ❌ | ❌ | ❌ |
| 部署灵活性 | ✅ | ❌ | ❌ | ❌ |
| 性能优化控制 | ✅ | ⚠️ 有限控制 | ⚠️ 有限控制 | ⚠️ 有限控制 |
| 与现有系统集成 | ✅ | ⚠️ 需API | ⚠️ 需API | ⚠️ 需API |

## 实际应用场景

### 场景一：采购管理系统扩展

某企业需要为采购系统添加新的供应商评估模型，通过Bone SmartMeta实现：

1. 业务人员在建模界面创建「SupplierEvaluation」模型
2. 定义评估指标字段（质量评分、交付及时性、成本效益等）
3. 设置计算字段自动计算总分：`qualityScore * 0.4 + deliveryScore * 0.3 + costScore * 0.3`
4. 配置业务规则：总分低于60分自动触发审核流程
5. 系统自动生成API和存储结构
6. 前端自动适配新模型的CRUD界面

### 场景二：多租户SaaS应用

SaaS供应商需要支持不同客户的定制化需求：

1. 基础模型定义通用字段和业务逻辑
2. 租户A添加特定的合规相关字段
3. 租户B添加行业特定的扩展字段
4. 系统确保租户数据隔离和元数据隔离
5. 变更不影响其他租户的使用

### 场景三：数据治理和主数据管理

企业需要统一管理跨系统的客户主数据：

1. 通过Bone SmartMeta定义客户实体元数据
2. 配置数据验证规则确保数据质量
3. 设置数据转换引擎实现与各系统的数据映射
4. 利用变更通知机制实现数据同步
5. 通过AI分析识别数据质量问题并提供优化建议

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

### 元数据驱动设计最佳实践

#### 1. 元数据建模规范
- **业务域分组**：按照业务领域对模型进行分组，如财务域、采购域、人力资源域等，提高可维护性
- **命名规范**：
  - 模型名称：使用PascalCase，如`PurchaseOrder`
  - 字段名称：使用camelCase，如`orderNumber`
  - 显示标签：使用中文，如`订单编号`
  - API路径：使用kebab-case，如`/purchase-orders`
- **版本控制**：
  - 实现元数据变更版本历史
  - 支持模型版本的比较和回滚
  - 记录变更人和变更时间

#### 2. 性能优化策略
- **字段索引**：对常用查询字段设置索引
- **缓存策略**：
  - 元数据缓存：本地缓存+分布式缓存
  - 计算字段结果缓存
  - 表达式编译结果缓存
- **数据分片**：按业务域或时间维度进行数据分片
- **查询优化**：限制关联查询深度，避免N+1问题

#### 3. 数据质量保障
- **验证规则配置**：
  - 字段级验证：类型、长度、格式、范围等
  - 记录级验证：业务规则、跨字段验证
  - 关联级验证：引用完整性检查
- **数据治理流程**：
  - 数据质量监控
  - 异常数据处理流程
  - 数据标准化规则

#### 4. 扩展性设计
- **模型继承**：
  - 基础模型定义通用字段和规则
  - 业务模型继承并扩展特定字段
- **组合模式**：
  - 使用组合而非继承实现复杂业务对象
  - 配置字段引用关系
- **插件机制**：
  - 支持自定义验证器
  - 支持自定义转换器
  - 支持自定义表达式函数

### 部署最佳实践

1. **环境隔离**：开发、测试、生产环境的严格隔离
2. **变更管理**：元数据变更的审批和发布流程
3. **监控告警**：关键指标监控和异常告警
4. **备份恢复**：定期备份和灾难恢复机制
5. **性能调优**：JVM参数、数据库连接池等配置优化
6. **高可用设计**：
   - 元数据引擎集群部署
   - 缓存一致性保障
   - 异步任务处理

## 开发人员API参考

### MetadataEngine API

```java
// 注册实体元数据
MetadataEngine.registerEntity(entityMetadata);

// 获取实体元数据
EntityMetadata metadata = MetadataEngine.getEntityMetadata(entityName);

// 更新实体元数据
MetadataEngine.updateEntity(entityMetadata);

// 删除实体元数据
MetadataEngine.deleteEntity(entityName);

// 刷新元数据缓存
MetadataEngine.refreshCache();
```

### ValidationEngine API

```java
// 验证单个实体
ValidationResult result = ValidationEngine.validate(entity, entityMetadata);

// 批量验证实体
List<ValidationResult> results = ValidationEngine.batchValidate(entities, entityMetadata);

// 验证单个字段
ValidationResult fieldResult = ValidationEngine.validateField(fieldName, fieldValue, fieldMetadata);
```

### ExpressionEngine API

```java
// 评估表达式
Object result = ExpressionEngine.evaluate(expression, context);

// 预编译表达式
CompiledExpression compiledExpr = ExpressionEngine.compile(expression);

// 执行预编译表达式
Object result = ExpressionEngine.execute(compiledExpr, context);

// 清理表达式缓存
ExpressionEngine.clearCache();
```

### DynamicModelDataService API

```java
// 创建实体
Map<String, Object> entity = dynamicModelDataService.create(entityName, data);

// 获取实体
Map<String, Object> entity = dynamicModelDataService.get(entityName, id);

// 更新实体
Map<String, Object> entity = dynamicModelDataService.update(entityName, id, data);

// 删除实体
boolean deleted = dynamicModelDataService.delete(entityName, id);

// 分页查询
Page<Map<String, Object>> page = dynamicModelDataService.query(entityName, query, pageable);

// 批量操作
List<Map<String, Object>> results = dynamicModelDataService.batchCreate(entityName, batchData);
```

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

### Q: 如何实现与现有系统的集成？
A: Bone SmartMeta提供丰富的集成能力：
- RESTful API接口，支持标准的CRUD操作
- Webhook机制，支持事件驱动集成
- 数据转换引擎，支持多种数据格式转换
- 提供Java客户端SDK，便于Java应用集成

### Q: 如何处理复杂的业务规则？
A: 系统支持多种方式实现复杂业务规则：
- 基于SpEL的表达式规则，适用于简单到中等复杂度的规则
- 自定义验证器插件，适用于复杂业务逻辑
- 与外部规则引擎集成，支持决策表、规则流等高级特性
- 事件驱动架构，支持异步业务规则处理

## 未来发展规划

### 近期规划（1-3个月）
- 增强AI辅助建模功能
- 完善低代码/无代码建模界面
- 优化元数据导入导出功能
- 提升性能和扩展性

### 中期规划（3-6个月）
- 引入高级数据治理功能
- 增强跨系统数据集成能力
- 完善事件驱动架构
- 支持更多数据库类型

### 长期规划（6-12个月）
- 构建元数据市场
- 提供行业模板库
- 增强AI预测分析能力
- 构建开放生态系统

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