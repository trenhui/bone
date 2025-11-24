package com.bone.engine.extension.core.register;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.api.annotation.ExtensionPoint;
import com.bone.engine.extension.api.exception.ExtensionRegistrationException;
import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.model.definition.ExtensionPointDefinition;
import com.bone.engine.extension.api.spi.ExpressionEvaluator;
import com.bone.engine.extension.core.router.DefaultExtPointRouter;
import com.bone.engine.extension.support.repository.ExtensionRepository;
import com.bone.engine.extension.support.repository.InMemoryExtensionRepository;
import com.bone.engine.extension.support.expression.AviatorExpressionEvaluator;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 扩展点注册中心 - 基于业界最佳实践实现
 *
 * 设计原则：
 * 1. 单一职责：专注于扩展点的注册和管理
 * 2. 明确命名：方法名清晰表达业务意图
 * 3. 防御式编程：充分的参数校验和异常处理
 * 4. 监控支持：完整的注册统计和日志
 * 5. 生命周期管理：清晰的初始化、注册、销毁流程
 */
@Component
@Slf4j
public class ExtensionRegister implements ApplicationContextAware, SmartInitializingSingleton {

    // ==================== 常量定义 ====================
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern WILDCARD_PATTERN = Pattern.compile(".*");
    private static final int DEFAULT_TIMEOUT = 30;
    private static final String DEFAULT_EXTENSION_CODE_PREFIX = "EXT_";

    // ==================== 核心组件 ====================
    private ApplicationContext applicationContext;
    private Environment environment;

    // 注册表：扩展点定义缓存
    private final Map<String, ExtensionPointDefinition> extensionPointRegistry = new ConcurrentHashMap<>();

    // 扩展仓库
    private final ExtensionRepository extensionRepository;

    // 表达式引擎
    private final ExpressionEvaluator expressionEvaluator = new AviatorExpressionEvaluator();

    // ==================== 注册统计 ====================
    private final AtomicInteger totalScannedCount = new AtomicInteger(0);
    private final AtomicInteger successfulRegistrationCount = new AtomicInteger(0);
    private final AtomicInteger failedRegistrationCount = new AtomicInteger(0);
    private final Set<String> registeredExtensionCodes = ConcurrentHashMap.newKeySet();

    public ExtensionRegister() {
        this.extensionRepository = new InMemoryExtensionRepository("inMemoryExtensionRepository");
    }

    public ExtensionRegister(ExtensionRepository extensionRepository) {
        this.extensionRepository = Objects.requireNonNull(extensionRepository,
                "ExtensionRepository cannot be null");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.environment = applicationContext.getEnvironment();
    }

    @PostConstruct
    public void initialize() {
        log.info("Bone Extension Registry v2.0 initializing with repository: {}",
                extensionRepository.getName());
    }

    @Override
    public void afterSingletonsInstantiated() {
        long startTime = System.currentTimeMillis();

        // 扫描所有扩展实现
        Map<String, Object> extensionBeans = applicationContext.getBeansWithAnnotation(Extension.class);
        log.info("Starting registration process for {} extension beans", extensionBeans.size());

        // 批量注册扩展
        extensionBeans.values().forEach(this::registerExtensionBean);

        // 输出注册报告
        logRegistrationReport(startTime);

        // 预热路由
        warmupExtensionRouters();
    }

    // ==================== 核心注册方法 ====================

    /**
     * 注册扩展Bean
     */
    public void registerExtensionBean(@NonNull Object extensionBean) {
        totalScannedCount.incrementAndGet();

        try {
            registerExtension(extensionBean);
            successfulRegistrationCount.incrementAndGet();
        } catch (Exception e) {
            failedRegistrationCount.incrementAndGet();
            log.error("Failed to register extension bean: {}", extensionBean.getClass().getName(), e);
            throw new ExtensionRegistrationException("Extension registration failed for: " +
                    extensionBean.getClass().getName(), e);
        }
    }

