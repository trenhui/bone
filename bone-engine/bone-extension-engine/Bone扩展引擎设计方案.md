# Bone 扩展引擎设计方案

## 一、概述

Bone 扩展引擎是一个轻量级、高性能的企业级插件化架构框架，旨在实现系统核心逻辑的稳定性与业务需求的灵活性之间的平衡。基于"开闭原则"，引擎通过标准化扩展点机制，允许在不修改核心代码的情况下动态注入定制化业务逻辑，从而快速响应业务变化、支持多租户定制化需求并降低系统维护成本。

### 1.1 设计理念

- **高内聚低耦合**：通过扩展点抽象实现核心逻辑与扩展逻辑的解耦
- **可插拔性**：支持扩展实现的热插拔，无需重启服务
- **多租户隔离**：提供租户级别的扩展点隔离能力
- **高性能**：采用缓存、代理等机制确保扩展调用的低延迟
- **可观测性**：内置监控、日志、追踪能力，便于问题排查
- **安全稳定**：提供资源隔离、异常处理、降级策略等保障机制

## 二、系统架构

### 2.1 总体架构

Bone 扩展引擎采用"SDK + 管理台"的二元架构设计：

1. **数据面（Data Plane）- bone-extension-sdk**
   - 轻量级嵌入式扩展框架，提供扩展点运行时支撑
   - 集成到业务应用中，负责扩展点的发现、路由、调用等核心功能
   - 提供高性能、低延迟的扩展点调用能力

2. **控制面（Control Plane）- bone-extension-studio**
   - 可视化管理台，提供扩展点元数据管理、插件生命周期治理
   - 配置路由规则、监控扩展点执行情况
   - 提供插件开发、部署、升级的管理能力

### 2.2 核心组件

#### 2.2.1 SDK 核心组件

- **扩展点注解（Annotation）**：定义扩展点和扩展实现的核心注解
- **业务上下文（BizContext）**：封装业务环境信息，作为扩展点调用和路由的依据
- **路由引擎（ExtPointRouter）**：基于上下文信息智能匹配最合适的扩展实现
- **代理工厂（ExtPointProxyFactory）**：为扩展点接口创建动态代理，拦截调用并触发路由
- **仓库（ExtPointRepository）**：存储扩展点元数据和实现类信息
- **事件发布器（ExtensionEventPublisher）**：发布扩展点生命周期事件
- **配置管理器（ExtensionConfigManager）**：管理扩展点配置信息
- **生命周期管理器（ExtensionLifecycle）**：管理扩展点的初始化和销毁过程
- **加载器（ExtensionLoader）**：基于SPI机制自动发现和加载扩展点实现

#### 2.2.2 管理台核心组件

- **元数据管理**：扩展点定义、版本控制、依赖分析
- **插件治理**：上传、安装、升级、卸载插件，依赖图谱展示
- **路由配置**：可视化配置路由规则，支持表达式编辑与校验
- **监控中心**：扩展点调用次数、响应时间、成功率等指标监控
- **告警系统**：异常调用、性能异常等告警机制

## 三、核心功能设计

### 3.1 标准化扩展点

#### 3.1.1 扩展点定义

扩展点是业务流程中的标准化接口，通过 `@ExtPoint` 注解标记：

```java
@ExtPoint(name = "订单折扣计算", description = "不同场景下的订单折扣逻辑")
public interface OrderDiscountExtPoint {
    DiscountResult calculate(BizContext<Order> context);
}
```

#### 3.1.2 扩展实现

扩展实现是对扩展点接口的具体实现，通过 `@Extension` 注解标记：

```java
@Extension(
    point = "OrderDiscountExtPoint",
    tenantCode = "VIP_TENANT",
    bizCode = "ORDER",
    scenario = "NORMAL",
    priority = 100,
    condition = "#context.data.amount > 1000"
)
@Component
public class VipLargeOrderDiscount implements OrderDiscountExtPoint {
    @Override
    public DiscountResult calculate(BizContext<Order> context) {
        // VIP用户大额订单折扣逻辑
    }
}
```

### 3.2 智能路由与上下文传递

#### 3.2.1 业务上下文设计

业务上下文封装了路由决策所需的信息：

```java
public class BizContext<T> {
    private String tenantCode;      // 租户编码
    private String bizCode;         // 业务域编码
    private String useCase;         // 用例标识
    private String scenario;        // 场景标识
    private T data;                 // 业务数据
    private Map<String, Object> attributes; // 自定义属性
    
    // 构建者模式
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    // 其他方法...
}
```

