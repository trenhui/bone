# 扩展点框架配置管理重构说明

## 问题背景

在重构前，扩展点框架的配置管理存在以下问题：

1. **配置分散**：配置信息同时存在于多个地方
   - `ExtensionProperties` - Spring Boot配置属性类
   - `ExtensionConfigManager` - 传统配置管理器
   - `DefaultExtPointRouter` - 直接通过@Value注解获取配置
   - `ExtensionConfigValidator` - 硬编码默认值

2. **配置重复**：相同的配置（如缓存过期时间、最大大小）在多处定义

3. **配置冲突**：多个配置类可能提供冲突的配置值

4. **维护困难**：修改配置需要同时更新多个文件

5. **缺少统一入口**：没有一个统一的配置管理入口

## 重构目标

1. **统一配置源**：使用`ExtensionProperties`作为唯一的配置源
2. **适配现有代码**：保持向后兼容性，同时鼓励使用新的配置方式
3. **简化维护**：减少配置重复，使配置更易于维护
4. **统一管理入口**：提供清晰的配置管理接口

## 重构内容

### 1. 配置源统一

- **`ExtensionProperties`** - 作为唯一的配置源，定义所有扩展点相关配置
  - 缓存配置（CacheConfig）
  - 扫描配置（ScanConfig）
  - 事件配置（EventConfig）
  - 路由配置（RouterConfig）

### 2. 适配层实现

- **`ExtensionConfigManager`** - 改造为适配层
  - 优先使用`ExtensionProperties`中的配置
  - 保留向后兼容的API
  - 标记为`@Component`，支持自动注入

### 3. 配置验证统一

- **`ExtensionConfigValidator`** - 更新为使用`ExtensionProperties`
  - 注入`ExtensionProperties`
  - 优先使用配置属性中的值
  - 移除硬编码的默认值

### 4. 统一自动配置

- **`UnifiedExtPointAutoConfiguration`** - 新增统一自动配置类
  - 整合所有扩展点框架的配置
  - 处理`@EnableExtPoints`注解属性与配置属性的合并
  - 确保配置的一致性

### 5. 移除直接配置依赖

- **`DefaultExtPointRouter`** - 移除直接@Value注入
  - 注入`ExtensionProperties`
  - 通过方法获取配置值
  - 保持向后兼容的API

## 配置使用指南

### 1. 通过Spring配置文件配置

```yaml
# application.yaml 或 application.properties
bone:
  extension:
    enabled: true
    cache:
      enabled: true
      expire-time: 300000  # 5分钟，单位毫秒
      max-size: 1000
    router:
      weighted-routing-enabled: true
      gray-publish-enabled: false
    scan:
      base-packages: com.example.extensions
    events:
      enabled: true
```

### 2. 通过注解配置

```java
@SpringBootApplication
@EnableExtPoints(
    basePackages = "com.example.extensions",
    enableAutoScan = true,
    enableCache = true,
    enableEvents = true
)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. 编程方式访问配置

#### 推荐方式（直接使用ExtensionProperties）

```java
@Service
public class MyService {
    
    @Autowired
    private ExtensionProperties extensionProperties;
    
    public void doSomething() {
        // 访问缓存配置
        boolean cacheEnabled = extensionProperties.getCache().isEnabled();
        long expireTime = extensionProperties.getCache().getExpireTime();
        int maxSize = extensionProperties.getCache().getMaxSize();
        
        // 访问路由配置
        boolean weightedRoutingEnabled = extensionProperties.getRouter().isWeightedRoutingEnabled();
    }
}
```

#### 兼容方式（使用ExtensionConfigManager）

```java
@Service
public class LegacyService {
    
    @Autowired
    private ExtensionConfigManager configManager;
    
    public void doSomething() {
        // 获取缓存配置
        boolean cacheEnabled = configManager.isExtPointCacheEnabled("myExtPoint");
        long expireTime = configManager.getExtPointCacheExpireTime("myExtPoint");
    }
}
```

## 向后兼容性

为确保向后兼容，重构保留了以下内容：

1. **原有API保持不变**：所有原有方法签名保持不变
2. **配置文件格式兼容**：支持原有配置文件格式
3. **注解属性兼容**：`@EnableExtPoints`注解的所有属性保持不变

## 最佳实践

1. **新代码应直接使用`ExtensionProperties`**
2. **现有代码可继续使用`ExtensionConfigManager`**，但建议逐步迁移到`ExtensionProperties`
3. **配置统一放在Spring配置文件中**，避免硬编码
4. **不要在代码中直接修改配置值**，使用配置文件或环境变量进行配置

## 常见问题解答

### Q: 如何获取扩展点特定的配置？

A: 使用`ExtensionConfigManager.getExtPointConfig()`方法，它会优先查找扩展点特定配置，如果不存在则返回全局配置。

### Q: 配置变更后需要重启应用吗？

A: 对于Spring Boot配置，支持动态刷新（使用`@RefreshScope`）；对于`ExtensionConfigManager`中的缓存配置，可以调用`clearCache()`方法重新加载。

### Q: 如何禁用缓存？

A: 在配置文件中设置`bone.extension.cache.enabled=false`，或在`@EnableExtPoints`注解中设置`enableCache=false`。

## 总结

通过本次重构，扩展点框架的配置管理更加统一、清晰和易于维护。团队应逐步迁移到使用`ExtensionProperties`，并通过Spring配置文件管理所有配置，以充分利用此次重构的成果。

---

*文档更新时间：2023-10-01*
*版本：1.0.0*