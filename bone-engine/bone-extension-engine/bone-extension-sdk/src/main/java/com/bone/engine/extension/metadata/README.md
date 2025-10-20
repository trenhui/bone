# 扩展点元数据系统

## 概述

本模块提供了扩展点元数据管理功能，支持通过元数据描述信息可视化平台展示扩展点、扩展实现及其调用关系，以及可视化配置扩展点的路由规则。

## 核心功能

1. **元数据收集与存储**：自动收集扩展点接口和实现类的元数据信息
2. **REST API接口**：提供查询、刷新、导入导出等元数据管理API
3. **优先级路由机制**：支持基于优先级的扩展实现选择
4. **可视化支持**：为可视化平台提供完整的元数据支持

## 元数据属性说明

### ExtPoint注解元数据

| 属性名 | 类型 | 说明 | 默认值 |
|--------|------|------|--------|
| description | String | 扩展点描述信息 | "" |
| owner | String | 扩展点负责人/团队 | "" |
| documentationUrl | String | 文档链接 | "" |
| category | String | 分类 | "default" |
| tags | String[] | 标签列表 | {} |
| deprecated | boolean | 是否废弃 | false |
| deprecatedSince | String | 废弃版本 | "" |
| replacement | String | 替代方案 | "" |

### Extension注解元数据

| 属性名 | 类型 | 说明 | 默认值 |
|--------|------|------|--------|
| description | String | 扩展实现描述 | "" |
| author | String | 作者/团队 | "" |
| isDefault | boolean | 是否默认实现 | false |
| isRecommended | boolean | 是否推荐实现 | false |
| priority | int | 优先级，值越小优先级越高 | 0 |
| dependencies | String[] | 依赖的其他扩展实现 | {} |
| properties | String[] | 配置属性，格式为"key=value" | {} |

## 使用方法

### 1. 定义扩展点接口

```java
@ExtPoint(
    description = "支付处理扩展点，支持多种支付方式的实现",
    owner = "payment-team",
    documentationUrl = "https://wiki.example.com/payment-extpoint",
    category = "payment",
    tags = {"payment", "transaction", "finance"}
)
public interface PaymentExtPoint {
    PaymentResult processPayment(PaymentRequest request);
    ValidationResult validatePayment(PaymentRequest request);
    PaymentStatus getPaymentStatus(String paymentId);
}
```

### 2. 实现扩展点

```java
@Component
@Extension(
    // 路由配置
    tenantCode = "alipay-tenant",
    bizCode = "ecommerce",
    useCase = "payment",
    scenario = "online",
    
    // 元数据属性
    description = "支付宝在线支付实现，支持标准支付流程",
    author = "payment-team@example.com",
    isDefault = false,
    isRecommended = true,
    priority = 10,
    
    // 依赖和配置
    dependencies = {"com.example.extension.log.LoggingExtension"},
    properties = {
        "alipay.gateway.url=https://openapi.alipay.com/gateway.do",
        "alipay.app.id=2021000000000000"
    }
)
public class AlipayPaymentExtension implements PaymentExtPoint {
    // 实现方法...
}
```

### 3. 访问元数据API

元数据系统提供了以下REST API端点：

- **GET /api/ext-point/metadata** - 获取所有扩展点元数据
- **GET /api/ext-point/metadata/{interfaceName}** - 获取特定扩展点元数据
- **GET /api/ext-point/metadata/search** - 根据条件查询扩展点
- **GET /api/ext-point/metadata/{interfaceName}/implementations/{implClassName}** - 获取扩展实现元数据
- **POST /api/ext-point/metadata/refresh** - 刷新元数据缓存
- **PUT /api/ext-point/metadata/{interfaceName}/implementations/{implClassName}/routing** - 更新路由配置
- **GET /api/ext-point/metadata/stats** - 获取使用统计
- **GET /api/ext-point/metadata/export** - 导出元数据
- **POST /api/ext-point/metadata/import** - 导入元数据

## 优先级路由机制

扩展点路由机制现在支持基于优先级的选择：

1. 首先根据`priority`属性排序，值越小优先级越高
2. 优先级相同时，根据匹配条件数量决定优先级
3. 通过`isDefault`属性直接标记默认实现

## 最佳实践

1. **完整的元数据描述**：为每个扩展点和扩展实现提供详细的元数据描述，便于管理和维护
2. **合理设置优先级**：根据业务需求合理设置扩展实现的优先级
3. **明确默认实现**：对于核心功能，明确指定默认实现
4. **版本管理**：使用`deprecated`、`deprecatedSince`和`replacement`属性管理扩展点的生命周期
5. **依赖声明**：明确声明扩展实现之间的依赖关系，便于理解调用链

## 可视化平台集成

元数据系统设计时充分考虑了与可视化平台的集成需求：

1. 提供完整的元数据模型，包含展示所需的所有信息
2. 支持查询、过滤、排序等操作
3. 提供导入导出功能，便于配置管理
4. 支持在线更新路由配置

可视化平台可以通过调用REST API接口获取所有必要的元数据信息，实现扩展点、扩展实现及调用关系的可视化展示，以及路由规则的可视化配置。

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| bone.extension.metadata.enabled | boolean | true | 是否启用元数据功能 |
| bone.extension.metadata.cache.enabled | boolean | true | 是否启用元数据缓存 |
| bone.extension.metadata.cache.size | int | 1000 | 元数据缓存大小 |

## 注意事项

1. 元数据收集是在应用启动时完成的，运行时修改代码不会自动更新元数据，需要调用刷新API
2. 元数据存储在内存中，应用重启后会重新收集
3. 优先级路由机制仅在有多个匹配的实现时才会生效