#### 3.2.2 多维度路由策略

采用评分机制进行路由匹配：

| 匹配维度 | 评分规则 | 权重 |
|---------|---------|------|
| 业务域匹配 | 完全匹配加100分 | 最高 |
| 租户匹配 | 完全匹配加80分 | 高 |
| 场景匹配 | 完全匹配加60分 | 中高 |
| 版本匹配 | 完全匹配加40分 | 中 |
| 支付方式匹配 | 完全匹配加30分 | 中低 |
| 数据源匹配 | 完全匹配加20分 | 低 |

#### 3.2.3 三级路由策略

1. **精确匹配**：优先匹配租户、业务域、场景完全一致的插件
2. **表达式匹配**：通过SpEL表达式动态匹配复杂条件
3. **默认实现**：未匹配到规则时，执行全局默认插件

### 3.3 插件生命周期管理

#### 3.3.1 生命周期接口

```java
public interface ExtensionLifecycle {
    void initialize(Object extension);
    void beforeInvoke(Object extension, Method method, Object[] args);
    void afterInvoke(Object extension, Method method, Object[] args, Object result);
    void onException(Object extension, Method method, Object[] args, Exception e);
    void destroy(Object extension);
}
```

#### 3.3.2 生命周期事件

- `ExtensionRegisteredEvent`：扩展点注册事件
- `ExtensionInvokeBeforeEvent`：扩展点调用前事件
- `ExtensionInvokeAfterEvent`：扩展点调用后事件
- `ExtensionInvokeExceptionEvent`：扩展点异常事件
- `ExtensionRouteSelectedEvent`：扩展点路由选择事件

### 3.4 依赖治理与资源隔离

#### 3.4.1 类加载隔离

采用"双亲委派+沙箱隔离"机制，每个插件拥有独立的类加载器，避免类名冲突与版本污染。

#### 3.4.2 资源限制

通过线程池隔离、内存配额控制，防止单个插件过度占用系统资源。

#### 3.4.3 异常隔离

插件抛出的异常被SDK捕获并封装，不影响核心业务流程，支持自定义降级策略。

### 3.5 扩展可观测性

#### 3.5.1 指标采集

内置Micrometer指标，记录扩展点调用次数、响应时间（P50/P95/P99）、成功率等核心指标，支持Prometheus集成。

#### 3.5.2 链路追踪

与SkyWalking无缝对接，生成"业务系统→扩展点→插件"的完整调用链路，标注各节点耗时与参数。

#### 3.5.3 日志埋点

记录插件调用的输入输出、路由决策过程，日志格式统一，支持通过管理台检索与分析。

### 3.6 灵活配置与动态更新

#### 3.6.1 参数动态配置

插件可通过配置中心读取外部配置，配置变更实时同步至SDK。

#### 3.6.2 配置隔离

支持按租户/插件维度配置参数，通过BizContext动态获取对应配置。

## 四、技术实现方案

### 4.1 核心注解实现

#### 4.1.1 ExtPoint注解

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExtPoint {
    String name() default "";
    String description() default "";
    ExtensionType type() default ExtensionType.BUSINESS;
    String version() default "1.0.0";
    boolean enabled() default true;
}
```

#### 4.1.2 Extension注解

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Extension {
    Class<?> point();
    String tenantCode() default "DEFAULT";
    String bizCode() default "DEFAULT";
    String useCase() default "DEFAULT";
    String scenario() default "DEFAULT";
    String condition() default "";
    int priority() default 0;
    boolean enabled() default true;
    String name() default "";
}
```

### 4.2 业务上下文实现

```java
@Getter
@Setter
@ToString
public class BizContext<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String tenantCode;
    private String bizCode;
    private String useCase;
    private String scenario;
    private String env;
    private String group;
    private T data;
    private LocalDateTime createTime;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, String> metadata = new HashMap<>();
    
    @Builder
    public BizContext(String tenantCode, String bizCode, String useCase, String scenario, 
                     String env, String group, T data, Map<String, Object> attributes) {
        this.tenantCode = tenantCode;
        this.bizCode = bizCode;
        this.useCase = useCase;
        this.scenario = scenario;
        this.env = env;
        this.group = group;
        this.data = data;
        this.createTime = LocalDateTime.now();
        
        if (!CollectionUtils.isEmpty(attributes)) {
            this.attributes.putAll(attributes);
        }
    }
    
    // 属性操作方法
    public BizContext<T> putAttribute(String key, Object value) {
        if (key != null) {
            if (value != null) {
                this.attributes.put(key, value);
            } else {
                this.attributes.remove(key);
            }
        }
        return this;
    }
    
    @SuppressWarnings("unchecked")
    public <V> V getAttribute(String key) {
        if (key == null) {
            return null;
        }
        return (V) this.attributes.get(key);
    }
    
    // 其他方法...
}
```