    /**
     * 注册扩展实现
     */
    public synchronized void registerExtension(@NonNull Object extensionImplementation) {
        Objects.requireNonNull(extensionImplementation, "Extension implementation cannot be null");

        Class<?> implementationClass = getTargetClass(extensionImplementation);
        Extension extensionAnnotation = findExtensionAnnotation(implementationClass);

        if (extensionAnnotation == null) {
            throw new ExtensionRegistrationException(
                    "No @Extension annotation found on: " + implementationClass.getName());
        }

        // 解析环境变量占位符
        Extension resolvedAnnotation = resolveEnvironmentPlaceholders(extensionAnnotation);

        // 查找扩展点接口
        Class<?> extensionPointInterface = findExtensionPointInterface(implementationClass);
        String extensionPointName = extensionPointInterface.getName();

        // 创建或获取扩展点定义
        ExtensionPointDefinition pointDefinition = getOrCreateExtensionPointDefinition(
                extensionPointName, extensionPointInterface);

        // 构建扩展定义
        ExtensionDefinition extensionDefinition = buildExtensionDefinition(
                extensionImplementation, implementationClass, resolvedAnnotation, pointDefinition);

        // 预处理和验证
        preprocessExtensionDefinition(extensionDefinition);
        validateExtensionDefinition(extensionDefinition);

        // 执行注册
        executeRegistration(extensionPointName, extensionDefinition);
    }

    /**
     * 注销扩展
     */
    public synchronized boolean unregisterExtension(@NonNull String extensionPoint, @NonNull String extensionCode) {
        Objects.requireNonNull(extensionPoint, "Extension point cannot be null");
        Objects.requireNonNull(extensionCode, "Extension code cannot be null");

        ExtensionPointDefinition pointDefinition = extensionPointRegistry.get(extensionPoint);
        if (pointDefinition != null && pointDefinition.getExtensions().remove(extensionCode) != null) {
            // 更新：使用新的 unregister 方法
            ExtensionDefinition removed = extensionRepository.unregister(extensionPoint, extensionCode);
            if (removed != null) {
                registeredExtensionCodes.remove(extensionCode);
                log.info("Extension unregistered successfully: {} -> {}", extensionPoint, extensionCode);

                // 清理空的扩展点
                if (pointDefinition.getExtensions().isEmpty()) {
                    extensionPointRegistry.remove(extensionPoint);
                }
                return true;
            }
        }
        return false;
    }

    // ==================== 查询方法 ====================

    /**
     * 根据扩展点和扩展代码查询扩展定义
     */
    public Optional<ExtensionDefinition> findExtension(@NonNull String extensionPoint, @NonNull String extensionCode) {
        // 更新：使用新的 getExtension 方法
        return Optional.ofNullable(extensionRepository.getExtension(extensionPoint, extensionCode));
    }

    /**
     * 查询扩展点下的所有扩展定义
     */
    public Collection<ExtensionDefinition> findExtensionsByPoint(@NonNull String extensionPoint) {
        // 更新：使用新的 getAllExtensions 方法
        return extensionRepository.getAllExtensions(extensionPoint);
    }

    /**
     * 查询扩展点下启用的扩展定义
     */
    public Collection<ExtensionDefinition> findEnabledExtensionsByPoint(@NonNull String extensionPoint) {
        // 更新：使用新的 getEnabledExtensions 方法
        return extensionRepository.getEnabledExtensions(extensionPoint);
    }

    /**
     * 全局搜索扩展定义
     */
    public Optional<ExtensionDefinition> findExtensionByCode(@NonNull String extensionCode) {
        // 更新：使用新的 getExtensionByCode 方法
        return Optional.ofNullable(extensionRepository.getExtensionByCode(extensionCode));
    }

    // ==================== 统计信息 ====================

    /**
     * 获取注册统计信息
     */
    public RegistrationStatistics getRegistrationStatistics() {
        return new RegistrationStatistics(
                totalScannedCount.get(),
                successfulRegistrationCount.get(),
                failedRegistrationCount.get(),
                // 更新：使用新的 countExtensionPoints 方法
                extensionRepository.countExtensionPoints(),
                // 更新：使用新的 countExtensions 方法
                extensionRepository.countExtensions(),
                registeredExtensionCodes.size()
        );
    }

