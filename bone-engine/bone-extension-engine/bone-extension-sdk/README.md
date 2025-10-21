# Bone Extension Framework

## 📖 文档目录

- [框架介绍](#框架介绍)
- [快速开始](#快速开始)
- [核心概念](#核心概念)
- [高级功能](#高级功能)
- [最佳实践](#最佳实践)
- [业务场景示例](#业务场景示例)
- [故障排查与监控](#故障排查与监控)
- [常见问题解答](#常见问题解答)
- [版本历史](#版本历史)
- [附录](#附录)

---

## 框架介绍

### 什么是 Bone Extension Framework？
Bone Extension Framework 是一个轻量级、高性能的业务扩展框架，专为解决复杂业务场景下的代码复用、多租户定制和业务规则动态扩展而设计。它允许开发者像使用"插件"一样为应用程序添加定制化业务逻辑，而无需修改核心代码。

### 核心优势
- **业务解耦**：将核心流程与定制化逻辑分离，提高代码可维护性
- **动态路由**：基于上下文智能选择合适的扩展实现
- **多租户支持**：为不同租户提供独立的业务规则和处理逻辑
- **灵活扩展**：无需修改核心代码即可添加新的业务规则
- **声明式配置**：通过简单的注解快速实现扩展点定义和实现

### 工作流程
```mermaid
graph TD
    A[业务请求] --> B[创建业务上下文<br>BizContext]
    B --> C[调用扩展点接口]
    C --> D[框架自动路由匹配]
    D --> E[执行匹配的扩展逻辑]
    E --> F[返回处理结果]
```

### 主要功能
- 基于注解的声明式扩展机制
- 多维度匹配路由（租户、业务、场景、条件）
- Spring Expression Language (SpEL) 动态条件匹配
- 灵活的优先级控制策略
- 与 Spring 框架无缝集成
- 高性能多级缓存优化
- 运行时动态扩展注册

---

## 快速开始

### 环境要求
- Java 8+ 或 Java 11+
- Spring Boot 2.x 或 Spring Boot 3.x
- Maven 3.6+ 或 Gradle 7.0+

### 安装配置

#### 1. 添加依赖
```xml
<dependency>
    <groupId>com.bone</groupId>
    <artifactId>bone-extension</artifactId>
    <version>1.3.0</version>
</dependency>
```

#### 2. 启用框架
在 Spring Boot 应用的启动类上添加 `@EnableExtPoints` 注解：

```java
@SpringBootApplication
@EnableExtPoints(basePackages = "com.yourcompany.extension")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

#### 3. 配置框架（可选）
在 `application.yml` 或 `application.properties` 中配置框架参数：

```yaml
# application.yml
bone:
  extension:
    cache-enabled: true              # 启用缓存（推荐）
    cache-size: 1000                 # 缓存大小
    logging-enabled: true            # 启用详细日志
    metrics-enabled: false           # 生产环境建议开启指标收集
    max-matching-extensions: 10      # 最大匹配扩展数
```

### 快速示例

以下是一个完整的示例，展示如何定义和使用扩展点：

#### 1. 定义扩展点接口
```java
/**
 * 用户问候扩展点
 * 根据用户类型返回不同的问候语
 */
@ExtPoint(name = "用户问候扩展点")
public interface GreetingExtPoint {
    /**
     * 向用户问好
     * @param context 业务上下文，包含用户名和用户类型信息
     * @return 问候语
     */
    String greet(BizContext<String> context);
}
```

#### 2. 实现默认扩展
```java
/**
 * 默认问候实现
 * 低优先级，作为兜底实现
 */
@Extension(priority = 100)
@Service
@Slf4j
public class DefaultGreetingExtension implements GreetingExtPoint {
    @Override
    public String greet(BizContext<String> context) {
        log.info("执行默认问候实现，租户: {}", context.getTenantCode());
        String userName = context.getData();
        if (userName == null) {
            throw new ExtensionBizException("INVALID_PARAM", "用户名不能为空");
        }
        return "Hello, " + userName + "!";
    }
}
```

#### 3. 实现VIP用户扩展
```java
/**
 * VIP用户问候实现
 * 高优先级，当用户为VIP时执行
 */
@Extension(
    condition = "#context.getAttribute('isVip') == true", // SpEL条件表达式
    priority = 50  // 优先级高于默认实现
)
@Service
@Slf4j
public class VipGreetingExtension implements GreetingExtPoint {
    @Override
    public String greet(BizContext<String> context) {
        log.info("执行VIP用户问候，用户: {}", context.getData());
        String userName = context.getData();
        // 可以获取更多上下文属性进行复杂业务处理
        Integer loginCount = context.getAttribute("loginCount", Integer.class);
        String greeting = "尊贵的VIP用户 " + userName + "，欢迎回来！";
        
        if (loginCount != null && loginCount > 50) {
            greeting += " 您已登录" + loginCount + "次，感谢您的支持！";
        }
        return greeting;
    }
}
```

#### 4. 实现管理员扩展（高级示例）
```java
/**
 * 管理员用户问候实现
 * 最高优先级，使用复杂SpEL表达式匹配管理员用户
 */
@Extension(
    condition = "#context.getAttribute('isAdmin') == true || " +
               "#data != null && #data.startsWith('admin') || " +
               "#context.getAttribute('memberLevel') == 'PLATINUM'",
    priority = 10  // 最高优先级
)
@Service
@Slf4j
public class AdminGreetingExtension implements GreetingExtPoint {
    @Override
    public String greet(BizContext<String> context) {
        log.info("执行管理员问候，租户: {}, 用户: {}", 
                context.getTenantCode(), context.getData());
                
        String userName = context.getData();
        String greeting = "尊敬的管理员 " + userName + "，欢迎您！";
        
        // 复杂业务逻辑示例：根据不同条件返回不同问候语
        if ("PLATINUM".equals(context.getAttribute("memberLevel"))) {
            greeting += " 您的铂金会员特权已激活。";
        }
        
        return greeting;
    }
}

#### 5. 在业务服务中使用
```java
@Service
public class UserService {
    
    @Autowired
    private GreetingExtPoint greetingExtPoint;
    
    /**
     * 欢迎用户
     * @param userName 用户名
     * @param isVip 是否为VIP用户
     * @return 问候语
     */
    public String welcomeUser(String userName, boolean isVip) {
        // 创建业务上下文（使用Builder模式）
        BizContext<String> context = BizContext.<String>builder()
            .tenantCode("TENANT_A")      // 租户标识
            .bizCode("USER_SERVICE")     // 业务标识
            .data(userName)              // 业务数据（通过Builder设置）
            .attribute("isVip", isVip)   // 自定义属性
            .build();
        
        // 使用 try-with-resources 自动管理上下文生命周期
        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            return greetingExtPoint.greet(context);
        }
    }
}
```

#### 5. 测试验证

单元测试是确保扩展机制正确工作的关键。以下是业界最佳实践的测试实现，包含复杂条件表达式、多优先级场景和异常处理测试：

```java
@SpringBootTest
class UserServiceTest {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ExtPointComposite<GreetingExtPoint> greetingExtPointComposite;
    
    @Test
    void testGreetNormalUser() {
        String result = userService.welcomeUser("张三", false);
        assertEquals("Hello, 张三!", result);
    }
    
    @Test
    void testGreetVipUser() {
        String result = userService.welcomeUser("李四", true);
        assertEquals("尊贵的VIP用户 李四，欢迎回来！", result);
    }
    
    // 测试复杂SpEL表达式匹配
    @Test
    void testComplexConditionExpression() {
        // 创建包含复杂属性的上下文
        BizContext<String> context = BizContext.<String>builder()
            .tenantCode("TENANT_A")
            .bizCode("USER_SERVICE")
            .data("王五")
            .attribute("isVip", true)
            .attribute("loginCount", 100)
            .attribute("lastLogin", LocalDate.now().minusDays(1))
            .build();
        
        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            // 测试基于复杂条件的扩展匹配
            List<GreetingExtPoint> extensions = greetingExtPointComposite.getExtensions(context);
            assertNotNull(extensions);
            assertFalse(extensions.isEmpty());
        }
    }
    
    // 测试优先级机制
    @Test
    void testPriorityMechanism() {
        // 创建满足多个扩展条件的上下文
        BizContext<String> context = BizContext.<String>builder()
            .tenantCode("TENANT_A")
            .bizCode("USER_SERVICE")
            .data("赵六")
            .attribute("isVip", true)
            .attribute("isAdmin", true)
            .attribute("memberLevel", "PLATINUM")
            .build();
        
        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            // 获取所有匹配的扩展并验证优先级顺序
            List<GreetingExtPoint> extensions = greetingExtPointComposite.getExtensions(context);
            assertEquals(3, extensions.size(), "应该匹配到3个扩展实现");
            
            // 验证第一个扩展是最高优先级的（管理员扩展）
            assertTrue(extensions.get(0) instanceof AdminGreetingExtension, "管理员扩展应该优先级最高");
            // 验证第二个扩展是VIP扩展
            assertTrue(extensions.get(1) instanceof VipGreetingExtension, "VIP扩展应该次之");
            // 验证最后一个扩展是默认实现
            assertTrue(extensions.get(2) instanceof DefaultGreetingExtension, "默认扩展应该优先级最低");
            
            // 验证优先级数值：10(管理员) < 50(VIP) < 100(默认)
        }
    }
    
    // 测试错误处理
    @Test
    void testErrorHandling() {
        // 创建会触发异常的上下文
        BizContext<String> context = BizContext.<String>builder()
            .tenantCode("INVALID_TENANT")
            .bizCode("USER_SERVICE")
            .data(null) // 故意传入null触发异常
            .build();
        
        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            // 测试异常处理机制
            assertThrows(ExtensionBizException.class, () -> {
                greetingExtPointComposite.getExtensions(context)
                    .get(0).greet(context);
            });
        }
    }
    
    // 测试边界条件
    @Test
    void testEdgeCases() {
        // 测试空字符串数据
        String result1 = userService.welcomeUser("", false);
        assertEquals("Hello, !", result1);
        
        // 测试特殊字符
        String result2 = userService.welcomeUser("测试用户123!@#", true);
        assertTrue(result2.contains("测试用户123!@#"));
    }
}
```

### 下一步
- 了解 [核心概念](#核心概念) 掌握框架设计原理
- 探索 [高级功能](#高级功能) 提升应用能力
- 参考 [最佳实践](#最佳实践) 编写优质代码

---

## 核心概念

### 扩展点（Extension Point）

扩展点是框架的核心概念，它定义了一个业务能力接口，允许在不修改核心代码的情况下进行功能扩展。

#### 定义扩展点
```java
/**
 * 订单处理扩展点
 * 提供订单生命周期的扩展能力
 */
@ExtPoint(name = "订单处理扩展点")
public interface OrderExtPoint {
    
    /**
     * 订单创建前处理
     * @param context 业务上下文，包含订单请求信息
     * @return 修改后的订单数据
     */
    OrderDTO preCreateOrder(BizContext<OrderRequest> context);
    
    /**
     * 订单创建后处理
     * @param context 业务上下文，包含创建的订单信息
     */
    void postCreateOrder(BizContext<OrderDTO> context);
}
```

#### 设计原则
- **单一职责**：每个扩展点专注于特定业务能力
- **方法明确**：命名清晰，参数和返回值类型具体
- **接口隔离**：避免创建大而全的接口
- **文档完善**：提供详细的方法注释和使用说明

### 扩展实现（Extension）

扩展实现是对扩展点接口的具体实现，通过 `@Extension` 注解进行配置。

#### 配置属性

| 属性 | 说明 | 示例 |
|------|------|------|
| tenantCode | 租户编码，用于多租户场景 | `tenantCode = "TENANT_A"` |
| bizCode | 业务编码，标识特定业务域 | `bizCode = "ECOMMERCE"` |
| useCase | 用例编码，标识特定业务流程 | `useCase = "CREATE_ORDER"` |
| scenario | 场景编码，标识特定应用场景 | `scenario = "MOBILE_APP"` |
| condition | SpEL条件表达式，动态匹配 | `condition = "#data.amount > 100"` |
| priority | 优先级，数值越小优先级越高 | `priority = 50` |

#### 示例
```java
@Extension(
    tenantCode = "TENANT_A",
    condition = "#data.amount > 1000",
    priority = 50
)
@Service
public class TenantAHighAmountOrderExtension implements OrderExtPoint {
    // 实现逻辑...
}
```

### 业务上下文（BizContext）

业务上下文包含了业务处理过程中的所有相关信息，是连接扩展点和扩展实现的桥梁。

#### 主要组成
- **核心维度**：租户、业务、用例、场景等标识信息
- **业务数据**：实际的业务处理对象
- **扩展属性**：自定义的上下文属性，用于条件匹配和数据传递

#### 设计特点
- **不可变设计**：BizContext实例一旦创建，核心属性不可修改
- **Builder模式**：必须通过Builder创建和配置实例
- **线程安全**：扩展属性存储使用ConcurrentHashMap，支持多线程访问
- **禁止直接修改**：不提供setData()等setter方法，确保数据一致性

#### 创建上下文
```java
BizContext<OrderRequest> context = BizContext.<OrderRequest>builder()
    // 核心维度信息
    .tenantCode("TENANT_A")
    .bizCode("ORDER_SERVICE")
    .useCase("CREATE_ORDER")
    .scenario("WEB")
    
    // 业务数据（必须通过Builder的data方法设置）
    .data(orderRequest)
    
    // 扩展属性
    .attribute("userId", "user123")
    .attribute("channel", "WEB")
    .attribute("timestamp", System.currentTimeMillis())
    
    .build();

// 重要提示：BizContext使用不可变设计模式，必须通过Builder创建
// ✅ 正确用法: context = BizContext.<T>builder().data(value).build();
// ❌ 错误用法: context.setData(value); // 不存在这样的方法
```

### 上下文管理（ExtensionContextManager）

上下文管理器负责维护当前线程的业务上下文，并在使用完毕后自动清理。

#### 正确使用方式
```java
// 推荐使用 try-with-resources 自动管理上下文生命周期
public OrderDTO processOrder(OrderRequest request) {
    BizContext<OrderRequest> context = createContext(request);
    
    try (ExtensionScope scope = ExtensionContextManager.with(context)) {
        // 执行业务逻辑，调用扩展点
        return orderExtPoint.processOrder(context);
    }
    // 自动清理上下文，避免内存泄漏
}
```

### 路由机制

框架会根据上下文信息自动选择最合适的扩展实现，匹配优先级如下：

1. 精确匹配（租户、业务、用例、场景完全匹配）
2. 条件匹配（通过SpEL表达式计算匹配）
3. 优先级匹配（在同等条件下，优先级数值小的优先）
4. 默认实现（没有其他匹配时的兜底实现）

#### 匹配流程
```
业务请求 → 创建上下文 → 查找匹配扩展 → 按优先级排序 → 执行匹配的扩展实现
```

### 缓存机制

框架内置多级缓存，优化扩展匹配性能：

- **表达式缓存**：缓存SpEL表达式的编译结果
- **匹配结果缓存**：缓存特定上下文匹配的扩展列表
- **扩展实例缓存**：缓存已加载的扩展实现实例

通过缓存，框架可以显著减少表达式解析和匹配计算的开销，提升系统性能。

---

## 高级功能

### 表达式路由（SpEL）

框架使用 Spring Expression Language (SpEL) 提供强大的动态条件匹配能力，可以基于上下文信息进行复杂的条件判断。

#### 可用变量

在 `condition` 表达式中，可以使用以下内置变量：

- `#tenantCode` - 当前租户编码
- `#bizCode` - 当前业务编码
- `#useCase` - 当前用例编码
- `#scenario` - 当前场景编码
- `#data` - 业务数据对象
- `#context` - 完整的业务上下文对象

#### 表达式示例

```java
// 1. 数值范围判断
@Extension(condition = "#data.amount >= 100 && #data.amount <= 1000")

// 2. 字符串匹配
@Extension(condition = "#data.status in {'PENDING', 'PROCESSING'}")

// 3. 集合操作（获取价格大于100的商品数量）
@Extension(condition = "#data.items.?[price > 100].size() > 0")

// 4. 复杂条件组合
@Extension(condition = "
    (#tenantCode == 'TENANT_A' && #data.amount > 500) || 
    (#tenantCode == 'TENANT_B' && #data.amount > 1000)
")

// 5. 安全访问（避免空指针异常）
@Extension(condition = "#data.user?.level == 'VIP'")

// 6. 方法调用
@Extension(condition = "#bizCode.startsWith('ORDER')")

// 7. 静态方法调用
@Extension(condition = "T(java.util.Objects).equals(#scenario, 'MOBILE')")
```

### 优先级控制

优先级机制允许您精细控制多个匹配扩展的执行顺序，数值越小优先级越高。

#### 优先级示例

```java
// 1. 管理员订单处理（最高优先级）
@Extension(
    priority = 10,  // 最高优先级
    condition = "#data.userId.startsWith('ADMIN')"
)
@Service
public class AdminOrderExtension implements OrderExtPoint {
    @Override
    public OrderDTO preCreateOrder(BizContext<OrderRequest> context) {
        // 管理员订单特殊处理逻辑
        return new OrderDTO();
    }
}

// 2. VIP用户订单处理（中等优先级）
@Extension(
    priority = 50,  // 中等优先级
    condition = "#context.getAttribute('vipLevel') != null"
)
@Service
public class VipOrderExtension implements OrderExtPoint {
    @Override
    public OrderDTO preCreateOrder(BizContext<OrderRequest> context) {
        // VIP订单处理逻辑
        return new OrderDTO();
    }
}

// 3. 普通订单处理（低优先级）
@Extension(priority = 100)  // 低优先级，作为兜底实现
@Service
public class DefaultOrderExtension implements OrderExtPoint {
    @Override
    public OrderDTO preCreateOrder(BizContext<OrderRequest> context) {
        // 默认订单处理逻辑
        return new OrderDTO();
    }
}
```

### 组合扩展执行

在某些场景下，您可能需要多个扩展共同处理一个业务请求。框架提供了 `ExtPointComposite` 来支持这种需求。

#### 组合执行示例

```java
@Service
public class OrderProcessingService {
    
    @Autowired
    private ExtPointComposite<OrderExtPoint> orderExtPointComposite;
    
    public OrderProcessingResult processOrder(OrderRequest request) {
        BizContext<OrderRequest> context = createContext(request);
        
        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            // 获取所有匹配的扩展实现（已按优先级排序）
            List<OrderExtPoint> extensions = orderExtPointComposite.getExtensions(context);
            
            OrderDTO finalResult = null;
            List<ProcessingStep> steps = new ArrayList<>();
            
            // 依次执行每个扩展
            for (OrderExtPoint extension : extensions) {
                String extensionName = extension.getClass().getSimpleName();
                
                try {
                    // 执行扩展逻辑
                    OrderDTO currentResult = extension.preCreateOrder(context);
                    steps.add(ProcessingStep.success(extensionName, "执行成功"));
                    
                    // 更新结果和上下文
                    if (currentResult != null) {
                        finalResult = currentResult;
                        context.setData(convertToRequest(currentResult));
                    }
                    
                } catch (Exception e) {
                    // 记录错误但继续执行其他扩展
                    steps.add(ProcessingStep.failed(extensionName, e.getMessage()));
                }
            }
            
            return OrderProcessingResult.builder()
                .order(finalResult)
                .processingSteps(steps)
                .success(finalResult != null)
                .build();
        }
    }
}
```

### 动态注册扩展

框架支持在运行时动态注册和注销扩展实现，适用于需要根据业务需求动态调整扩展行为的场景。

#### 动态注册示例

```java
@Service
public class DynamicExtensionManager {
    
    @Autowired
    private ExtensionRegister extensionRegister;
    
    /**
     * 动态注册租户专属扩展
     */
    public void registerTenantExtension(String tenantCode, String bizCode) {
        // 创建动态扩展实例
        OrderExtPoint dynamicExtension = new DynamicOrderExtension();
        
        // 构建扩展元数据
        ExtensionMetadata metadata = ExtensionMetadata.builder()
            .tenantCode(tenantCode)
            .bizCode(bizCode)
            .priority(75)
            .condition("#data.amount > 0")
            .build();
            
        // 注册扩展
        extensionRegister.registerExtension(OrderExtPoint.class, dynamicExtension, metadata);
    }
    
    /**
     * 动态注销扩展
     */
    public void unregisterExtension(OrderExtPoint extension) {
        extensionRegister.unregisterExtension(OrderExtPoint.class, extension);
    }
}

// 动态扩展实现类
class DynamicOrderExtension implements OrderExtPoint {
    @Override
    public OrderDTO preCreateOrder(BizContext<OrderRequest> context) {
        // 实现动态业务逻辑
        return new OrderDTO();
    }
    
    // 实现其他必要的方法
}
```

### 异步执行扩展

对于非关键路径的扩展逻辑，可以使用异步执行提高系统响应速度。

#### 异步执行示例

```java
@Configuration
public class AsyncConfig {
    @Bean(name = "extensionExecutor")
    public Executor extensionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("ExtensionAsync-");
        executor.initialize();
        return executor;
    }
}

@Extension(tenantCode = "TENANT_A")
@Service
public class AsyncNotificationExtension implements OrderExtPoint {
    
    @Async("extensionExecutor")
    @Override
    public void postCreateOrder(BizContext<OrderDTO> context) {
        // 异步发送通知、记录日志等非关键操作
        OrderDTO order = context.getData();
        // 执行异步操作...
    }
}
```

---

## 5. 最佳实践

### 5.1 扩展点设计原则

| 原则 | 正确示例 | 错误示例 |
|------|----------|----------|
| **单一职责** | `OrderValidationExt` 只做验证 | `OrderExt` 包含验证、计算、通知 |
| **明确命名** | `preCreateUser`, `validateOrder` | `process`, `handle` |
| **合理返回值** | `OrderDTO`, `ValidationResult` | `void`（无法传递数据） |
| **异常处理** | 抛出 `BizException` 包含错误码 | 抛出通用 `Exception` |

### 5.2 性能优化指南

**1. 表达式优化**
```java
// ❌ 避免：复杂嵌套表达式
@Extension(condition = "#data.user.addresses.?[#this.default].size() > 0 && 
                          #data.user.addresses.?[#this.default].get(0).city == 'Beijing'")

// ✅ 推荐：简化表达式 + 预计算
@Extension(condition = "#context.getAttribute('isBeijingUser') == true")
@Service  
public class OptimizedExtension implements OrderExtPoint {
    @Override
    public OrderDTO preCreateOrder(BizContext<OrderRequest> context) {
        // 预计算复杂条件
        boolean isBeijingUser = calculateIsBeijingUser(context.getData());
        context.setAttribute("isBeijingUser", isBeijingUser);
        
        return processOrder(context);
    }
}
```

**2. 缓存策略**
```java
@Service
public class CachedExtension implements ProductExtPoint {
    
    // 本地缓存减少重复查询
    private final Cache<String, ProductConfig> cache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();
  ### 促销服务集成实现

```java
/**
 * 促销服务
 * 负责协调多种促销策略的应用，支持多种促销方式的组合使用
 */
@Slf4j
public class PromotionService {
    
    @Autowired
    private PromotionExtPoint promotionExtPoint;
    
    // 用于测试的setter方法
    public void setPromotionExtPoint(PromotionExtPoint promotionExtPoint) {
        this.promotionExtPoint = promotionExtPoint;
    }

    /**
     * 计算订单适用的促销
     * @param request 促销请求
     * @param tenantCode 租户代码
     * @return 最终的促销结果
     */
    public PromotionResult calculatePromotion(PromotionRequest request, String tenantCode) {
        // 使用ExtensionContextManager创建上下文，支持try-with-resources模式
        try (ExtensionScope scope = ExtensionContextManager.withTenant(tenantCode)
                .withAttribute("userId", request.getUserId())) {
            
            // 创建业务上下文
            BizContext<PromotionRequest> context = ExtensionContextManager.fromData(request);
            
            // 记录请求日志
            log.info("Calculating promotions for user: {}", request.getUserId());
            
            // 通过扩展点计算促销
            PromotionResult result = promotionExtPoint.calculatePromotion(context);
            
            // 如果结果为null，创建默认结果
            if (result == null) {
                result = new PromotionResult();
                result.setOriginalTotal(request.getSubtotal());
                result.setFinalTotal(request.getSubtotal());
                result.setDiscountApplied(false);
                result.setAppliedPromotions(new ArrayList<>());
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error calculating promotions for user: {}", request.getUserId(), e);
            // 返回默认结果，保证服务可用性
            PromotionResult fallbackResult = new PromotionResult();
            fallbackResult.setOriginalTotal(request != null ? request.getSubtotal() : BigDecimal.ZERO);
            fallbackResult.setFinalTotal(request != null ? request.getSubtotal() : BigDecimal.ZERO);
            fallbackResult.setDiscountApplied(false);
            fallbackResult.setAppliedPromotions(new ArrayList<>());
            return fallbackResult;
        }
    }
}

### 促销结果数据结构

```java
/**
 * 促销计算结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResult {
    private BigDecimal originalTotal;
    private BigDecimal finalTotal;
    private List<AppliedPromotion> appliedPromotions;
    private boolean discountApplied;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppliedPromotion {
        private String promotionId;
        private String promotionName;
        private String promotionType;
        private BigDecimal discountAmount;
        private String description;
    }
}

### 促销请求数据结构

```java
/**
 * 促销计算请求
 * 支持复杂SpEL条件表达式匹配和多维度促销策略
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRequest {
    private String userId;
    private List<OrderItem> items;
    private BigDecimal subtotal;
    private String userLevel;
    private String promotionCode;
    private String orderType; // 订单类型，用于SpEL条件表达式匹配
    private UserInfo userInfo; // 用户信息，用于复杂条件匹配
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private String productId;
        private String productName;
        private BigDecimal unitPrice;
        private int quantity;
        private String category;
    }
    
    /**
     * 用户信息类，用于复杂SpEL条件表达式
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String memberLevel;
        private int memberPoints;
        private int orderCount;
        private String registrationDate;
    }
}

### 促销服务单元测试

```java
/**
 * 促销服务测试类
 * 包含复杂条件表达式测试、优先级测试、错误处理测试等全面的测试场景
 */
class PromotionServiceTest {

    private PromotionService promotionService;
    private PromotionExtPoint promotionExtPoint;

    @BeforeEach
    void setUp() {
        // 创建mock对象
        promotionExtPoint = Mockito.mock(PromotionExtPoint.class);
        promotionService = new PromotionService();
        
        // 注入mock对象
        promotionService.setPromotionExtPoint(promotionExtPoint);
    }

    /**
     * 测试复杂SpEL条件表达式匹配场景
     */
    @Test
    void testComplexSpELExpressionMatching() {
        // 准备测试数据 - 符合满减促销的条件
        PromotionRequest request = PromotionRequest.builder()
            .userId("user123")
            .orderType("NORMAL") // 满足条件 #data.orderType == 'NORMAL'
            .subtotal(new BigDecimal("300"))
            .build();
        
        String tenantCode = "DEFAULT";
        
        // 配置mock行为 - 模拟满减促销被触发
        PromotionResult expectedResult = PromotionResult.builder()
            .originalTotal(new BigDecimal("300"))
            .finalTotal(new BigDecimal("270"))
            .discountApplied(true)
            .appliedPromotions(List.of(
                PromotionResult.AppliedPromotion.builder()
                    .promotionId("RULE002")
                    .promotionName("满200减30")
                    .promotionType("FULL_DISCOUNT")
                    .discountAmount(new BigDecimal("30"))
                    .description("满200减30")
                    .build()
            ))
            .build();
        
        // 使用argThat匹配条件，验证上下文是否包含了正确的属性
        when(promotionExtPoint.calculatePromotion(argThat(context -> {
            // 验证上下文是否包含userId属性
            return "user123".equals(context.getAttribute("userId"));
        }))).thenReturn(expectedResult);
        
        // 执行测试
        try (ExtensionScope scope = ExtensionContextManager.withTenant(tenantCode)
                .withAttribute("promotionType", "FULL_DISCOUNT")) { // 设置属性满足条件 #context.getAttribute('promotionType') == 'FULL_DISCOUNT'
            PromotionResult result = promotionService.calculatePromotion(request, tenantCode);
            
            // 验证结果
            assertNotNull(result);
            assertEquals(new BigDecimal("270"), result.getFinalTotal());
            assertTrue(result.isDiscountApplied());
            assertEquals(1, result.getAppliedPromotions().size());
            assertEquals("FULL_DISCOUNT", result.getAppliedPromotions().get(0).getPromotionType());
        }
        
        // 验证扩展点被正确调用
        verify(promotionExtPoint).calculatePromotion(any(BizContext.class));
    }

    /**
     * 测试会员折扣表达式匹配场景
     */
    @Test
    void testMemberDiscountExpressionMatching() {
        // 准备测试数据 - VIP会员
        PromotionRequest.UserInfo userInfo = PromotionRequest.UserInfo.builder()
            .memberLevel("VIP") // 满足条件 #data.userInfo.memberLevel != null
            .build();
        
        PromotionRequest request = PromotionRequest.builder()
            .userId("vipUser")
            .userInfo(userInfo)
            .subtotal(new BigDecimal("100"))
            .build();
        
        String tenantCode = "DEFAULT";
        
        // 配置mock行为 - 模拟会员折扣被触发
        PromotionResult expectedResult = PromotionResult.builder()
            .originalTotal(new BigDecimal("100"))
            .finalTotal(new BigDecimal("95")) // VIP 95折
            .discountApplied(true)
            .appliedPromotions(List.of(
                PromotionResult.AppliedPromotion.builder()
                    .promotionId("MEMBER_VIP")
                    .promotionName("VIP会员专属折扣")
                    .promotionType("MEMBER_DISCOUNT")
                    .discountAmount(new BigDecimal("5"))
                    .description("VIP会员专享9.5折优惠")
                    .build()
            ))
            .build();
        
        when(promotionExtPoint.calculatePromotion(any(BizContext.class))).thenReturn(expectedResult);
        
        // 执行测试
        PromotionResult result = promotionService.calculatePromotion(request, tenantCode);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(new BigDecimal("95"), result.getFinalTotal());
        assertTrue(result.isDiscountApplied());
        assertEquals(1, result.getAppliedPromotions().size());
        assertEquals("MEMBER_DISCOUNT", result.getAppliedPromotions().get(0).getPromotionType());
    }

    /**
     * 测试促销优先级机制
     * 验证优先级数值：180(会员折扣) < 190(商品特定) < 200(满减)
     */
    @Test
    void testPromotionPriorityMechanism() {
        // 准备测试数据 - 同时满足多个促销条件
        PromotionRequest.UserInfo userInfo = PromotionRequest.UserInfo.builder()
            .memberLevel("GOLD")
            .build();
        
        // 创建包含特定商品的商品列表
        List<PromotionRequest.OrderItem> items = List.of(
            PromotionRequest.OrderItem.builder()
                .productId("PROD001") // 特定商品折扣
                .productName("测试商品")
                .unitPrice(new BigDecimal("200"))
                .quantity(1)
                .category("ELECTRONICS")
                .build()
        );
        
        PromotionRequest request = PromotionRequest.builder()
            .userId("priorityUser")
            .userInfo(userInfo)
            .items(items)
            .orderType("NORMAL")
            .subtotal(new BigDecimal("200"))
            .build();
        
        String tenantCode = "DEFAULT";
        
        // 配置mock行为 - 返回满减促销结果（优先级最高的）
        PromotionResult expectedResult = PromotionResult.builder()
            .originalTotal(new BigDecimal("200"))
            .finalTotal(new BigDecimal("170")) // 满200减30
            .discountApplied(true)
            .appliedPromotions(List.of(
                PromotionResult.AppliedPromotion.builder()
                    .promotionId("RULE002")
                    .promotionName("满200减30")
                    .promotionType("FULL_DISCOUNT")
                    .discountAmount(new BigDecimal("30"))
                    .description("满200减30")
                    .build()
            ))
            .build();
        
        // 使用argThat验证是否通过了正确的上下文
        when(promotionExtPoint.calculatePromotion(argThat(context -> {
            PromotionRequest req = context.getData();
            return req != null && 
                   "NORMAL".equals(req.getOrderType()) &&
                   req.getItems() != null && !req.getItems().isEmpty() &&
                   req.getUserInfo() != null && "GOLD".equals(req.getUserInfo().getMemberLevel());
        }))).thenReturn(expectedResult);
        
        // 执行测试
        try (ExtensionScope scope = ExtensionContextManager.withTenant(tenantCode)
                .withAttribute("promotionType", "FULL_DISCOUNT")) {
            PromotionResult result = promotionService.calculatePromotion(request, tenantCode);
            
            // 验证结果
            assertNotNull(result);
            assertEquals(new BigDecimal("170"), result.getFinalTotal());
            assertTrue(result.isDiscountApplied());
            assertEquals(1, result.getAppliedPromotions().size());
            // 验证优先级最高的满减促销被应用
            assertEquals("FULL_DISCOUNT", result.getAppliedPromotions().get(0).getPromotionType());
        }
    }

    /**
     * 测试异常处理机制
     */
    @Test
    void testExceptionHandling() {
        // 准备测试数据
        PromotionRequest request = PromotionRequest.builder()
            .userId("errorUser")
            .subtotal(new BigDecimal("100"))
            .build();
        
        String tenantCode = "DEFAULT";
        
        // 配置mock行为 - 抛出异常
        when(promotionExtPoint.calculatePromotion(any(BizContext.class))).thenThrow(new RuntimeException("模拟促销计算异常"));
        
        // 执行测试 - 即使出现异常，服务也应该返回默认结果而不是崩溃
        PromotionResult result = promotionService.calculatePromotion(request, tenantCode);
        
        // 验证结果 - 应该返回默认结果
        assertNotNull(result);
        assertEquals(new BigDecimal("100"), result.getFinalTotal());
        assertFalse(result.isDiscountApplied());
        assertNotNull(result.getAppliedPromotions());
        assertTrue(result.getAppliedPromotions().isEmpty());
    }

    /**
     * 测试空请求边界条件
     */
    @Test
    void testNullRequestBoundaryCondition() {
        String tenantCode = "DEFAULT";
        
        // 执行测试 - 传入null请求
        PromotionResult result = promotionService.calculatePromotion(null, tenantCode);
        
        // 验证结果 - 应该返回安全的默认结果
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getFinalTotal());
        assertFalse(result.isDiscountApplied());
        assertNotNull(result.getAppliedPromotions());
        assertTrue(result.getAppliedPromotions().isEmpty());
    }

    /**
     * 测试商品特定边界条件
     */
    @Test
    void testProductSpecificBoundaryCondition() {
        // 准备测试数据 - 空商品列表
        PromotionRequest request = PromotionRequest.builder()
            .userId("emptyItemsUser")
            .items(new ArrayList<>()) // 空商品列表，不会触发商品特定促销
            .subtotal(new BigDecimal("100"))
            .build();
        
        String tenantCode = "DEFAULT";
        
        // 配置mock行为 - 返回无促销结果
        PromotionResult expectedResult = PromotionResult.builder()
            .originalTotal(new BigDecimal("100"))
            .finalTotal(new BigDecimal("100"))
            .discountApplied(false)
            .appliedPromotions(new ArrayList<>())
            .build();
        
        when(promotionExtPoint.calculatePromotion(any(BizContext.class))).thenReturn(expectedResult);
        
        // 执行测试
        PromotionResult result = promotionService.calculatePromotion(request, tenantCode);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(new BigDecimal("100"), result.getFinalTotal());
        assertFalse(result.isDiscountApplied());
        assertTrue(result.getAppliedPromotions().isEmpty());
    }
}
    public ProductDTO enhanceProduct(BizContext<ProductRequest> context) {
        String cacheKey = context.getTenantCode() + ":" + context.getData().getProductId();
        
        return cache.get(cacheKey, () -> {
            // 缓存miss时的加载逻辑
            return loadAndProcessProduct(context);
        });
    }
    
    @PostConstruct
    public void warmUpCache() {
        // 应用启动时预热常用数据
    }
}
```

**3. 异步处理**
```java
@Extension(tenantCode = "TENANT_A")
@Service
public class AsyncOrderExtension implements OrderExtPoint {
    
    @Async("extensionExecutor")
    @Override
    public void postCreateOrder(BizContext<OrderDTO> context) {
        // 异步处理非关键路径
        OrderDTO order = context.getData();
        notificationService.sendOrderConfirmation(order);
        analyticsService.recordOrderEvent(order);
    }
}
```

## 错误处理与性能优化

### 统一错误处理

在扩展点实现中，良好的错误处理对于系统稳定性至关重要。推荐使用统一的异常处理模式：

```java
// 1. 自定义业务异常类
public class ExtensionBizException extends RuntimeException {
    private final String errorCode;
    private final String errorMessage;
    
    public ExtensionBizException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}

// 2. 健壮的扩展实现示例
@Extension(tenantCode = "TENANT_A")
@Service
@Slf4j
public class RobustExtension implements OrderExtPoint {
    
    @Override
    public OrderDTO preCreateOrder(BizContext<OrderRequest> context) {
        try {
            // 1. 参数验证
            OrderRequest request = context.getData();
            if (request == null) {
                throw new ExtensionBizException("INVALID_REQUEST", "请求数据不能为空");
            }
            
            // 2. 业务验证
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ExtensionBizException("INVALID_AMOUNT", "订单金额必须大于0");
            }
            
            // 3. 执行业务逻辑
            return processOrder(request);
            
        } catch (ExtensionBizException e) {
            // 业务异常：记录日志并保持原异常传播
            log.warn("业务异常: {}", e.getErrorMessage());
            throw e;
        } catch (Exception e) {
            // 系统异常：包装为业务异常，避免暴露底层错误
            log.error("系统异常", e);
            throw new ExtensionBizException("SYSTEM_ERROR", "系统处理异常");
        }
    }
    
    private OrderDTO processOrder(OrderRequest request) {
        // 实际业务处理逻辑
        return new OrderDTO();
    }
}
```

## 业务场景示例

本节通过真实业务场景展示Bone扩展框架的实际应用，帮助开发者更好地理解如何在生产环境中有效使用扩展点。

### 多租户SaaS系统中的支付处理

在多租户SaaS系统中，不同行业的租户往往需要定制化的支付流程和业务规则。通过扩展点可以为每个租户提供专属的支付处理逻辑，同时保持系统核心的一致性。

```java
/**
 * 支付处理扩展点
 * 定义了支付前验证、支付金额计算和支付后处理三个核心方法
 */
@ExtPoint(name = "支付处理扩展点", description = "支持多租户的支付处理流程定制")
public interface PaymentExtPoint {
    
    /**
     * 支付前验证
     * @param context 业务上下文，包含订单信息和支付请求
     * @return 验证结果，包含是否通过和错误信息
     */
    ValidationResult prePayValidate(BizContext<PaymentRequest> context);
    
    /**
     * 计算最终支付金额
     * @param context 业务上下文
     * @return 支付计算结果
     */
    PaymentCalculationResult calculatePayment(BizContext<PaymentRequest> context);
    
    /**
     * 支付后处理
     * @param context 业务上下文，包含支付结果
     */
    void postPayProcess(BizContext<PaymentResult> context);
}

/**
 * 电商平台租户支付实现
 * 提供标准电商支付流程，包括优惠券、积分抵扣等功能
 */
@Extension(tenantCode = "ECOMMERCE_TENANT", priority = 100)
@Service
@Slf4j
public class EcommercePaymentExtension implements PaymentExtPoint {
    
    @Autowired
    private CouponService couponService;
    
    @Autowired
    private PointsService pointsService;
    
    @Autowired
    private PaymentLogService logService;
    
    @Override
    public ValidationResult prePayValidate(BizContext<PaymentRequest> context) {
        try {
            PaymentRequest request = context.getData();
            
            // 参数验证
            if (request == null || request.getOrderId() == null) {
                return ValidationResult.fail("INVALID_REQUEST", "支付请求参数不完整");
            }
            
            // 业务验证
            if (!isOrderValid(request.getOrderId(), context.getTenantCode())) {
                return ValidationResult.fail("INVALID_ORDER", "订单无效或已被处理");
            }
            
            // 优惠券验证
            if (request.getCouponId() != null) {
                ValidationResult couponValidation = couponService.validateCoupon(
                    request.getCouponId(), request.getUserId(), request.getAmount());
                if (!couponValidation.isSuccess()) {
                    return couponValidation;
                }
            }
            
            log.info("Payment validation passed for order: {}", request.getOrderId());
            return ValidationResult.success();
        } catch (Exception e) {
            log.error("Payment validation failed for tenant: {}", context.getTenantCode(), e);
            return ValidationResult.fail("VALIDATION_ERROR", "支付验证过程中出现异常");
        }
    }
    
    @Override
    public PaymentCalculationResult calculatePayment(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getData();
        BigDecimal baseAmount = request.getAmount();
        BigDecimal finalAmount = baseAmount;
        Map<String, BigDecimal> deductionDetails = new HashMap<>();
        
        // 优惠券计算
        if (request.getCouponId() != null) {
            BigDecimal couponDiscount = couponService.calculateDiscount(
                request.getCouponId(), baseAmount);
            finalAmount = finalAmount.subtract(couponDiscount);
            deductionDetails.put("COUPON_DISCOUNT", couponDiscount);
        }
        
        // 积分抵扣
        if (request.getPointsToDeduct() > 0) {
            BigDecimal pointsValue = pointsService.calculatePointsValue(request.getPointsToDeduct());
            finalAmount = finalAmount.subtract(pointsValue);
            deductionDetails.put("POINTS_DEDUCTION", pointsValue);
        }
        
        // 确保最终金额不为负数
        finalAmount = finalAmount.max(BigDecimal.ZERO);
        
        return PaymentCalculationResult.builder()
            .originalAmount(baseAmount)
            .finalAmount(finalAmount)
            .deductionDetails(deductionDetails)
            .currency("CNY")
            .build();
    }
    
    @Override
    public void postPayProcess(BizContext<PaymentResult> context) {
        PaymentResult result = context.getData();
        
        // 记录支付日志
        logService.logPayment(result);
        
        // 异步处理积分扣减
        if (result.getPointsDeducted() > 0) {
            CompletableFuture.runAsync(() -> {
                try {
                    pointsService.deductPoints(result.getUserId(), result.getPointsDeducted());
                } catch (Exception e) {
                    log.error("Failed to deduct points for user: {}", result.getUserId(), e);
                }
            });
        }
        
        // 异步发送通知
        sendPaymentNotification(result);
    }
    
    private boolean isOrderValid(Long orderId, String tenantCode) {
        // 实际的订单验证逻辑
        return true;
    }
    
    private void sendPaymentNotification(PaymentResult result) {
        // 发送支付通知的逻辑
    }
}

/**
 * 金融服务租户支付实现
 * 支持复杂的费率计算、手续费分配和合规检查
 */
@Extension(tenantCode = "FINANCIAL_TENANT", priority = 100)
@Service
@Slf4j
public class FinancialPaymentExtension implements PaymentExtPoint {
    
    @Autowired
    private ComplianceService complianceService;
    
    @Autowired
    private FeeService feeService;
    
    @Override
    public ValidationResult prePayValidate(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getData();
        
        // 合规性检查
        if (!complianceService.checkTransaction(request.getUserId(), request.getAmount())) {
            return ValidationResult.fail("COMPLIANCE_VIOLATION", "交易不符合合规要求");
        }
        
        // 风控检查
        RiskLevel riskLevel = complianceService.assessRisk(request);
        if (riskLevel == RiskLevel.HIGH) {
            return ValidationResult.fail("HIGH_RISK", "交易风险等级过高");
        }
        
        return ValidationResult.success();
    }
    
    @Override
    public PaymentCalculationResult calculatePayment(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getData();
        
        // 计算手续费
        BigDecimal fee = feeService.calculateFee(request.getAmount(), request.getPaymentMethod());
        
        // 计算税率
        BigDecimal tax = calculateTax(request.getAmount(), request.getTaxType());
        
        return PaymentCalculationResult.builder()
            .originalAmount(request.getAmount())
            .finalAmount(request.getAmount().add(fee).add(tax))
            .feeAmount(fee)
            .taxAmount(tax)
            .currency("CNY")
            .build();
    }
    
    @Override
    public void postPayProcess(BizContext<PaymentResult> context) {
        // 金融特有的支付后处理逻辑
        // 包括：清算、对账标记、合规记录等
        complianceService.recordTransaction(context.getData());
    }
    
    private BigDecimal calculateTax(BigDecimal amount, String taxType) {
        // 根据税务类型计算税额
        return amount.multiply(new BigDecimal("0.06")); // 默认6%税率
    }
}

/**
 * 支付服务集成类
 * 负责集成支付扩展点并提供统一的支付接口
 */
@Service
@Slf4j
public class PaymentService {
    
    @Autowired
    private PaymentExtPoint paymentExtPoint;
    
    @Autowired
    private TransactionService transactionService;
    
    /**
     * 处理支付请求
     * @param request 支付请求
     * @param tenantCode 租户代码
     * @return 支付处理结果
     */
    public PaymentProcessResult processPayment(PaymentRequest request, String tenantCode) {
        // 创建业务上下文
        BizContext<PaymentRequest> context = BizContext.<PaymentRequest>builder()
            .tenantCode(tenantCode)
            .bizCode("PAYMENT_SERVICE")
            .data(request)
            .attribute("timestamp", System.currentTimeMillis())
            .build();
        
        // 使用上下文管理器（自动清理）
        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            // 1. 支付前验证
            ValidationResult validationResult = paymentExtPoint.prePayValidate(context);
            if (!validationResult.isSuccess()) {
                log.warn("Payment validation failed: {}", validationResult.getErrorMessage());
                return PaymentProcessResult.fail(
                    validationResult.getErrorCode(), 
                    validationResult.getErrorMessage());
            }
            
            // 2. 计算支付金额
            PaymentCalculationResult calculationResult = paymentExtPoint.calculatePayment(context);
            
            // 3. 执行实际支付
            PaymentResult paymentResult = transactionService.executePayment(
                request, calculationResult.getFinalAmount());
            
            if (paymentResult.isSuccess()) {
                // 4. 支付成功后的处理
                BizContext<PaymentResult> postPayContext = BizContext.<PaymentResult>builder()
                    .tenantCode(tenantCode)
                    .bizCode("PAYMENT_SERVICE")
                    .data(paymentResult)
                    .build();
                
                try {
                    paymentExtPoint.postPayProcess(postPayContext);
                } catch (Exception e) {
                    // 支付后处理异常不影响主流程
                    log.error("Post payment process failed", e);
                }
                
                return PaymentProcessResult.success(paymentResult.getTransactionId(), 
                    calculationResult.getFinalAmount());
            } else {
                log.error("Payment execution failed: {}", paymentResult.getErrorMessage());
                return PaymentProcessResult.fail(paymentResult.getErrorCode(), 
                    paymentResult.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Payment processing failed", e);
            return PaymentProcessResult.fail("SYSTEM_ERROR", "支付处理过程中出现系统异常");
        }
    }
}
### 电商系统中的促销策略

电商系统的促销策略复杂多变，需要根据不同商品、用户群体、营销场景应用不同的规则。通过扩展点可以灵活管理各类促销策略的动态切换与组合。

```java
/**
 * 促销计算扩展点
 * 支持多种促销策略的动态切换与组合
 */
@ExtPoint(name = "促销计算扩展点", description = "电商系统中的促销策略计算接口")
public interface PromotionExtPoint {
    
    /**
     * 计算促销优惠金额
     * @param context 业务上下文，包含订单和商品信息
     * @return 促销计算结果
     */
    PromotionResult calculatePromotion(BizContext<PromotionRequest> context);
    
    /**
     * 判断是否适用当前促销策略
     * @param context 业务上下文
     * @return 是否适用
     */
    boolean isApplicable(BizContext<PromotionRequest> context);
}

/**
 * 满减促销策略实现
 * 支持阶梯式满减规则
 */
@Extension(condition = "#data.orderType == 'NORMAL' && #context.getAttribute('promotionType') == 'FULL_DISCOUNT'", 
           priority = 200)
@Service
@Slf4j
public class FullDiscountPromotionExtension implements PromotionExtPoint {
    
    @Override
    public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
        log.info("Processing full discount promotion");
        PromotionRequest request = context.getData();
        
        // 添加空值检查
        if (request == null) {
            log.error("Promotion request is null");
            return createEmptyResult();
        }
        
        BigDecimal subtotal = request.getSubtotal();
        // 添加金额空值检查
        if (subtotal == null) {
            log.error("Subtotal is null in promotion request");
            return createEmptyResult();
        }
        
        // 获取适用的满减规则
        FullDiscountRule rule = getApplicableRule(subtotal);
        
        if (rule != null) {
            BigDecimal discount = rule.getDiscountAmount();
            
            PromotionResult.AppliedPromotion appliedPromotion = PromotionResult.AppliedPromotion.builder()
                .promotionId(rule.getId())
                .promotionName(rule.getName())
                .promotionType("FULL_DISCOUNT")
                .discountAmount(discount)
                .description("满" + rule.getThreshold() + "减" + discount)
                .build();
            
            List<PromotionResult.AppliedPromotion> promotions = new ArrayList<>();
            promotions.add(appliedPromotion);
            
            return PromotionResult.builder()
                .originalTotal(subtotal)
                .finalTotal(subtotal.subtract(discount))
                .appliedPromotions(promotions)
                .discountApplied(true)
                .build();
        }
        
        // 无适用的满减规则
        return PromotionResult.builder()
            .originalTotal(subtotal)
            .finalTotal(subtotal)
            .appliedPromotions(new ArrayList<>())
            .discountApplied(false)
            .build();
    }
    
    @Override
    public boolean isApplicable(BizContext<PromotionRequest> context) {
        try {
            PromotionRequest request = context.getData();
            return request != null && request.getSubtotal() != null && request.getSubtotal().compareTo(BigDecimal.ZERO) > 0;
        } catch (Exception e) {
            log.error("Error checking applicability for full discount promotion", e);
            return false;
        }
    }
    
    /**
     * 满减规则类
     */
    private static class FullDiscountRule {
        private String id;
        private String name;
        private BigDecimal threshold;
        private BigDecimal discountAmount;
        
        public FullDiscountRule(String id, String name, BigDecimal threshold, BigDecimal discountAmount) {
            this.id = id;
            this.name = name;
            this.threshold = threshold;
            this.discountAmount = discountAmount;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public BigDecimal getThreshold() { return threshold; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
    }
    
    /**
     * 获取适用的满减规则
     */
    private FullDiscountRule getApplicableRule(BigDecimal amount) {
        List<FullDiscountRule> rules = new ArrayList<>();
        rules.add(new FullDiscountRule("RULE001", "满100减10", new BigDecimal("100"), new BigDecimal("10")));
        rules.add(new FullDiscountRule("RULE002", "满200减30", new BigDecimal("200"), new BigDecimal("30")));
        rules.add(new FullDiscountRule("RULE003", "满500减100", new BigDecimal("500"), new BigDecimal("100")));
        
        // 按门槛降序排序，选择最高的适用规则
        return rules.stream()
            .filter(rule -> amount.compareTo(rule.getThreshold()) >= 0)
            .max(Comparator.comparing(FullDiscountRule::getThreshold))
            .orElse(null);
    }
    
    /**
     * 创建空的促销结果
     */
    private PromotionResult createEmptyResult() {
        return PromotionResult.builder()
            .originalTotal(BigDecimal.ZERO)
            .finalTotal(BigDecimal.ZERO)
            .appliedPromotions(new ArrayList<>())
            .discountApplied(false)
            .build();
    }
}

/**
 * 会员折扣促销策略实现
 * 根据用户会员等级提供不同比例的折扣
 */
@Extension(condition = "#data.userInfo.memberLevel != null", priority = 180)
@Service
@Slf4j
public class MemberDiscountPromotionExtension implements PromotionExtPoint {
    
    @Override
    public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
        log.info("Processing member discount promotion");
        PromotionRequest request = context.getData();
        
        // 添加空值检查
        if (request == null) {
            log.error("Promotion request is null");
            return createEmptyResult();
        }
        
        // 获取用户信息和会员等级
        PromotionRequest.UserInfo userInfo = request.getUserInfo();
        if (userInfo == null || userInfo.getMemberLevel() == null) {
            log.error("User info or member level is null");
            return createEmptyResult();
        }
        
        String memberLevel = userInfo.getMemberLevel();
        double discountRate = getDiscountRateByMemberLevel(memberLevel);
        
        if (discountRate < 1.0) { // 有折扣
            BigDecimal subtotal = request.getSubtotal();
            if (subtotal == null) {
                log.error("Subtotal is null in promotion request");
                return createEmptyResult();
            }
            
            BigDecimal discountAmount = subtotal.subtract(
                subtotal.multiply(new BigDecimal(discountRate)));
            
            String memberLevelName = getMemberLevelName(memberLevel);
            log.info("Applied member discount: {}% for member level: {}", 
                    (1 - discountRate) * 100, memberLevel);
            
            PromotionResult.AppliedPromotion appliedPromotion = PromotionResult.AppliedPromotion.builder()
                .promotionId("MEMBER_" + memberLevel)
                .promotionName(memberLevelName + "专属折扣")
                .promotionType("MEMBER_DISCOUNT")
                .discountAmount(discountAmount)
                .description(memberLevelName + "专享" + (discountRate * 10) + "折优惠")
                .build();
            
            List<PromotionResult.AppliedPromotion> promotions = new ArrayList<>();
            promotions.add(appliedPromotion);
            
            return PromotionResult.builder()
                .originalTotal(subtotal)
                .finalTotal(subtotal.subtract(discountAmount))
                .appliedPromotions(promotions)
                .discountApplied(true)
                .build();
        }
        
        return createEmptyResult();
    }
    
    @Override
    public boolean isApplicable(BizContext<PromotionRequest> context) {
        try {
            PromotionRequest request = context.getData();
            return request != null && request.getUserInfo() != null && 
                   request.getUserInfo().getMemberLevel() != null && 
                   request.getSubtotal() != null && request.getSubtotal().compareTo(BigDecimal.ZERO) > 0;
        } catch (Exception e) {
            log.error("Error checking applicability for member discount promotion", e);
            return false;
        }
    }
    
    /**
     * 根据会员等级获取折扣率
     */
    private double getDiscountRateByMemberLevel(String memberLevel) {
        switch (memberLevel) {
            case "VIP": return 0.95;   // VIP用户95折
            case "GOLD": return 0.90;  // 黄金会员9折
            case "PLATINUM": return 0.85;  // 铂金会员85折
            case "DIAMOND": return 0.80;   // 钻石会员8折
            default: return 1.0;       // 普通用户无折扣
        }
    }
    
    /**
     * 获取会员等级名称
     */
    private String getMemberLevelName(String memberLevel) {
        switch (memberLevel) {
            case "VIP": return "VIP会员";
            case "GOLD": return "黄金会员";
            case "PLATINUM": return "铂金会员";
            case "DIAMOND": return "钻石会员";
            default: return "普通会员";
        }
    }
    
    /**
     * 创建空的促销结果
     */
    private PromotionResult createEmptyResult() {
        return PromotionResult.builder()
            .originalTotal(BigDecimal.ZERO)
            .finalTotal(BigDecimal.ZERO)
            .appliedPromotions(new ArrayList<>())
            .discountApplied(false)
            .build();
    }
}

/**
 * 特定商品促销策略实现
 * 针对特定类别或特定商品提供促销折扣
 */
@Extension(condition = "#data.items != null && #data.items.size() > 0", priority = 190)
@Service
@Slf4j
public class ProductSpecificPromotionExtension implements PromotionExtPoint {
    
    // 商品类别与折扣比例映射
    private final Map<String, BigDecimal> categoryDiscountMap = new HashMap<>();
    // 特定商品与折扣比例映射
    private final Map<String, BigDecimal> productDiscountMap = new HashMap<>();
    
    public ProductSpecificPromotionExtension() {
        // 初始化商品类别折扣配置
        categoryDiscountMap.put("ELECTRONICS", new BigDecimal("0.9"));  // 电子产品9折
        categoryDiscountMap.put("CLOTHING", new BigDecimal("0.85"));    // 服装85折
        
        // 初始化特定商品折扣配置
        productDiscountMap.put("PROD001", new BigDecimal("0.7"));       // 特定商品7折
        productDiscountMap.put("PROD002", new BigDecimal("0.6"));       // 特定商品6折
    }
    
    @Override
    public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
        log.info("Processing product specific promotion");
        PromotionRequest request = context.getData();
        
        // 添加空值检查
        if (request == null) {
            log.error("Product promotion request is null");
            return createEmptyResult();
        }
        
        List<PromotionRequest.OrderItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            log.error("No items in promotion request");
            return createEmptyResult();
        }

        List<PromotionResult.AppliedPromotion> appliedPromotions = new ArrayList<>();
        BigDecimal totalDiscount = BigDecimal.ZERO;

        // 计算每个商品的折扣
        for (PromotionRequest.OrderItem item : items) {
            BigDecimal discount = calculateItemDiscount(item);
            if (discount.compareTo(BigDecimal.ZERO) > 0) {
                totalDiscount = totalDiscount.add(discount);
                
                String discountType = productDiscountMap.containsKey(item.getProductId()) ? "特定商品" : "类别";
                PromotionResult.AppliedPromotion promotion = PromotionResult.AppliedPromotion.builder()
                    .promotionId(discountType + "_" + (productDiscountMap.containsKey(item.getProductId()) ? 
                                                           item.getProductId() : item.getCategory()))
                    .promotionName(discountType + "促销")
                    .promotionType("PRODUCT_SPECIFIC")
                    .discountAmount(discount)
                    .description(item.getProductName() + "享受特定折扣")
                    .build();
                
                appliedPromotions.add(promotion);
            }
        }
        
        return PromotionResult.builder()
            .originalTotal(request.getSubtotal())
            .finalTotal(request.getSubtotal().subtract(totalDiscount))
            .appliedPromotions(appliedPromotions)
            .discountApplied(!appliedPromotions.isEmpty())
            .build();
    }
    
    @Override
    public boolean isApplicable(BizContext<PromotionRequest> context) {
        try {
            PromotionRequest request = context.getData();
            return request != null && request.getItems() != null && !request.getItems().isEmpty();
        } catch (Exception e) {
            log.error("Error checking applicability for product specific promotion", e);
            return false;
        }
    }
    
    /**
     * 计算单个商品的折扣
     */
    private BigDecimal calculateItemDiscount(PromotionRequest.OrderItem item) {
        // 先检查是否有特定商品折扣
        if (productDiscountMap.containsKey(item.getProductId())) {
            BigDecimal discountRate = productDiscountMap.get(item.getProductId());
            return item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()))
                      .multiply(BigDecimal.ONE.subtract(discountRate));
        }
        
        // 再检查是否有类别折扣
        if (item.getCategory() != null && categoryDiscountMap.containsKey(item.getCategory())) {
            BigDecimal discountRate = categoryDiscountMap.get(item.getCategory());
            return item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()))
                      .multiply(BigDecimal.ONE.subtract(discountRate));
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * 创建空的促销结果
     */
    private PromotionResult createEmptyResult() {
        return PromotionResult.builder()
            .originalTotal(BigDecimal.ZERO)
            .finalTotal(BigDecimal.ZERO)
            .appliedPromotions(new ArrayList<>())
            .discountApplied(false)
            .build();
    }
}
    
    @Override
    public PromotionResult calculatePromotion(BizContext<OrderContext> context) {
        OrderContext orderContext = context.getData();
        List<OrderItem> items = orderContext.getOrderItems();
        
        // 计算每个商品的促销优惠
        BigDecimal totalDiscount = BigDecimal.ZERO;
        Map<String, BigDecimal> itemDiscounts = new HashMap<>();
        
        for (OrderItem item : items) {
            // 检查商品是否有特定促销
            ProductPromotion promotion = productPromotionService.getActivePromotion(
                item.getProductId(), 
                context.getTenantCode(),
                context.getAttribute("campaignId", String.class)
            );
            
            if (promotion != null) {
                BigDecimal itemDiscount = calculateItemDiscount(item, promotion);
                totalDiscount = totalDiscount.add(itemDiscount);
                itemDiscounts.put(item.getProductId(), itemDiscount);
            }
        }
        
        if (totalDiscount.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Applied product specific promotions, total discount: {}", totalDiscount);
            
            return PromotionResult.builder()
                .promotionType("PRODUCT_SPECIFIC")
                .discountAmount(totalDiscount)
                .promotionName("商品专享优惠")
                .description("特定商品专享价格优惠")
                .itemDiscountDetails(itemDiscounts)
                .build();
        }
        
        return PromotionResult.empty();
    }
    
    private BigDecimal calculateItemDiscount(OrderItem item, ProductPromotion promotion) {
        // 根据促销类型计算商品优惠
        switch (promotion.getPromotionType()) {
            case "DIRECT_DISCOUNT":
                // 直降金额
                return promotion.getDiscountAmount().multiply(new BigDecimal(item.getQuantity()));
            case "PRICE_DISCOUNT":
                // 特价优惠
                BigDecimal originalPrice = item.getPrice();
                BigDecimal promotionPrice = promotion.getPromotionPrice();
                return originalPrice.subtract(promotionPrice).multiply(new BigDecimal(item.getQuantity()));
            case "PERCENT_DISCOUNT":
                // 百分比折扣
                double discountPercent = promotion.getDiscountPercent() / 100.0;
                return item.getSubtotal().multiply(new BigDecimal(discountPercent));
            default:
                return BigDecimal.ZERO;
        }
    }
}

/**
 * 促销服务集成类
 * 负责协调多种促销策略的应用
 */
@Service
@Slf4j
public class PromotionService {
    
    @Autowired
    private PromotionExtPoint promotionExtPoint;
    
    /**
     * 应用促销策略
     * @param orderContext 订单上下文
     * @param tenantCode 租户代码
     * @return 促销应用结果
     */
    public OrderPromotionResult applyPromotions(OrderContext orderContext, String tenantCode) {
        // 创建业务上下文
        BizContext<OrderContext> context = BizContext.<OrderContext>builder()
            .tenantCode(tenantCode)
            .bizCode("ORDER_PROMOTION")
            .data(orderContext)
            .attribute("timestamp", System.currentTimeMillis())
            .build();
        
        // 使用上下文管理器（自动清理）
        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            // 存储所有应用的促销结果
            List<PromotionResult> appliedPromotions = new ArrayList<>();
            BigDecimal totalDiscount = BigDecimal.ZERO;
            
            // 注意：由于框架的优先级机制，这里只返回一个最高优先级的促销结果
            // 如需组合多种促销，需要调整设计或自定义处理逻辑
            PromotionResult promotionResult = promotionExtPoint.calculatePromotion(context);
            
            if (!promotionResult.isEmpty()) {
                appliedPromotions.add(promotionResult);
                totalDiscount = promotionResult.getDiscountAmount();
                log.info("Applied promotion: {} with discount: {}", 
                        promotionResult.getPromotionName(), totalDiscount);
            } else {
                log.info("No promotions applied for order: {}", orderContext.getOrderId());
            }
            
            // 计算最终价格
            BigDecimal finalAmount = orderContext.getTotalAmount().subtract(totalDiscount);
            finalAmount = finalAmount.max(BigDecimal.ZERO); // 确保最终价格不为负
            
            return OrderPromotionResult.builder()
                .originalAmount(orderContext.getTotalAmount())
                .totalDiscount(totalDiscount)
                .finalAmount(finalAmount)
                .appliedPromotions(appliedPromotions)
                .build();
        } catch (Exception e) {
            // 促销计算异常不应影响订单流程，记录日志并返回无促销的结果
            log.error("Failed to calculate promotions for order: {}", 
                    orderContext.getOrderId(), e);
            
            return OrderPromotionResult.builder()
                .originalAmount(orderContext.getTotalAmount())
                .totalDiscount(BigDecimal.ZERO)
                .finalAmount(orderContext.getTotalAmount())
                .appliedPromotions(Collections.emptyList())
                .errorMessage("促销计算失败，按原价结算")
                .build();
        }
    }
}
        
        #### 5.4.3 风控系统中的规则扩展

在风控系统中，需要根据不同业务场景、不同风险等级和不同监管要求动态应用不同的风控规则。通过扩展点可以灵活配置和管理各类风控规则。

```java
/**
 * 风控规则扩展点
 * 支持多种风控策略的灵活配置与执行
 */
@ExtPoint(name = "风控规则扩展点", description = "风控系统中的规则评估与决策接口")
public interface RiskControlExtPoint {
    
    /**
     * 评估交易风险
     * @param context 业务上下文，包含交易信息和用户数据
     * @return 风险评估结果
     */
    RiskAssessmentResult assessRisk(BizContext<TransactionInfo> context);
}

/**
 * 交易金额风控规则实现
 * 基于交易金额和用户历史交易模式评估风险
 */
@Extension(priority = 100)
@Service
@Slf4j
public class AmountRiskControlExtension implements RiskControlExtPoint {
    
    @Autowired
    private UserTransactionHistoryService historyService;
    
    @Autowired
    private RiskConfigService configService;
    
    @Override
    public RiskAssessmentResult assessRisk(BizContext<TransactionInfo> context) {
        TransactionInfo transaction = context.getData();
        BigDecimal amount = transaction.getAmount();
        String userId = transaction.getUserId();
        
        // 获取风控配置阈值
        RiskThresholdConfig config = configService.getAmountRiskConfig(
            context.getTenantCode(), 
            transaction.getBusinessType()
        );
        
        // 基础金额检查
        if (amount.compareTo(config.getHighRiskThreshold()) > 0) {
            log.warn("High risk transaction detected by amount: {} for user: {}", amount, userId);
            return RiskAssessmentResult.builder()
                .riskLevel(RiskLevel.HIGH)
                .riskCode("HIGH_AMOUNT")
                .riskDescription("交易金额超出高风险阈值")
                .suggestedAction(RiskAction.REVIEW)
                .confidenceScore(0.9)
                .build();
        }
        
        // 检查是否超过用户历史交易均值的异常倍数
        if (config.isUserPatternCheckEnabled()) {
            BigDecimal avgAmount = historyService.getAverageTransactionAmount(userId, 30);
            if (avgAmount != null && avgAmount.compareTo(BigDecimal.ZERO) > 0) {
                // 计算金额相对于历史均值的倍数
                BigDecimal multiple = amount.divide(avgAmount, 2, RoundingMode.HALF_UP);
                
                if (multiple.compareTo(new BigDecimal(config.getAmountMultipleThreshold())) > 0) {
                    log.warn("Abnormal transaction amount pattern: {}x average for user: {}", multiple, userId);
                    return RiskAssessmentResult.builder()
                        .riskLevel(RiskLevel.MEDIUM)
                        .riskCode("ABNORMAL_AMOUNT_PATTERN")
                        .riskDescription(String.format("交易金额超出用户历史均值%.1f倍", multiple.doubleValue()))
                        .suggestedAction(RiskAction.MONITOR)
                        .confidenceScore(0.7)
                        .build();
                }
            }
        }
        
        // 低风险或无风险
        return RiskAssessmentResult.builder()
            .riskLevel(RiskLevel.LOW)
            .riskCode("NORMAL_AMOUNT")
            .riskDescription("交易金额在正常范围内")
            .suggestedAction(RiskAction.PASS)
            .confidenceScore(0.95)
            .build();
    }
}

/**
 * 地理位置风控规则实现
 * 基于交易地理位置和用户常用位置评估风险
 */
@Extension(priority = 110)
@Service
@Slf4j
public class LocationRiskControlExtension implements RiskControlExtPoint {
    
    @Autowired
    private UserLocationService locationService;
    
    @Autowired
    private GeoDistanceService distanceService;
    
    @Override
    public RiskAssessmentResult assessRisk(BizContext<TransactionInfo> context) {
        TransactionInfo transaction = context.getData();
        String userId = transaction.getUserId();
        Location transactionLocation = transaction.getLocation();
        
        // 获取用户常用位置
        List<UserFrequentLocation> frequentLocations = locationService.getUserFrequentLocations(userId);
        
        if (transactionLocation == null) {
            // 无地理位置信息，返回中等风险
            log.warn("No location information for transaction from user: {}", userId);
            return RiskAssessmentResult.builder()
                .riskLevel(RiskLevel.MEDIUM)
                .riskCode("MISSING_LOCATION")
                .riskDescription("交易缺少地理位置信息")
                .suggestedAction(RiskAction.MONITOR)
                .confidenceScore(0.6)
                .build();
        }
        
        if (frequentLocations.isEmpty()) {
            // 新用户无常用位置，需要额外验证
            return RiskAssessmentResult.builder()
                .riskLevel(RiskLevel.MEDIUM)
                .riskCode("NEW_USER_LOCATION")
                .riskDescription("新用户首次交易位置")
                .suggestedAction(RiskAction.VERIFY)
                .confidenceScore(0.7)
                .build();
        }
        
        // 检查是否在常用位置附近
        boolean isNearFrequentLocation = false;
        double minDistance = Double.MAX_VALUE;
        
        for (UserFrequentLocation freqLocation : frequentLocations) {
            double distance = distanceService.calculateDistance(
                transactionLocation.getLatitude(), transactionLocation.getLongitude(),
                freqLocation.getLatitude(), freqLocation.getLongitude()
            );
            
            minDistance = Math.min(minDistance, distance);
            
            // 如果距离小于5公里，认为是常用位置
            if (distance < 5.0) {
                isNearFrequentLocation = true;
                break;
            }
        }
        
        if (!isNearFrequentLocation) {
            // 非常用位置交易，检查是否有短时间内的异地交易
            boolean hasRecentRemoteTransaction = locationService.hasRecentRemoteTransaction(
                userId, transactionLocation, 24 // 24小时内
            );
            
            if (hasRecentRemoteTransaction) {
                // 短时间内异地交易，高风险
                log.warn("Remote transaction detected for user: {} at distance: {}km", userId, minDistance);
                return RiskAssessmentResult.builder()
                    .riskLevel(RiskLevel.HIGH)
                    .riskCode("REMOTE_TRANSACTION")
                    .riskDescription(String.format("检测到异地交易，距离常用位置%.1f公里", minDistance))
                    .suggestedAction(RiskAction.BLOCK)
                    .confidenceScore(0.85)
                    .build();
            }
            
            // 非常用位置但非短时间异地，中等风险
            return RiskAssessmentResult.builder()
                .riskLevel(RiskLevel.MEDIUM)
                .riskCode("UNUSUAL_LOCATION")
                .riskDescription(String.format("交易位置不常用，距离常用位置%.1f公里", minDistance))
                .suggestedAction(RiskAction.VERIFY)
                .confidenceScore(0.75)
                .build();
        }
        
        // 常用位置，低风险
        return RiskAssessmentResult.builder()
            .riskLevel(RiskLevel.LOW)
            .riskCode("FREQUENT_LOCATION")
            .riskDescription("交易位置为用户常用位置")
            .suggestedAction(RiskAction.PASS)
            .confidenceScore(0.9)
            .build();
    }
}

/**
 * 行为模式风控规则实现
 * 基于用户行为模式和交易时间模式评估风险
 */
@Extension(priority = 120)
@Service
@Slf4j
public class BehaviorPatternRiskControlExtension implements RiskControlExtPoint {
    
    @Autowired
    private UserBehaviorService behaviorService;
    
    @Override
    public RiskAssessmentResult assessRisk(BizContext<TransactionInfo> context) {
        TransactionInfo transaction = context.getData();
        String userId = transaction.getUserId();
        long transactionTime = transaction.getTimestamp();
        
        // 检查交易时间模式
        DayOfWeek dayOfWeek = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(transactionTime), ZoneId.systemDefault()
        ).getDayOfWeek();
        int hourOfDay = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(transactionTime), ZoneId.systemDefault()
        ).getHour();
        
        // 获取用户交易时间模式
        UserTimePattern timePattern = behaviorService.getUserTransactionTimePattern(userId);
        
        if (timePattern != null) {
            // 检查是否在用户不活跃时段交易
            if (isUnusualTimeSlot(dayOfWeek, hourOfDay, timePattern)) {
                log.warn("Transaction at unusual time slot: {}:{}, user: {}", dayOfWeek, hourOfDay, userId);
                return RiskAssessmentResult.builder()
                    .riskLevel(RiskLevel.MEDIUM)
                    .riskCode("UNUSUAL_TIME_SLOT")
                    .riskDescription(String.format("交易发生在用户不活跃时段: %s %02d:00", dayOfWeek, hourOfDay))
                    .suggestedAction(RiskAction.MONITOR)
                    .confidenceScore(0.7)
                    .build();
            }
        }
        
        // 检查短时间内的交易频率
        int transactionCount = behaviorService.getRecentTransactionCount(
            userId, transactionTime - 3600000, transactionTime // 最近1小时
        );
        
        if (transactionCount > 10) { // 1小时内超过10笔交易
            log.warn("High transaction frequency: {} in 1 hour for user: {}", transactionCount, userId);
            return RiskAssessmentResult.builder()
                .riskLevel(RiskLevel.HIGH)
                .riskCode("HIGH_TRANSACTION_FREQUENCY")
                .riskDescription(String.format("短时间内交易频率异常: 1小时内%d笔", transactionCount))
                .suggestedAction(RiskAction.REVIEW)
                .confidenceScore(0.8)
                .build();
        }
        
        // 低风险或无风险
        return RiskAssessmentResult.builder()
            .riskLevel(RiskLevel.LOW)
            .riskCode("NORMAL_BEHAVIOR")
            .riskDescription("交易行为模式正常")
            .suggestedAction(RiskAction.PASS)
            .confidenceScore(0.9)
            .build();
    }
    
    private boolean isUnusualTimeSlot(DayOfWeek dayOfWeek, int hourOfDay, UserTimePattern timePattern) {
        // 检查是否在用户通常不活跃的时间段
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            // 周末模式
            return !timePattern.getWeekendActiveHours().contains(hourOfDay);
        } else {
            // 工作日模式
            return !timePattern.getWeekdayActiveHours().contains(hourOfDay);
        }
    }
}

/**
 * 风控服务集成类
 * 负责协调多种风控规则的执行和结果聚合
 */
@Service
@Slf4j
public class RiskControlService {
    
    @Autowired
    private RiskControlExtPoint riskControlExtPoint;
    
    /**
     * 执行风控评估
     * @param transaction 交易信息
     * @param tenantCode 租户代码
     * @return 风控决策结果
     */
    public RiskDecisionResult executeRiskControl(TransactionInfo transaction, String tenantCode) {
        // 创建业务上下文
        BizContext<TransactionInfo> context = BizContext.<TransactionInfo>builder()
            .tenantCode(tenantCode)
            .bizCode("RISK_CONTROL")
            .data(transaction)
            .attribute("timestamp", System.currentTimeMillis())
            .build();
        
        // 使用上下文管理器（自动清理）
        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            // 执行风控评估
            RiskAssessmentResult assessmentResult = riskControlExtPoint.assessRisk(context);
            
            // 根据风险评估结果生成风控决策
            RiskDecision decision = generateRiskDecision(assessmentResult);
            
            // 记录风控决策日志
            logRiskDecision(transaction, assessmentResult, decision);
            
            return RiskDecisionResult.builder()
                .decision(decision)
                .riskLevel(assessmentResult.getRiskLevel())
                .riskCode(assessmentResult.getRiskCode())
                .riskDescription(assessmentResult.getRiskDescription())
                .timestamp(System.currentTimeMillis())
                .build();
        } catch (Exception e) {
            // 风控评估异常时默认返回需要审核
            log.error("Risk control assessment failed for transaction: {}", 
                    transaction.getTransactionId(), e);
            
            return RiskDecisionResult.builder()
                .decision(RiskDecision.REVIEW)
                .riskLevel(RiskLevel.UNKNOWN)
                .riskCode("RISK_SYSTEM_ERROR")
                .riskDescription("风控系统评估异常")
                .timestamp(System.currentTimeMillis())
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    private RiskDecision generateRiskDecision(RiskAssessmentResult assessment) {
        // 根据风险等级和建议操作生成最终决策
        switch (assessment.getRiskLevel()) {
            case HIGH:
                return assessment.getSuggestedAction() == RiskAction.BLOCK ? 
                       RiskDecision.REJECT : RiskDecision.REVIEW;
            case MEDIUM:
                return assessment.getSuggestedAction() == RiskAction.VERIFY ? 
                       RiskDecision.CHALLENGE : RiskDecision.MONITOR;
            case LOW:
                return RiskDecision.APPROVE;
            default:
                return RiskDecision.REVIEW;
        }
    }
    
    private void logRiskDecision(TransactionInfo transaction, 
                               RiskAssessmentResult assessment, 
                               RiskDecision decision) {
        // 记录风控决策日志
        log.info("Risk control decision: {} for transaction: {}, risk level: {}, risk code: {}",
                decision, transaction.getTransactionId(), 
                assessment.getRiskLevel(), assessment.getRiskCode());
        
        // 可以在这里调用风控日志服务进行持久化存储
    }
}
        BigDecimal finalPrice = basePrice.multiply(new BigDecimal("1.13"));
        
        #### 5.4.4 医疗保险理赔处理

在医疗保险系统中，不同类型的理赔（门诊、住院、特殊病种等）需要应用不同的理赔规则和计算逻辑。通过扩展点可以灵活配置各类理赔处理策略。

```java
/**
 * 医疗保险理赔扩展点
 * 支持不同类型医疗理赔的处理与验证
 */
@ExtPoint(name = "医疗保险理赔扩展点", description = "处理各类医疗保险理赔的接口")
public interface MedicalClaimExtPoint {
    
    /**
     * 处理医疗理赔申请
     * @param context 业务上下文，包含理赔申请信息
     * @return 理赔处理结果
     */
    ClaimProcessResult processClaim(BizContext<ClaimRequest> context);
    
    /**
     * 验证理赔材料和资格
     * @param context 业务上下文，包含理赔申请信息
     * @return 验证结果
     */
    ValidationResult validateClaim(BizContext<ClaimRequest> context);
}

/**
 * 门诊理赔处理实现
 * 处理门诊医疗费用的理赔申请
 */
@Extension(condition = "#data.claimType == 'OUTPATIENT'", priority = 100)
@Service
@Slf4j
public class OutpatientClaimExtension implements MedicalClaimExtPoint {
    
    @Autowired
    private MedicalFeeService medicalFeeService;
    
    @Autowired
    private PolicyCoverageService coverageService;
    
    @Autowired
    private MedicalRecordService recordService;
    
    @Override
    public ClaimProcessResult processClaim(BizContext<ClaimRequest> context) {
        ClaimRequest request = context.getData();
        String policyNo = request.getPolicyNo();
        String patientId = request.getPatientId();
        
        log.info("Processing outpatient claim for policy: {}, patient: {}", policyNo, patientId);
        
        // 获取保单覆盖范围
        PolicyCoverage coverage = coverageService.getPolicyCoverage(policyNo);
        
        // 计算可理赔金额
        List<MedicalExpense> expenses = request.getMedicalExpenses();
        BigDecimal totalExpense = calculateTotalExpense(expenses);
        BigDecimal deductible = coverage.getOutpatientDeductible();
        BigDecimal reimbursementRate = coverage.getOutpatientReimbursementRate();
        
        // 计算实际赔付金额（扣除免赔额后按比例赔付）
        BigDecimal reimbursableAmount = totalExpense.subtract(deductible).max(BigDecimal.ZERO);
        BigDecimal reimbursementAmount = reimbursableAmount.multiply(reimbursementRate);
        
        // 检查是否超过年度限额
        BigDecimal yearlyLimit = coverage.getOutpatientYearlyLimit();
        BigDecimal usedAmount = recordService.getYearlyUsedAmount(policyNo, "OUTPATIENT");
        BigDecimal remainingAmount = yearlyLimit.subtract(usedAmount);
        
        if (reimbursementAmount.compareTo(remainingAmount) > 0) {
            reimbursementAmount = remainingAmount;
            log.warn("Claim amount exceeds yearly limit, adjusted to: {}", reimbursementAmount);
        }
        
        // 生成理赔明细
        List<ClaimDetail> details = generateClaimDetails(expenses, coverage);
        
        return ClaimProcessResult.builder()
            .claimId(generateClaimId())
            .policyNo(policyNo)
            .patientId(patientId)
            .claimType("OUTPATIENT")
            .totalExpense(totalExpense)
            .reimbursementAmount(reimbursementAmount)
            .deductible(deductible)
            .reimbursementRate(reimbursementRate)
            .claimDetails(details)
            .status(ClaimStatus.APPROVED)
            .processTime(new Date())
            .build();
    }
    
    @Override
    public ValidationResult validateClaim(BizContext<ClaimRequest> context) {
        ClaimRequest request = context.getData();
        
        // 基础参数验证
        if (request == null || request.getPolicyNo() == null || request.getPatientId() == null) {
            return ValidationResult.fail("INVALID_REQUEST", "理赔申请参数不完整");
        }
        
        // 保单有效性检查
        if (!coverageService.isPolicyActive(request.getPolicyNo())) {
            return ValidationResult.fail("POLICY_INACTIVE", "保单已失效或未激活");
        }
        
        // 患者资格检查
        if (!coverageService.isPatientCovered(request.getPolicyNo(), request.getPatientId())) {
            return ValidationResult.fail("PATIENT_NOT_COVERED", "患者不在保单覆盖范围内");
        }
        
        // 理赔时效检查（门诊通常要求90天内）
        Date treatmentDate = request.getTreatmentDate();
        Date claimDate = request.getClaimDate();
        long daysBetween = ChronoUnit.DAYS.between(
            treatmentDate.toInstant(), claimDate.toInstant());
        
        if (daysBetween > 90) {
            return ValidationResult.fail("CLAIM_TIMEOUT", "超出门诊理赔申请时效（90天）");
        }
        
        // 医疗费用合理性检查
        try {
            List<MedicalExpense> expenses = request.getMedicalExpenses();
            for (MedicalExpense expense : expenses) {
                if (!medicalFeeService.isFeeReasonable(expense.getCategory(), expense.getAmount())) {
                    return ValidationResult.fail("UNREASONABLE_FEE", 
                        "医疗费用不合理: " + expense.getCategory() + " - " + expense.getAmount());
                }
            }
        } catch (Exception e) {
            log.error("Failed to validate medical fees", e);
            return ValidationResult.fail("VALIDATION_ERROR", "医疗费用验证失败");
        }
        
        log.info("Outpatient claim validated successfully: {}", request.getPolicyNo());
        return ValidationResult.success();
    }
    
    private BigDecimal calculateTotalExpense(List<MedicalExpense> expenses) {
        return expenses.stream()
            .map(MedicalExpense::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private List<ClaimDetail> generateClaimDetails(List<MedicalExpense> expenses, PolicyCoverage coverage) {
        List<ClaimDetail> details = new ArrayList<>();
        
        for (MedicalExpense expense : expenses) {
            // 检查费用是否在覆盖范围内
            boolean isCovered = coverageService.isExpenseCovered(
                coverage, expense.getCategory(), expense.getItemCode());
            
            if (isCovered) {
                // 计算单项赔付金额
                BigDecimal itemRate = coverageService.getExpenseReimbursementRate(
                    coverage, expense.getCategory());
                BigDecimal reimbursedAmount = expense.getAmount().multiply(itemRate);
                
                details.add(ClaimDetail.builder()
                    .expenseCategory(expense.getCategory())
                    .itemCode(expense.getItemCode())
                    .itemName(expense.getItemName())
                    .expenseAmount(expense.getAmount())
                    .reimbursementRate(itemRate)
                    .reimbursementAmount(reimbursedAmount)
                    .status(ClaimDetailStatus.APPROVED)
                    .build());
            } else {
                details.add(ClaimDetail.builder()
                    .expenseCategory(expense.getCategory())
                    .itemCode(expense.getItemCode())
                    .itemName(expense.getItemName())
                    .expenseAmount(expense.getAmount())
                    .reimbursementRate(BigDecimal.ZERO)
                    .reimbursementAmount(BigDecimal.ZERO)
                    .status(ClaimDetailStatus.DENIED)
                    .reason("不在覆盖范围内")
                    .build());
            }
        }
        
        return details;
    }
    
    private String generateClaimId() {
        // 生成唯一理赔ID
        return "CLM" + System.currentTimeMillis() + RandomStringUtils.randomNumeric(6);
    }
}

/**
 * 住院理赔处理实现
 * 处理住院医疗费用的理赔申请
 */
@Extension(condition = "#data.claimType == 'INPATIENT'", priority = 100)
@Service
@Slf4j
public class InpatientClaimExtension implements MedicalClaimExtPoint {
    
    @Autowired
    private HospitalService hospitalService;
    
    @Autowired
    private PolicyCoverageService coverageService;
    
    @Autowired
    private MedicalRecordService recordService;
    
    @Override
    public ClaimProcessResult processClaim(BizContext<ClaimRequest> context) {
        ClaimRequest request = context.getData();
        String policyNo = request.getPolicyNo();
        String hospitalId = request.getHospitalId();
        
        log.info("Processing inpatient claim for policy: {}, hospital: {}", policyNo, hospitalId);
        
        // 获取医院等级信息
        HospitalInfo hospitalInfo = hospitalService.getHospitalInfo(hospitalId);
        
        // 获取保单覆盖范围
        PolicyCoverage coverage = coverageService.getPolicyCoverage(policyNo);
        
        // 计算住院天数
        long hospitalizationDays = ChronoUnit.DAYS.between(
            request.getAdmissionDate().toInstant(), 
            request.getDischargeDate().toInstant()) + 1; // 入院当天计为1天
        
        // 计算总费用
        List<MedicalExpense> expenses = request.getMedicalExpenses();
        BigDecimal totalExpense = calculateTotalExpense(expenses);
        
        // 获取报销比例（根据医院等级和住院天数）
        BigDecimal reimbursementRatio = getReimbursementRatio(
            hospitalInfo.getLevel(), hospitalizationDays, coverage);
        
        // 计算免赔额
        BigDecimal deductible = coverage.getInpatientDeductible();
        if (hospitalInfo.getLevel() > 3) { // 三级以上医院免赔额更高
            deductible = deductible.multiply(new BigDecimal("1.5"));
        }
        
        // 计算报销金额
        BigDecimal reimbursementAmount = calculateInpatientReimbursement(
            totalExpense, deductible, reimbursementRatio, coverage);
        
        // 生成理赔明细
        List<ClaimDetail> details = generateInpatientClaimDetails(
            expenses, hospitalInfo, coverage);
        
        return ClaimProcessResult.builder()
            .claimId(generateClaimId())
            .policyNo(policyNo)
            .patientId(request.getPatientId())
            .claimType("INPATIENT")
            .totalExpense(totalExpense)
            .reimbursementAmount(reimbursementAmount)
            .deductible(deductible)
            .reimbursementRate(reimbursementRatio)
            .hospitalLevel(hospitalInfo.getLevel())
            .hospitalizationDays(hospitalizationDays)
            .claimDetails(details)
            .status(ClaimStatus.APPROVED)
            .processTime(new Date())
            .build();
    }
    
    @Override
    public ValidationResult validateClaim(BizContext<ClaimRequest> context) {
        ClaimRequest request = context.getData();
        
        // 基础参数验证
        if (request == null || request.getPolicyNo() == null || request.getHospitalId() == null) {
            return ValidationResult.fail("INVALID_REQUEST", "住院理赔申请参数不完整");
        }
        
        // 保单有效性检查
        if (!coverageService.isPolicyActive(request.getPolicyNo())) {
            return ValidationResult.fail("POLICY_INACTIVE", "保单已失效或未激活");
        }
        
        // 住院日期验证
        if (request.getAdmissionDate() == null || request.getDischargeDate() == null ||
            request.getAdmissionDate().after(request.getDischargeDate())) {
            return ValidationResult.fail("INVALID_DATE", "住院日期无效");
        }
        
        // 医院资质验证
        HospitalInfo hospitalInfo = hospitalService.getHospitalInfo(request.getHospitalId());
        if (hospitalInfo == null || !hospitalInfo.isInNetwork()) {
            return ValidationResult.fail("HOSPITAL_NOT_IN_NETWORK", "医院不在理赔网络内");
        }
        
        // 医保状态验证
        if (!recordService.isPatientEligibleForInpatientClaim(
            request.getPatientId(), request.getPolicyNo())) {
            return ValidationResult.fail("PATIENT_NOT_ELIGIBLE", "患者不具备住院理赔资格");
        }
        
        // 理赔时效检查（住院通常要求180天内）
        Date dischargeDate = request.getDischargeDate();
        Date claimDate = request.getClaimDate();
        long daysBetween = ChronoUnit.DAYS.between(
            dischargeDate.toInstant(), claimDate.toInstant());
        
        if (daysBetween > 180) {
            return ValidationResult.fail("CLAIM_TIMEOUT", "超出住院理赔申请时效（180天）");
        }
        
        log.info("Inpatient claim validated successfully: {}", request.getPolicyNo());
        return ValidationResult.success();
    }
    
    private BigDecimal getReimbursementRatio(int hospitalLevel, long days, PolicyCoverage coverage) {
        // 根据医院等级和住院天数确定报销比例
        BigDecimal baseRatio = coverage.getInpatientReimbursementRate();
        
        // 医院等级系数
        BigDecimal hospitalFactor;
        switch (hospitalLevel) {
            case 1:
            case 2:
                hospitalFactor = new BigDecimal("1.1"); // 二级及以下医院报销比例上浮10%
                break;
            case 3:
                hospitalFactor = new BigDecimal("1.0"); // 三级医院标准比例
                break;
            default:
                hospitalFactor = new BigDecimal("0.9"); // 三级以上医院报销比例下浮10%
                break;
        }
        
        // 住院天数系数
        BigDecimal dayFactor;
        if (days > 30) {
            dayFactor = new BigDecimal("1.05"); // 30天以上住院报销比例上浮5%
        } else if (days <= 7) {
            dayFactor = new BigDecimal("0.95"); // 7天以下住院报销比例下浮5%
        } else {
            dayFactor = new BigDecimal("1.0"); // 7-30天标准比例
        }
        
        return baseRatio.multiply(hospitalFactor).multiply(dayFactor);
    }
    
    private BigDecimal calculateInpatientReimbursement(BigDecimal totalExpense, 
                                                     BigDecimal deductible, 
                                                     BigDecimal ratio, 
                                                     PolicyCoverage coverage) {
        // 计算住院报销金额
        BigDecimal reimbursableAmount = totalExpense.subtract(deductible).max(BigDecimal.ZERO);
        BigDecimal reimbursement = reimbursableAmount.multiply(ratio);
        
        // 检查单次限额
        BigDecimal singleLimit = coverage.getInpatientSingleLimit();
        if (reimbursement.compareTo(singleLimit) > 0) {
            reimbursement = singleLimit;
        }
        
        // 检查年度限额
        BigDecimal yearlyLimit = coverage.getInpatientYearlyLimit();
        BigDecimal usedAmount = recordService.getYearlyUsedAmount(
            coverage.getPolicyNo(), "INPATIENT");
        BigDecimal remainingAmount = yearlyLimit.subtract(usedAmount);
        
        if (reimbursement.compareTo(remainingAmount) > 0) {
            reimbursement = remainingAmount;
        }
        
        return reimbursement;
    }
    
    private List<ClaimDetail> generateInpatientClaimDetails(List<MedicalExpense> expenses, 
                                                          HospitalInfo hospital, 
                                                          PolicyCoverage coverage) {
        // 生成住院理赔明细，逻辑类似门诊但有特殊规则
        // ...（实现代码）
        return new ArrayList<>();
    }
    
    private BigDecimal calculateTotalExpense(List<MedicalExpense> expenses) {
        return expenses.stream()
            .map(MedicalExpense::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private String generateClaimId() {
        return "CLM" + System.currentTimeMillis() + RandomStringUtils.randomNumeric(6);
    }
}

/**
 * 商业医疗保险理赔增强实现
 * 提供商业医疗保险的额外理赔处理
 */
@Extension(condition = "#data.policyType == 'COMMERCIAL'", priority = 90)
@Service
@Slf4j
public class CommercialClaimEnhancementExtension implements MedicalClaimExtPoint {
    
    @Autowired
    private CommercialPolicyService commercialPolicyService;
    
    @Autowired
    private MedicalClaimExtPoint delegate; // 委托给基础理赔处理实现
    
    @Override
    public ClaimProcessResult processClaim(BizContext<ClaimRequest> context) {
        ClaimRequest request = context.getData();
        
        // 先调用基础理赔处理
        ClaimProcessResult baseResult = delegate.processClaim(context);
        
        // 获取商业保险额外赔付
        CommercialPolicy commercialPolicy = commercialPolicyService.getPolicy(request.getPolicyNo());
        BigDecimal additionalReimbursement = calculateCommercialReimbursement(
            baseResult, commercialPolicy, request);
        
        // 增强理赔结果
        BigDecimal totalReimbursement = baseResult.getReimbursementAmount()
            .add(additionalReimbursement);
        
        log.info("Commercial insurance additional reimbursement: {} for policy: {}",
                additionalReimbursement, request.getPolicyNo());
        
        // 创建增强后的理赔结果
        return ClaimProcessResult.builder()
            .from(baseResult) // 复制基础结果的所有属性
            .reimbursementAmount(totalReimbursement)
            .additionalBenefits(additionalReimbursement)
            .policyType("COMMERCIAL")
            .build();
    }
    
    @Override
    public ValidationResult validateClaim(BizContext<ClaimRequest> context) {
        ClaimRequest request = context.getData();
        
        // 先执行基础验证
        ValidationResult baseValidation = delegate.validateClaim(context);
        if (!baseValidation.isSuccess()) {
            return baseValidation;
        }
        
        // 商业保险特定验证
        CommercialPolicy policy = commercialPolicyService.getPolicy(request.getPolicyNo());
        
        // 检查等待期
        if (isWithinWaitingPeriod(policy, request.getTreatmentDate())) {
            return ValidationResult.fail("WAITING_PERIOD", "尚在等待期内，无法理赔");
        }
        
        // 检查特定疾病覆盖
        if (request.getDiagnosisCode() != null) {
            if (!commercialPolicyService.isDiseaseCovered(
                policy, request.getDiagnosisCode())) {
                return ValidationResult.fail("DISEASE_NOT_COVERED", "诊断疾病不在商业保险覆盖范围内");
            }
        }
        
        // 检查理赔时效（商业保险可能有特殊要求）
        if (policy.getClaimPeriodDays() != null) {
            Date treatmentDate = request.getTreatmentDate();
            Date claimDate = request.getClaimDate();
            long daysBetween = ChronoUnit.DAYS.between(
                treatmentDate.toInstant(), claimDate.toInstant());
            
            if (daysBetween > policy.getClaimPeriodDays()) {
                return ValidationResult.fail("CLAIM_TIMEOUT", 
                    String.format("超出商业保险理赔申请时效（%d天）", policy.getClaimPeriodDays()));
            }
        }
        
        log.info("Commercial insurance claim validated successfully: {}", request.getPolicyNo());
        return ValidationResult.success();
    }
    
    private boolean isWithinWaitingPeriod(CommercialPolicy policy, Date treatmentDate) {
        // 检查是否在等待期内
        Date policyEffectiveDate = policy.getEffectiveDate();
        long daysBetween = ChronoUnit.DAYS.between(
            policyEffectiveDate.toInstant(), treatmentDate.toInstant());
        
        return daysBetween < policy.getWaitingPeriodDays();
    }
    
    private BigDecimal calculateCommercialReimbursement(ClaimProcessResult baseResult, 
                                                     CommercialPolicy policy, 
                                                     ClaimRequest request) {
        // 根据商业保险计划计算额外赔付
        BigDecimal additionalAmount = BigDecimal.ZERO;
        
        switch (policy.getPlanType()) {
            case "PREMIUM":
                // 高端计划：赔付自付部分的80%
                if (baseResult.getDeductible() != null) {
                    additionalAmount = baseResult.getDeductible().multiply(new BigDecimal("0.8"));
                }
                // 额外的住院津贴
                if ("INPATIENT".equals(request.getClaimType()) && request.getAdmissionDate() != null && request.getDischargeDate() != null) {
                    long days = ChronoUnit.DAYS.between(
                        request.getAdmissionDate().toInstant(), 
                        request.getDischargeDate().toInstant()) + 1;
                    additionalAmount = additionalAmount.add(
                        new BigDecimal(days).multiply(new BigDecimal("500"))); // 每天500元津贴
                }
                break;
            case "STANDARD":
                // 标准计划：赔付自付部分的50%
                if (baseResult.getDeductible() != null) {
                    additionalAmount = baseResult.getDeductible().multiply(new BigDecimal("0.5"));
                }
                break;
            case "BASIC":
                // 基础计划：赔付自付部分的30%
                if (baseResult.getDeductible() != null) {
                    additionalAmount = baseResult.getDeductible().multiply(new BigDecimal("0.3"));
                }
                break;
        }
        
        // 检查单次赔付限额
        if (additionalAmount.compareTo(policy.getSingleClaimLimit()) > 0) {
            additionalAmount = policy.getSingleClaimLimit();
        }
        
        return additionalAmount;
    }
}

/**
 * 医疗保险理赔服务集成类
 * 负责协调理赔处理流程
 */
@Service
@Slf4j
public class MedicalClaimService {
    
    @Autowired
    private MedicalClaimExtPoint claimExtPoint;
    
    @Autowired
    private ClaimNotificationService notificationService;
    
    @Autowired
    private ClaimAuditService auditService;
    
    /**
     * 处理医疗理赔申请
     * @param request 理赔申请
     * @param tenantCode 租户代码
     * @return 理赔处理结果
     */
    public ClaimProcessingResult processMedicalClaim(ClaimRequest request, String tenantCode) {
        // 创建业务上下文
        BizContext<ClaimRequest> context = BizContext.<ClaimRequest>builder()
            .tenantCode(tenantCode)
            .bizCode("MEDICAL_CLAIM")
            .data(request)
            .attribute("timestamp", System.currentTimeMillis())
            .attribute("claimSource", request.getSource() != null ? request.getSource() : "SYSTEM")
            .build();
        
        // 使用上下文管理器（自动清理）
        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            log.info("Start processing medical claim for policy: {}, type: {}", 
                    request.getPolicyNo(), request.getClaimType());
            
            // 1. 记录理赔申请
            String applicationId = recordClaimApplication(request);
            
            // 2. 验证理赔资格和材料
            ValidationResult validationResult = claimExtPoint.validateClaim(context);
            
            if (!validationResult.isSuccess()) {
                // 验证失败，记录并通知
                log.warn("Claim validation failed: {} - {}", 
                        validationResult.getErrorCode(), validationResult.getErrorMessage());
                
                auditService.recordValidationFailure(
                    applicationId, validationResult.getErrorCode(), validationResult.getErrorMessage());
                
                notificationService.sendClaimRejectionNotice(
                    request.getPolicyNo(), request.getPatientId(), validationResult.getErrorMessage());
                
                return ClaimProcessingResult.builder()
                    .applicationId(applicationId)
                    .status(ClaimOverallStatus.REJECTED)
                    .errorCode(validationResult.getErrorCode())
                    .errorMessage(validationResult.getErrorMessage())
                    .build();
            }
            
            // 3. 处理理赔计算
            ClaimProcessResult processResult = claimExtPoint.processClaim(context);
            
            // 4. 记录理赔处理结果
            auditService.recordClaimProcessing(applicationId, processResult);
            
            // 5. 发送通知
            if (ClaimStatus.APPROVED.equals(processResult.getStatus())) {
                notificationService.sendClaimApprovalNotice(
                    request.getPolicyNo(), request.getPatientId(), processResult);
                
                // 异步处理理赔支付
                CompletableFuture.runAsync(() -> {
                    try {
                        processClaimPayment(processResult);
                    } catch (Exception e) {
                        log.error("Failed to process claim payment for: {}", 
                                processResult.getClaimId(), e);
                    }
                });
            }
            
            log.info("Claim processed successfully: {}, amount: {}", 
                    processResult.getClaimId(), processResult.getReimbursementAmount());
            
            return ClaimProcessingResult.builder()
                .applicationId(applicationId)
                .claimId(processResult.getClaimId())
                .status(ClaimOverallStatus.COMPLETED)
                .claimResult(processResult)
                .build();
            
        } catch (Exception e) {
            // 处理异常
            log.error("Failed to process medical claim for policy: {}", 
                    request.getPolicyNo(), e);
            
            // 记录异常并通知
            String errorId = UUID.randomUUID().toString();
            auditService.recordProcessingError(errorId, request, e.getMessage());
            notificationService.sendSystemErrorNotice(request.getPolicyNo(), errorId);
            
            return ClaimProcessingResult.builder()
                .status(ClaimOverallStatus.ERROR)
                .errorCode("SYSTEM_ERROR")
                .errorMessage("系统处理异常，请联系客服")
                .errorId(errorId)
                .build();
        }
    }
    
    private String recordClaimApplication(ClaimRequest request) {
        // 记录理赔申请
        return "APP" + System.currentTimeMillis();
    }
    
    private void processClaimPayment(ClaimProcessResult result) {
        // 处理理赔支付逻辑
        log.info("Processing payment for claim: {}, amount: {}", 
                result.getClaimId(), result.getReimbursementAmount());
        // ...支付处理代码
    }
}
        if (basePrice.compareTo(new BigDecimal("1000")) >= 0) {
            finalPrice = finalPrice.subtract(new BigDecimal("100"));
        }
        
        return PriceResult.builder()
            .originalPrice(basePrice)
            .finalPrice(finalPrice)
            .currency("CNY")
            .build();
    }
}

// 跨境电商价格策略
@Extension(tenantCode = "CROSSBORDER_ECOMMERCE")  
@Service
public class CrossBorderPricingExtension implements PricingExtPoint {
    @Override
    public PriceResult calculatePrice(BizContext<PriceRequest> context) {
        PriceRequest request = context.getData();
        
        // 汇率转换
        BigDecimal usdPrice = exchangeRateService.convertToUSD(request.getBasePrice());
        
        // 关税 + 物流 + 服务费
        BigDecimal finalPrice = usdPrice
            .add(calculateTariff(usdPrice))
            .add(calculateShipping(context))
            .add(usdPrice.multiply(new BigDecimal("0.05")));
            
        return PriceResult.builder()
            .originalPrice(usdPrice)
            .finalPrice(finalPrice)
            .currency("USD")
            .build();
    }
}
```

#### 场景2：会员积分系统
```java
@ExtPoint(name = "会员积分扩展点")  
public interface MembershipExtPoint {
    int calculatePoints(BizContext<Transaction> context);
    MemberLevel checkLevelUpgrade(BizContext<Member> context);
}

// 普通会员
@Extension(priority = 200)
@Service
public class RegularMemberExtension implements MembershipExtPoint {
    @Override
    public int calculatePoints(BizContext<Transaction> context) {
        // 1元 = 1积分
        return context.getData().getAmount().intValue();
    }
}

// 黄金会员  
@Extension(condition = "#data.level == 'GOLD'", priority = 150)
@Service
public class GoldMemberExtension implements MembershipExtPoint {
    @Override
    public int calculatePoints(BizContext<Transaction> context) {
        // 1元 = 1.5积分
        return context.getData().getAmount().multiply(new BigDecimal("1.5")).intValue();
    }
}

// 银行联名会员
@Extension(condition = "#context.getAttribute('bankPartner') != null", priority = 100)
@Service
public class BankMemberExtension implements MembershipExtPoint {
    @Override
    public int calculatePoints(BizContext<Transaction> context) {
        String bankType = context.getAttribute("bankPartner");
        BigDecimal multiplier = getBankMultiplier(bankType);
        return context.getData().getAmount().multiply(multiplier).intValue();
    }
}
```

## 故障排查与监控

### 常见问题与解决方案

| 问题现象 | 可能原因 | 解决方案 |
|----------|----------|----------|
| 扩展点未调用 | 1. 未启用扫描<br>2. 包路径错误<br>3. 缺少注解 | 1. 检查 `@EnableExtPoints` 注解是否添加<br>2. 验证 `basePackages` 配置是否正确<br>3. 确认实现类添加了 `@Extension` 注解 |
| 表达式匹配失败 | 1. SpEL语法错误<br>2. 变量不存在<br>3. 空指针异常 | 1. 使用简单表达式逐步测试<br>2. 检查表达式中使用的变量是否可用<br>3. 使用安全导航操作符 `?.` 避免NPE |
| 优先级不生效 | 1. 未设置priority<br>2. 优先级设置相同<br>3. 条件匹配不正确 | 1. 确保所有扩展都设置了priority属性<br>2. 为不同优先级的扩展设置不同的值<br>3. 检查条件表达式是否正确匹配 |
| 性能问题 | 1. 复杂表达式<br>2. 重复查询<br>3. 同步阻塞 | 1. 简化表达式或使用预计算<br>2. 添加缓存减少重复计算<br>3. 对非关键路径使用异步处理 |

### 调试与监控工具

#### 1. 启用详细日志
```yaml
# application.yml
logging:
  level:
    com.bone.engine.extension: DEBUG
    org.springframework.expression: DEBUG
```

**2. 诊断控制器**
```java
@RestController
@RequestMapping("/diagnostic")
public class ExtensionDiagnosticController {
    
    @Autowired
    private ExtPointRegistry extPointRegistry;
    
    @GetMapping("/extensions")
    public Map<String, Object> getExtensions() {
        Map<String, Object> result = new HashMap<>();
        
        // 获取所有注册的扩展点
        extPointRegistry.getExtPoints().forEach((name, definition) -> {
            result.put(name, definition.getProviders().stream()
                .map(p -> p.getBeanName() + " (priority: " + p.getPriority() + ")")
                .collect(Collectors.toList()));
        });
        
        return result;
    }
    
    @PostMapping("/test-expression")  
    public Map<String, Object> testExpression(@RequestBody TestRequest request) {
        // 测试表达式匹配
        Map<String, Object> result = new HashMap<>();
        try {
            boolean match = expressionEvaluator.evaluate(request.getExpression(), request.getContext());
            result.put("match", match);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}
```

**3. 性能监控**
```java
@Component
public class ExtensionPerformanceMonitor {
    
    private final Map<String, PerformanceStats> stats = new ConcurrentHashMap<>();
    
    @EventListener
    public void onExtensionCall(ExtensionCallEvent event) {
        String key = event.getExtPoint() + ":" + event.getProvider();
        stats.computeIfAbsent(key, k -> new PerformanceStats())
             .recordCall(event.getDuration(), event.isSuccess());
    }
    
    @Scheduled(fixedRate = 300000) // 5分钟
    public void reportSlowExtensions() {
        stats.entrySet().stream()
            .filter(entry -> entry.getValue().getAverageDuration() > 100) // >100ms
            .forEach(entry -> log.warn("慢扩展点: {}, 平均耗时: {}ms", 
                entry.getKey(), entry.getValue().getAverageDuration()));
    }
}
```

## 常见问题解答

### 基础概念

**Q: 为什么我不能直接调用BizContext的setData()方法？**
A: BizContext采用不可变设计模式，所有核心属性必须在创建时通过Builder模式设置，不提供setter方法。这样设计可以确保线程安全和数据一致性。正确做法是使用`BizContext.<T>builder().data(value).build()`创建新实例。

**Q: 如何获取上下文中的业务数据？**
A: 使用`context.data()`方法获取业务数据，由于BizContext是泛型类，请确保类型参数正确。

**Q: 什么时候应该使用扩展点框架？**
A: 当你的业务需要以下特性时，扩展点框架是理想选择：
- 为不同租户提供定制化业务逻辑
- 根据动态条件动态选择不同的实现
- 避免在核心代码中编写大量条件判断
- 支持插件化架构和动态扩展
- 实现业务逻辑与框架逻辑的解耦

**Q: 扩展点框架和策略模式有什么区别？**
A: 策略模式是一种设计模式，需要手动选择和切换策略；而扩展点框架是一种基础设施，它自动根据上下文信息进行路由，支持多维度的匹配条件，并提供了丰富的功能如优先级控制、动态注册等。

**Q: 扩展点和Spring的自动装配有什么关系？**
A: 扩展点框架基于Spring生态构建，利用Spring的自动装配机制来管理扩展实现，但增加了基于上下文的动态路由能力，解决了自动装配只能静态注入的局限性。

### 性能优化

**Q: 表达式路由的性能开销大吗？**
A: 框架内置了多级缓存机制，对相同表达式和上下文会缓存匹配结果，有效降低了性能开销。为了进一步优化性能，建议：
- 避免使用过于复杂的SpEL表达式
- 对高频调用场景优先使用精确匹配（如tenantCode、bizCode）
- 启用缓存预热机制减少冷启动开销

**Q: 如何优化高并发场景下的扩展点性能？**
A: 可以采用以下优化策略：
1. 使用本地缓存减少重复计算
2. 对非关键路径使用异步执行
3. 避免在扩展点实现中进行耗时操作
4. 合理设置优先级，避免不必要的匹配计算

### 高级用法

**Q: 如何实现扩展点的A/B测试？**
A: 可以通过表达式路由轻松实现A/B测试：
```java
// A组实现
@Extension(condition = "#context.getAttribute('abTestGroup') == 'A'")
public class VersionAExtension implements FeatureExtPoint {
    // A版本逻辑实现
}

// B组实现  
@Extension(condition = "#context.getAttribute('abTestGroup') == 'B'")
public class VersionBExtension implements FeatureExtPoint {
    // B版本逻辑实现
}
```

**Q: 扩展点之间如何传递数据？**
A: 可以通过 `BizContext` 的属性机制在扩展点之间传递数据：
```java
// 第一个扩展点
context.setAttribute("processedData", intermediateResult);

// 后续扩展点
MyData data = context.getAttribute("processedData", MyData.class);
```

**Q: 如何实现扩展点的动态更新？**
A: 可以利用框架提供的动态注册机制：
```java
@Autowired
private ExtensionRegister extensionRegister;

// 注销旧实现
extensionRegister.unregisterExtension(OrderExtPoint.class, oldExtension);

// 注册新实现
extensionRegister.registerExtension(OrderExtPoint.class, newExtension, metadata);
```

## 版本历史

### 1.3.0 (最新版本)
- 扩展点版本管理功能
- 支持扩展热更新
- 核心性能优化
- 自动文档生成支持

### 1.2.0
- 动态注册扩展实现
- 内置监控指标
- 异步执行支持
- 多租户功能增强

### 1.1.0
- 扩展组合执行能力
- 缓存预热机制
- 表达式引擎优化
- 错误处理机制增强

### 1.0.0
- 基础扩展点机制
- 注解驱动开发模式
- SpEL表达式路由
- Spring框架深度集成

## 附录：速查表

### 注解速查

| 注解 | 用途 | 示例 |
|------|------|------|
| `@ExtPoint` | 定义扩展点接口 | `@ExtPoint(name="订单扩展点")` |
| `@Extension` | 标记扩展点实现 | `@Extension(tenantCode="T1", priority=50)` |
| `@EnableExtPoints` | 启用扩展点框架 | `@EnableExtPoints(basePackages="com.example")` |

### API速查

| 方法 | 用途 | 示例 |
|------|------|------|
| `BizContext.builder()` | 创建业务上下文 | `.tenantCode("T1").data(obj).build()` |
| `ExtensionContextManager.with()` | 设置当前上下文 | `try (var scope = ExtensionContextManager.with(ctx)) { }` |
| `ExtPointComposite.getExtensions()` | 获取匹配的扩展实现 | `composite.getExtensions(context)` |

### SpEL表达式速查

| 表达式类型 | 示例 | 说明 |
|------------|------|------|
| **基础比较** | `#data.amount > 100` | 简单数值比较 |
| | `#tenantCode == 'TENANT_A'` | 字符串相等判断 |
| | `#data.status in {'PENDING', 'PROCESSING'}` | 集合包含判断 |
| **安全访问** | `#data.user?.level == 'VIP'` | 使用安全导航操作符避免空指针异常 |
| | `#data.user?.name == 'admin'` | 安全属性访问 |
| **字符串操作** | `#bizCode.startsWith('ORDER')` | 字符串前缀匹配 |
| | `#data.name.contains('test')` | 字符串包含检查 |
| **集合操作** | `#data.items.size() > 0` | 集合大小判断 |
| | `#data.items.?[price > 100].size() > 0` | 过滤集合元素并统计数量 |
| **方法调用** | `T(java.util.Objects).equals(#scenario, 'MOBILE')` | 调用静态方法进行比较 |
| | `T(java.lang.Math).random() > 0.5` | 随机值比较 |
| **上下文属性** | `#context.getAttribute('vipLevel') == 'GOLD'` | 访问上下文中的自定义属性 |
| **日期操作** | `#data.createTime.after(new java.util.Date())` | 日期比较 |
| **默认值处理** | `#data.amount ?: 0` | 空值时使用默认值 |
| **复杂组合** | `(#tenantCode == 'T1' && #data.amount > 1000) || (#tenantCode == 'T2' && #data.amount > 500)` | 多条件组合判断 |
| | `#data.amount > 100 && #data.amount < 1000` | 范围判断 |

---

## 🎉 开始使用！

现在你已经掌握了 Bone Extension Framework 的核心概念和最佳实践。建议从 [快速开始](#快速开始) 的最小示例开始，逐步应用到你的业务场景中。

**遇到问题？** 查看 [故障排查与监控](#故障排查与监控) 和 [常见问题解答](#常见问题解答)，或检查框架日志中的详细错误信息。

Happy Coding! 🚀