### 4.3 路由引擎实现

```java
@Component
@Slf4j
public class DefaultExtPointRouter implements ExtPointRouter {
    
    private final ExtPointRepository repository;
    private final ExpressionEvaluator expressionEvaluator;
    private final Cache<RouteKey, Object> routeCache;
    
    public DefaultExtPointRouter(ExtPointRepository repository, 
                               ExpressionEvaluator expressionEvaluator,
                               ExtensionConfigProperties config) {
        this.repository = repository;
        this.expressionEvaluator = expressionEvaluator;
        
        // 初始化路由缓存
        CacheBuilder<Object, Object> cacheBuilder = Caffeine.newBuilder()
            .maximumSize(config.getCache().getMaximumSize())
            .expireAfterWrite(config.getCache().getExpireAfterWrite());
            
        if (config.getCache().isRecordStats()) {
            cacheBuilder.recordStats();
        }
        
        this.routeCache = cacheBuilder.build();
    }
    
    @Override
    public <T> T route(Class<T> extPointClass, BizContext<?> context) {
        // 构建缓存Key
        RouteKey cacheKey = new RouteKey(extPointClass, context);
        
        // 尝试从缓存获取
        if (routeCache != null) {
            T cached = (T) routeCache.getIfPresent(cacheKey);
            if (cached != null) {
                log.debug("Route cache hit for: {}", cacheKey);
                return cached;
            }
        }
        
        // 执行路由逻辑
        T result = doRoute(extPointClass, context);
        
        // 缓存结果
        if (routeCache != null && result != null) {
            routeCache.put(cacheKey, result);
        }
        
        return result;
    }
    
    private <T> T doRoute(Class<T> extPointClass, BizContext<?> context) {
        List<Extension> candidates = repository.findByPoint(extPointClass);
        
        if (candidates.isEmpty()) {
            throw new ExtensionNotFoundException("No extension found for: " + extPointClass.getName());
        }
        
        // 过滤启用的扩展
        List<Extension> enabledExtensions = candidates.stream()
            .filter(Extension::isEnabled)
            .collect(Collectors.toList());
            
        if (enabledExtensions.isEmpty()) {
            throw new ExtensionNotFoundException("No enabled extension found for: " + extPointClass.getName());
        }
        
        // 三级路由匹配
        Optional<Extension> targetExtension = findTargetExtension(enabledExtensions, context);
        
        return targetExtension.map(extension -> {
            try {
                T instance = createExtensionInstance(extension);
                log.debug("Routed to extension: {} for context: {}", extension.getId(), context);
                return instance;
            } catch (Exception e) {
                throw new RouteException("Failed to create extension instance: " + extension.getId(), e);
            }
        }).orElseThrow(() -> new ExtensionNotFoundException(
            "No matching extension found for context: " + context));
    }
    
    // 其他路由方法...
}
```

### 4.4 配置管理实现

```java
@ConfigurationProperties(prefix = "bone.extension")
@Data
@Validated
public class ExtensionConfigProperties {
    
    private boolean enabled = true;
    private List<String> basePackages = Arrays.asList("com.bone.extension");
    private CacheConfig cache = new CacheConfig();
    private EventConfig event = new EventConfig();
    private RepositoryConfig repository = new RepositoryConfig();
    
    @Data
    public static class CacheConfig {
        private boolean enabled = true;
        private int maximumSize = 1000;
        private Duration expireAfterWrite = Duration.ofMinutes(30);
        private boolean recordStats = true;
    }
    
    @Data
    public static class EventConfig {
        private boolean enabled = true;
        private boolean asyncPublish = true;
        private ExecutorConfig executor = new ExecutorConfig();
    }
    
    // 其他配置类...
}
```

### 4.5 Spring Boot自动配置