    /**
     * 获取所有扩展点定义
     */
    public Map<String, ExtensionPointDefinition> getAllExtensionPointDefinitions() {
        return Collections.unmodifiableMap(extensionPointRegistry);
    }

    /**
     * 获取仓库统计信息
     */
    public ExtensionRepository.RepositoryStats getRepositoryStatistics() {
        // 更新：使用新的 getStats 方法
        return extensionRepository.getStats();
    }

    // ==================== 内部辅助方法 ====================

    private Class<?> getTargetClass(Object bean) {
        return AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : bean.getClass();
    }

    private Extension findExtensionAnnotation(Class<?> clazz) {
        return AnnotatedElementUtils.findMergedAnnotation(clazz, Extension.class);
    }

    private Extension resolveEnvironmentPlaceholders(Extension annotation) {
        return new Extension() {
            @Override public Class<? extends Annotation> annotationType() { return Extension.class; }
            @Override public String value() { return resolvePlaceholder(annotation.value()); }
            @Override public String description() { return resolvePlaceholder(annotation.description()); }
            @Override public String tenant() { return resolvePlaceholder(annotation.tenant()); }
            @Override public String bizCode() { return resolvePlaceholder(annotation.bizCode()); }
            @Override public String useCase() { return resolvePlaceholder(annotation.useCase()); }
            @Override public String scenario() { return resolvePlaceholder(annotation.scenario()); }
            @Override public String env() { return resolvePlaceholder(annotation.env()); }
            @Override public String version() { return resolvePlaceholder(annotation.version()); }
            @Override public int order() { return annotation.order(); }
            @Override public int weight() { return annotation.weight(); }
            @Override public int traffic() { return annotation.traffic(); }
            @Override public boolean enabled() { return annotation.enabled(); }
            @Override public String condition() { return resolvePlaceholder(annotation.condition()); }
            @Override public String[] tags() { return Arrays.stream(annotation.tags()).map(this::resolvePlaceholder).toArray(String[]::new); }
            @Override public String startTime() { return resolvePlaceholder(annotation.startTime()); }
            @Override public String endTime() { return resolvePlaceholder(annotation.endTime()); }
            @Override public boolean async() { return annotation.async(); }
            @Override public int timeout() { return annotation.timeout(); }

            private String resolvePlaceholder(String value) {
                return StringUtils.hasText(value) ? environment.resolvePlaceholders(value) : value;
            }
        };
    }

    private Class<?> findExtensionPointInterface(Class<?> implementationClass) {
        Set<Class<?>> candidateInterfaces = new LinkedHashSet<>();
        collectAllInterfaces(implementationClass, candidateInterfaces);

        List<Class<?>> extensionPointInterfaces = candidateInterfaces.stream()
                .filter(interfaceClass -> AnnotatedElementUtils.hasAnnotation(interfaceClass, ExtensionPoint.class))
                .toList();

        if (extensionPointInterfaces.isEmpty()) {
            throw new ExtensionRegistrationException(
                    "No @ExtensionPoint interface found for implementation: " + implementationClass.getName());
        }
        if (extensionPointInterfaces.size() > 1) {
            log.warn("Multiple @ExtensionPoint interfaces found for {}, using first one: {}",
                    implementationClass.getName(), extensionPointInterfaces.get(0).getName());
        }
        return extensionPointInterfaces.get(0);
    }

    private void collectAllInterfaces(Class<?> clazz, Set<Class<?>> interfaces) {
        if (clazz == null || clazz == Object.class) return;

        Collections.addAll(interfaces, clazz.getInterfaces());
        collectAllInterfaces(clazz.getSuperclass(), interfaces);
    }

    private ExtensionPointDefinition getOrCreateExtensionPointDefinition(String pointName, Class<?> pointInterface) {
        return extensionPointRegistry.computeIfAbsent(pointName, k ->
                ExtensionPointDefinition.builder()
                        .code(pointName)
                        .interfaceType(pointInterface)
                        .singleton(true)
                        .timeout(DEFAULT_TIMEOUT)
                        .build());
    }

