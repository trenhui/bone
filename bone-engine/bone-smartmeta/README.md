# Bone SmartMeta 企业级元数据操作系统

Bone SmartMeta 是一个融合了Salesforce、Workday和Coupa最佳实践的企业级元数据操作系统，提供智能元数据管理、动态数据模型、AI增强功能等核心能力。

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
- AI元数据支持
- 智能字段建议
- 自动元数据分析

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

### 核心组件

1. **MetadataRegistry**: 元数据注册中心，负责元数据的存储和管理
2. **MetadataEngine**: 元数据核心引擎，提供元数据操作的高级API
3. **ValidationEngine**: 验证引擎，负责数据验证和规则执行
4. **ExpressionEngine**: 表达式引擎，负责表达式解析和计算
5. **TransformationEngine**: 转换引擎，负责数据格式转换和映射

### 数据流程

1. 应用通过MetadataEngine注册实体元数据
2. MetadataRegistry存储元数据并触发变更事件
3. 应用提供数据，ValidationEngine进行验证
4. ExpressionEngine计算动态字段值
5. TransformationEngine进行数据转换和格式化

## 最佳实践

1. 为每个业务域创建独立的实体组
2. 使用计算字段减少冗余数据
3. 为重要实体添加完整的验证规则
4. 利用元数据变更事件进行缓存刷新
5. 定期备份元数据配置

## 开发环境要求

- JDK 1.8+
- Maven 3.6+
- Spring Boot 2.7.x

## 构建和测试

```bash
# 构建项目
mvn clean install

# 运行测试
mvn test
```

## 许可证

本项目采用Apache 2.0许可证。