```java
@Configuration
@EnableConfigurationProperties(ExtensionProperties.class)
public class ExtensionAutoConfiguration implements ImportAware {

    private String[] basePackages = {};
    private boolean enableAutoScan = true;
    private boolean enableCache = true;
    private boolean enableEvents = true;

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importMetadata.getAnnotationAttributes(EnableExtPoints.class.getName(), false));
        if (attributes != null) {
            this.basePackages = attributes.getStringArray("basePackages");
            this.enableAutoScan = attributes.getBoolean("enableAutoScan");
            this.enableCache = attributes.getBoolean("enableCache");
            this.enableEvents = attributes.getBoolean("enableEvents");
        }
    }

    @Bean
    @ConditionalOnMissingBean(ExtPointRouter.class)
    public ExtPointRouter extPointRouter() {
        DefaultExtPointRouter router = new DefaultExtPointRouter();
        router.setEnableCache(enableCache);
        return router;
    }

    @Bean
    @ConditionalOnMissingBean(ExtPointProxyFactory.class)
    public ExtPointProxyFactory extPointProxyFactory(
            ExtPointRouter extPointRouter,
            ExtensionLifecycle extensionLifecycle,
            ExtensionEventPublisher eventPublisher,
            ExtensionConfigManager configManager) {
        ExtPointProxyFactory factory = new ExtPointProxyFactory(extPointRouter, extensionLifecycle, eventPublisher, configManager);
        factory.setEnableCache(enableCache);
        return factory;
    }
    
    // 其他Bean定义...
}
```

## 五、使用示例

### 5.1 支付服务扩展点示例

#### 5.1.1 扩展点接口定义

```java
@ExtPoint
@ExtensionDoc(
    title = "支付服务扩展点",
    description = "提供多种支付方式的统一接入接口",
    usage = "用于处理订单支付、会员支付等场景",
    parameters = {
        @Parameter(name = "context", description = "业务上下文，包含支付请求信息")
    },
    returnValue = @ReturnValue(description = "支付结果，包含支付状态、交易ID等信息")
)
public interface PaymentService {
    PaymentResult processPayment(BizContext<PaymentRequest> context);
    
    class PaymentRequest {
        private String orderId;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String userId;
        // getters and setters
    }
    
    class PaymentResult {
        private String paymentId;
        private String status;
        private String message;
        private BigDecimal paidAmount;
        private long paidTime;
        // getters and setters
    }
}
```

#### 5.1.2 支付宝支付实现

```java
@Extension(
    name = "alipayService",
    description = "支付宝支付实现",
    bizCode = {"ORDER", "MEMBERSHIP"},
    tenantCode = {"DEFAULT", "TENANT001"},
    scenario = {"NORMAL_PAY"},
    paymentMethod = "ALIPAY",
    priority = 5
)
@Component
public class AlipayServiceImpl implements PaymentService {
    
    private static final Logger logger = LoggerFactory.getLogger(AlipayServiceImpl.class);

    @Override
    public PaymentResult processPayment(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getData();
        logger.info("Processing Alipay payment for order: {}, amount: {}", 
                request.getOrderId(), request.getAmount());
        
        // 模拟支付宝支付处理逻辑
        PaymentResult result = new PaymentResult();
        result.setPaymentId("ALI" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        result.setStatus("SUCCESS");
        result.setMessage("支付宝支付成功");
        result.setPaidAmount(request.getAmount());
        result.setPaidTime(System.currentTimeMillis());
        
        return result;
    }
}
```

#### 5.1.3 微信支付实现

```java
@Extension(
    name = "wechatPayService",
    description = "微信支付实现",
    bizCode = {"ORDER", "MEMBERSHIP"},
    tenantCode = {"DEFAULT", "TENANT002"},
    scenario = {"NORMAL_PAY", "MINI_APP_PAY"},
    paymentMethod = "WECHAT",
    priority = 6
)
@Component
public class WechatPayServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(WechatPayServiceImpl.class);

    @Override
    public PaymentResult processPayment(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getData();
        logger.info("Processing WeChat payment for order: {}, amount: {}", 
                request.getOrderId(), request.getAmount());
        
        // 模拟微信支付处理逻辑
        PaymentResult result = new PaymentResult();
        result.setPaymentId("WX" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        result.setStatus("SUCCESS");
        result.setMessage("微信支付成功");
        result.setPaidAmount(request.getAmount());
        result.setPaidTime(System.currentTimeMillis());
        
        return result;
    }
}
```

#### 5.1.4 在业务中使用