    private ExtensionDefinition buildExtensionDefinition(Object instance, Class<?> implClass,
                                                         Extension annotation, ExtensionPointDefinition pointDefinition) {
        String extensionCode = generateExtensionCode(annotation.value(), implClass);

        return ExtensionDefinition.builder()
                .code(extensionCode)
                .point(pointDefinition)
                .implClass(implClass)
                .instance(instance)
                .tenant(normalizeWildcard(annotation.tenant()))
                .bizCode(normalizeWildcard(annotation.bizCode()))
                .useCase(normalizeWildcard(annotation.useCase()))
                .scenario(normalizeWildcard(annotation.scenario()))
                .env(normalizeWildcard(annotation.env()))
                .version(annotation.version())
                .order(annotation.order())
                .weight(annotation.weight())
                .traffic(annotation.traffic())
                .enabled(annotation.enabled())
                .condition(annotation.condition())
                .tags(annotation.tags())
                .startTime(annotation.startTime())
                .endTime(annotation.endTime())
                .async(annotation.async())
                .timeout(annotation.timeout() > 0 ? annotation.timeout() : DEFAULT_TIMEOUT)
                .build();
    }

    private String generateExtensionCode(String customCode, Class<?> implClass) {
        if (StringUtils.hasText(customCode)) {
            return customCode;
        }
        return DEFAULT_EXTENSION_CODE_PREFIX + implClass.getSimpleName();
    }

    private String normalizeWildcard(String value) {
        return StringUtils.hasText(value) && !"*".equals(value) ? value : "*";
    }

    private void preprocessExtensionDefinition(ExtensionDefinition definition) {
        // 编译路由规则
        compileRoutingPatterns(definition);

        // 调整启用状态
        adjustExtensionStatus(definition);
    }

    private void compileRoutingPatterns(ExtensionDefinition definition) {
        definition.setTenantPattern(compilePattern(definition.getTenant()));
        definition.setBizCodePattern(compilePattern(definition.getBizCode()));
        definition.setUseCasePattern(compilePattern(definition.getUseCase()));
        definition.setScenarioPattern(compilePattern(definition.getScenario()));
        definition.setEnvPattern(compilePattern(definition.getEnv()));

        if (StringUtils.hasText(definition.getCondition())) {
            definition.setConditionPredicate(expressionEvaluator.compile(definition.getCondition()));
        }
    }

    private Pattern compilePattern(String pattern) {
        return (pattern == null || "*".equals(pattern)) ?
                WILDCARD_PATTERN : Pattern.compile(pattern.replace("*", ".*"));
    }

    private void adjustExtensionStatus(ExtensionDefinition definition) {
        LocalDateTime now = LocalDateTime.now();

        // 检查开始时间
        if (StringUtils.hasText(definition.getStartTime())) {
            try {
                LocalDateTime startTime = LocalDateTime.parse(definition.getStartTime(), DATE_TIME_FORMATTER);
                if (now.isBefore(startTime)) {
                    definition.setEnabled(false);
                    log.debug("Extension {} disabled until {}", definition.getCode(), definition.getStartTime());
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid startTime format for extension {}: {}", definition.getCode(), definition.getStartTime());
            }
        }

        // 检查结束时间
        if (StringUtils.hasText(definition.getEndTime())) {
            try {
                LocalDateTime endTime = LocalDateTime.parse(definition.getEndTime(), DATE_TIME_FORMATTER);
                if (now.isAfter(endTime)) {
                    definition.setEnabled(false);
                    log.debug("Extension {} expired at {}", definition.getCode(), definition.getEndTime());
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid endTime format for extension {}: {}", definition.getCode(), definition.getEndTime());
            }
        }
    }

    private void validateExtensionDefinition(ExtensionDefinition definition) {
        // 更新：使用新的 isRegistered 方法进行重复注册检查
        if (extensionRepository.isRegistered(definition.getPoint().getCode(), definition.getCode())) {
            throw new ExtensionRegistrationException(
                    "Duplicate extension code: " + definition.getCode() + " for point: " + definition.getPoint().getCode());
        }

        // 验证必要字段
        if (!StringUtils.hasText(definition.getCode())) {
            throw new ExtensionRegistrationException("Extension code cannot be null or empty");
        }

        // 验证时间范围
        if (StringUtils.hasText(definition.getStartTime()) && StringUtils.hasText(definition.getEndTime())) {
            try {
                LocalDateTime start = LocalDateTime.parse(definition.getStartTime(), DATE_TIME_FORMATTER);
                LocalDateTime end = LocalDateTime.parse(definition.getEndTime(), DATE_TIME_FORMATTER);
                if (start.isAfter(end)) {
                    log.warn("Extension {} has invalid time range: startTime after endTime", definition.getCode());
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid time format for extension {}", definition.getCode());
            }
        }
    }

    private void executeRegistration(String extensionPoint, ExtensionDefinition definition) {
        // 更新：使用新的 register 方法进行注册
        ExtensionDefinition previous = extensionRepository.register(extensionPoint, definition);
        if (previous != null) {
            throw new ExtensionRegistrationException(
                    "Extension code already exists: " + definition.getCode());
        }

        // 更新本地缓存
        definition.getPoint().getExtensions().put(definition.getCode(), definition);
        registeredExtensionCodes.add(definition.getCode());

        log.info("Extension registered successfully: {} -> {} [tenant={}, biz={}, order={}]",
                extensionPoint, definition.getCode(), definition.getTenant(),
                definition.getBizCode(), definition.getOrder());
    }

    private void logRegistrationReport(long startTime) {
        RegistrationStatistics stats = getRegistrationStatistics();
        // 更新：使用新的 getStats 方法获取仓库统计
        ExtensionRepository.RepositoryStats repoStats = extensionRepository.getStats();

        log.info("""
                =========================================================================
                Bone Extension Registry - Registration Report
                =========================================================================
                Scanned Beans     : {}
                Successful        : {}
                Failed            : {}
                Extension Points  : {}
                Total Extensions  : {}
                Repository        : {} ({} points, {} extensions)
                Time Elapsed      : {} ms
                =========================================================================
                """,
                stats.getTotalScanned(), stats.getSuccessfulRegistrations(),
                stats.getFailedRegistrations(), stats.getExtensionPointCount(),
                stats.getTotalExtensions(), repoStats.getRepositoryName(),
                repoStats.getExtensionPointCount(), repoStats.getExtensionCount(),
                System.currentTimeMillis() - startTime);
    }

    private void warmupExtensionRouters() {
        try {
            Optional.ofNullable(applicationContext.getBean(DefaultExtPointRouter.class))
                    .ifPresent(router -> {
                        log.info("Warming up extension routers for {} extension points",
                                extensionPointRegistry.size());
                        router.warmupAll();
                    });
        } catch (Exception e) {
            log.warn("Failed to warm up extension routers", e);
        }
    }

    // ==================== 内部类 ====================

    /**
     * 注册统计信息
     */
    public static class RegistrationStatistics {
        private final int totalScanned;
        private final int successfulRegistrations;
        private final int failedRegistrations;
        private final int extensionPointCount;
        private final int totalExtensions;
        private final int uniqueExtensionCodes;

        public RegistrationStatistics(int totalScanned, int successfulRegistrations,
                                      int failedRegistrations, int extensionPointCount,
                                      int totalExtensions, int uniqueExtensionCodes) {
            this.totalScanned = totalScanned;
            this.successfulRegistrations = successfulRegistrations;
            this.failedRegistrations = failedRegistrations;
            this.extensionPointCount = extensionPointCount;
            this.totalExtensions = totalExtensions;
            this.uniqueExtensionCodes = uniqueExtensionCodes;
        }

        // Getters
        public int getTotalScanned() { return totalScanned; }
        public int getSuccessfulRegistrations() { return successfulRegistrations; }
        public int getFailedRegistrations() { return failedRegistrations; }
        public int getExtensionPointCount() { return extensionPointCount; }
        public int getTotalExtensions() { return totalExtensions; }
        public int getUniqueExtensionCodes() { return uniqueExtensionCodes; }
    }
}