```java
@Service
public class PaymentServiceDemo {
    
    @Autowired
    private PaymentService paymentService;
    
    public PaymentResult processPaymentByScenario(
            String orderId, 
            BigDecimal amount, 
            String userId, 
            String tenantCode, 
            String scenario) {
        
        // 创建支付请求
        PaymentService.PaymentRequest request = new PaymentService.PaymentRequest();
        request.setOrderId(orderId);
        request.setAmount(amount);
        request.setCurrency("CNY");
        request.setUserId(userId);
        
        // 根据场景设置支付方式
        if ("MINI_APP_PAY".equals(scenario)) {
            request.setPaymentMethod("WECHAT");
        } else {
            request.setPaymentMethod("ALIPAY");
        }
        
        // 创建业务上下文
        BizContext<PaymentRequest> context = BizContext.<PaymentRequest>builder()
            .bizCode("ORDER")
            .tenantCode(tenantCode)
            .scenario(scenario)
            .data(request)
            .build();
        
        // 扩展点框架会根据上下文自动选择合适的实现类
        return paymentService.processPayment(context);
    }
}
```

## 六、高级特性

### 6.1 文档化支持

通过 `@ExtensionDoc` 注解提供扩展点的文档信息：

```java
@ExtensionDoc(
    title = "扩展点标题",
    description = "详细描述",
    usage = "使用场景",
    parameters = { @Parameter(name = "param1", description = "参数说明") },
    returnValue = @ReturnValue(description = "返回值说明")
)
```

### 6.2 扩展点组合模式

支持多个扩展点实现的组合调用：

```java
@Extension(
    point = "OrderProcessExtPoint",
    tenantCode = "TENANT001",
    mode = ExtensionMode.COMPOSITE
)
@Component
public class CompositeOrderProcessor implements OrderProcessExtPoint {
    
    @Autowired
    private List<OrderProcessExtPoint> processors;
    
    @Override
    public Order process(BizContext<Order> context) {
        Order order = context.getData();
        for (OrderProcessExtPoint processor : processors) {
            order = processor.process(context);
        }
        return order;
    }
}
```

### 6.3 动态版本管理

支持扩展点的版本控制和灰度发布：

```java
@Extension(
    point = "DiscountExtPoint",
    version = "2.0.0",
    rolloutRate = 0.3,  // 30%流量
    rolloutType = RolloutType.PERCENTAGE
)
@Component
public class NewDiscountCalculator implements DiscountExtPoint {
    // 新算法实现
}
```

## 七、性能优化

### 7.1 缓存策略

- 路由结果缓存：缓存热点路由结果，降低匹配耗时
- 代理对象缓存：缓存扩展点代理对象，避免重复创建
- 配置缓存：缓存扩展点配置，减少配置中心访问

### 7.2 异步处理

- 事件异步发布：扩展点事件采用异步发布，不影响主流程性能
- 监控数据异步采集：指标数据异步上报，减少对业务的影响

### 7.3 预热机制

- 启动时预热：系统启动时预加载常用扩展点和路由规则
- 定时预热：定期刷新缓存，避免冷启动性能问题

## 八、最佳实践

### 8.1 扩展点设计原则

- **单一职责**：每个扩展点专注于一个业务场景
- **接口简洁**：扩展点接口设计简洁，参数明确
- **合理抽象**：选择合适的抽象粒度，避免过度设计
- **向前兼容**：扩展点版本升级时保持向后兼容

### 8.2 扩展实现最佳实践

- **无状态设计**：扩展实现尽量设计为无状态，避免并发问题
- **异常处理**：妥善处理异常，避免影响主流程
- **性能考虑**：注意扩展实现的性能，避免耗时操作
- **日志规范**：使用统一的日志格式，便于问题排查

### 8.3 路由策略选择

- 简单场景：使用基本的租户、业务域、场景匹配
- 复杂场景：结合SpEL表达式实现灵活的条件匹配
- 多租户场景：通过tenantCode实现租户隔离
- 灰度场景：使用版本控制和流量控制

## 九、总结与展望

Bone 扩展引擎通过"SDK+管理台"的二元架构设计，为企业级应用提供了一套完整的插件化架构解决方案。通过标准化扩展点机制、智能路由策略、完整的生命周期管理和完善的可观测性能力，引擎实现了系统核心逻辑的稳定性与业务需求的灵活性之间的平衡，使企业能够快速响应业务变化，降低系统维护成本。

未来，Bone 扩展引擎将在以下方向持续演进：

1. **更智能的路由策略**：引入AI辅助的路由决策，基于历史调用数据优化路由选择
2. **更丰富的扩展模式**：支持更多样化的扩展模式，如条件分支、循环等复杂流程
3. **更完善的云原生支持**：深度集成云原生生态，支持Kubernetes环境下的动态扩缩容
4. **更强大的开发工具**：提供IDE插件、代码生成等开发工具，进一步降低开发门槛
5. **更广泛的生态集成**：与更多开源框架和中间件深度集成，扩展应用场景