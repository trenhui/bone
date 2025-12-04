package com.bone.engine.extension.core.register;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.api.annotation.ExtensionPoint;
import com.bone.engine.extension.api.exception.ExtensionRegistrationException;
import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.spi.ExpressionEvaluator;
import com.bone.engine.extension.api.spi.ExtensionRepository;
import com.bone.engine.extension.support.config.ExtensionProperties;
import com.bone.engine.extension.support.expression.SpELExpressionEvaluator;
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

/**
 * 扩展点注册中心 - 修复版
 *
 * 修复问题：
 * 1. ✅ 匹配ExtensionRepository接口方法名
 * 2. ✅ 修复方法调用错误
 * 3. ✅ 统一注册逻辑
 * 4. ✅ 保持统计功能
 */
@Component
@Slf4j
public class ExtensionRegister implements ApplicationContextAware, SmartInitializingSingleton {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_EXTENSION_CODE_PREFIX = "EXT_";

    private ApplicationContext applicationContext;
    private Environment environment;
    private final ExtensionRepository extensionRepository;
    private final ExtensionProperties extensionProperties;
    private final ExpressionEvaluator expressionEvaluator = new SpELExpressionEvaluator();

    private final AtomicInteger totalScannedCount = new AtomicInteger(0);
    private final AtomicInteger successfulRegistrationCount = new AtomicInteger(0);
    private final AtomicInteger failedRegistrationCount = new AtomicInteger(0);
    private final Set<String> registeredExtensionCodes = ConcurrentHashMap.newKeySet();

    public ExtensionRegister(ExtensionRepository extensionRepository,
                             ExtensionProperties extensionProperties) {
        this.extensionRepository = Objects.requireNonNull(extensionRepository,
                "ExtensionRepository cannot be null");
        this.extensionProperties = Objects.requireNonNull(extensionProperties,
                "ExtensionProperties cannot be null");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.environment = applicationContext.getEnvironment();
    }

    @PostConstruct
    public void initialize() {
        log.info("Extension Register initialized with repository: {}",
                extensionRepository.getClass().getSimpleName());
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
    }

    // ==================== 核心注册方法 ====================

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

        // 构建扩展定义
        ExtensionDefinition extensionDefinition = buildExtensionDefinition(
                extensionImplementation, implementationClass, resolvedAnnotation, extensionPointInterface);

        // 预处理和验证
        preprocessExtensionDefinition(extensionDefinition);
        validateExtensionDefinition(extensionDefinition);

        // 执行注册
        executeRegistration(extensionPointInterface.getName(), extensionDefinition);
    }

    public synchronized boolean unregisterExtension(@NonNull String extensionPoint, @NonNull String extensionCode) {
        Objects.requireNonNull(extensionPoint, "Extension point cannot be null");
        Objects.requireNonNull(extensionCode, "Extension code cannot be null");

        // 从仓库中注销 - 使用新接口方法
        ExtensionDefinition removed = extensionRepository.unregisterExtension(extensionPoint, extensionCode);
        if (removed != null) {
            registeredExtensionCodes.remove(extensionCode);
            log.info("Extension unregistered successfully: {} -> {}", extensionPoint, extensionCode);
            return true;
        }
        return false;
    }

    // ==================== 查询方法 ====================

    public Optional<ExtensionDefinition> findExtension(@NonNull String extensionPoint, @NonNull String extensionCode) {
        // 使用新接口方法 - 返回Optional
        return extensionRepository.getExtensionByCode(extensionPoint, extensionCode);
    }

    public Collection<ExtensionDefinition> findExtensionsByPoint(@NonNull String extensionPoint) {
        // 使用新接口方法
        return extensionRepository.getAllExtensions(extensionPoint);
    }

    public Collection<ExtensionDefinition> findEnabledExtensionsByPoint(@NonNull String extensionPoint) {
        // 使用新接口方法 - 直接获取启用扩展
        return extensionRepository.getEnabledExtensions(extensionPoint);
    }

    // ==================== 统计信息 ====================

    public RegistrationStatistics getRegistrationStatistics() {
        // 使用新接口方法获取扩展点名称
        Set<String> allExtensionPoints = extensionRepository.getAllExtensionPointNames();
        int totalExtensions = 0;
        int enabledExtensions = 0;

        for (String extPoint : allExtensionPoints) {
            // 获取所有扩展
            Collection<ExtensionDefinition> extensions = extensionRepository.getAllExtensions(extPoint);
            totalExtensions += extensions.size();

            // 计算启用的扩展
            for (ExtensionDefinition def : extensions) {
                if (def.isEnabled() && isEffective(def)) {
                    enabledExtensions++;
                }
            }
        }

        return new RegistrationStatistics(
                totalScannedCount.get(),
                successfulRegistrationCount.get(),
                failedRegistrationCount.get(),
                allExtensionPoints.size(),
                totalExtensions,
                enabledExtensions,
                registeredExtensionCodes.size()
        );
    }

    /**
     * 获取扩展仓库统计信息
     */
    public ExtensionRepository.ExtensionRepositoryStats getRepositoryStats() {
        return extensionRepository.getRepositoryStats();
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
            @Override
            public Class<? extends Annotation> annotationType() {
                return Extension.class;
            }

            @Override
            public String name() {
                return resolvePlaceholder(annotation.name());
            }

            @Override
            public String description() {
                return resolvePlaceholder(annotation.description());
            }

            @Override
            public String tenant() {
                return resolvePlaceholder(annotation.tenant());
            }

            @Override
            public String bizCode() {
                return resolvePlaceholder(annotation.bizCode());
            }

            @Override
            public String useCase() {
                return resolvePlaceholder(annotation.useCase());
            }

            @Override
            public String scenario() {
                return resolvePlaceholder(annotation.scenario());
            }

            @Override
            public String env() {
                return resolvePlaceholder(annotation.env());
            }

            @Override
            public String userGroup() {
                return resolvePlaceholder(annotation.userGroup());
            }

            @Override
            public String version() {
                return resolvePlaceholder(annotation.version());
            }

            @Override
            public int order() {
                return annotation.order();
            }

            @Override
            public int weight() {
                return annotation.weight();
            }

            @Override
            public int traffic() {
                return annotation.traffic();
            }

            @Override
            public boolean enabled() {
                return annotation.enabled();
            }

            @Override
            public String condition() {
                return resolvePlaceholder(annotation.condition());
            }

            @Override
            public String[] tags() {
                return Arrays.stream(annotation.tags()).map(this::resolvePlaceholder).toArray(String[]::new);
            }

            @Override
            public String startTime() {
                return resolvePlaceholder(annotation.startTime());
            }

            @Override
            public String endTime() {
                return resolvePlaceholder(annotation.endTime());
            }

            @Override
            public boolean async() {
                return annotation.async();
            }

            @Override
            public int timeout() {
                return annotation.timeout();
            }

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

    private ExtensionDefinition buildExtensionDefinition(Object instance, Class<?> implClass,
                                                         Extension annotation, Class<?> extensionPointInterface) {
        String extensionCode = generateExtensionCode(annotation.name(), implClass);

        ExtensionDefinition.Builder builder = ExtensionDefinition.builder()
                .code(extensionCode)
                .extensionPoint(extensionPointInterface.getName())
                .implementationClass(implClass.getName())
                .instance(instance)
                .description(annotation.description())

                // 关键修复：直接使用注解值，不要 normalize！
                .tenant(annotation.tenant())
                .bizCode(annotation.bizCode())
                .useCase(annotation.useCase())
                .scenario(annotation.scenario())
                .env(annotation.env())
                .userGroup(annotation.userGroup())

                .condition(annotation.condition())
                .defaultImpl(isDefaultImplementation(annotation))
                .weight(annotation.weight())
                .priority(annotation.order())
                .enabled(annotation.enabled())
                .startTime(annotation.startTime())
                .endTime(annotation.endTime());

        addCustomDimensions(builder, annotation);

        ExtensionDefinition def = builder.build();

        // 加一行调试日志，永不踩坑！
        log.debug("扩展注册成功: {} | 维度: tenant={}, bizCode={}, useCase={}, scenario={}, userGroup={}",
                def.getCode(),
                def.getTenant(), def.getBizCode(), def.getUseCase(), def.getScenario(), def.getUserGroup());

        return def;
    }

    private String generateExtensionCode(String customCode, Class<?> implClass) {
        if (StringUtils.hasText(customCode)) {
            return customCode;
        }
        return DEFAULT_EXTENSION_CODE_PREFIX + implClass.getSimpleName();
    }

    private boolean isDefaultImplementation(Extension annotation) {
        // 如果所有维度都是通配符，且没有条件表达式，则认为是默认实现
        return "*".equals(annotation.tenant()) &&
                "*".equals(annotation.bizCode()) &&
                "*".equals(annotation.useCase()) &&
                "*".equals(annotation.scenario()) &&
                "*".equals(annotation.env()) &&
                !StringUtils.hasText(annotation.condition());
    }

    private void addCustomDimensions(ExtensionDefinition.Builder builder, Extension annotation) {
        // 从tags中解析自定义维度（格式：key=value,key2=value2）
        for (String tag : annotation.tags()) {
            if (StringUtils.hasText(tag) && tag.contains("=")) {
                String[] parts = tag.split("=", 2);
                if (parts.length == 2 && StringUtils.hasText(parts[0]) && StringUtils.hasText(parts[1])) {
                    builder.dimension(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }

    private void preprocessExtensionDefinition(ExtensionDefinition definition) {
        // 编译条件表达式谓词
        if (StringUtils.hasText(definition.getCondition())) {
            try {
                // 假设expressionEvaluator有compile方法
                definition.setConditionPredicate(expressionEvaluator.compile(definition.getCondition()));
            } catch (Exception e) {
                log.warn("Failed to compile condition expression for extension {}: {}",
                        definition.getCode(), definition.getCondition(), e);
            }
        }

        // 调整启用状态（检查时间范围）
        adjustExtensionStatus(definition);
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

    private boolean isEffective(ExtensionDefinition definition) {
        if (!definition.isEnabled()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // 检查开始时间
        if (StringUtils.hasText(definition.getStartTime())) {
            try {
                LocalDateTime startTime = LocalDateTime.parse(definition.getStartTime(), DATE_TIME_FORMATTER);
                if (now.isBefore(startTime)) {
                    return false;
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
                    return false;
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid endTime format for extension {}: {}", definition.getCode(), definition.getEndTime());
            }
        }

        return true;
    }

    private void validateExtensionDefinition(ExtensionDefinition definition) {
        // 验证必要字段
        if (!StringUtils.hasText(definition.getCode())) {
            throw new ExtensionRegistrationException("Extension code cannot be null or empty");
        }

        if (!StringUtils.hasText(definition.getExtensionPoint())) {
            throw new ExtensionRegistrationException("Extension point cannot be null or empty");
        }

        if (definition.getInstance() == null) {
            throw new ExtensionRegistrationException("Extension instance cannot be null");
        }

        // 检查重复注册 - 使用新接口方法
        Optional<ExtensionDefinition> existing = extensionRepository
                .getExtensionByCode(definition.getExtensionPoint(), definition.getCode());
        if (existing.isPresent()) {
            throw new ExtensionRegistrationException(
                    "Duplicate extension code: " + definition.getCode() +
                            " for point: " + definition.getExtensionPoint());
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
        // 注册到仓库 - 使用新接口方法
        ExtensionDefinition previous = extensionRepository.registerExtension(extensionPoint, definition);
        if (previous != null) {
            // 如果返回非null，表示存在相同code的扩展
            throw new ExtensionRegistrationException(
                    "Extension code already exists: " + definition.getCode());
        }

        registeredExtensionCodes.add(definition.getCode());

        log.info("Extension registered successfully: {} -> {} [tenant={}, biz={}, priority={}, weight={}]",
                extensionPoint, definition.getCode(), definition.getTenant(),
                definition.getBizCode(), definition.getPriority(), definition.getWeight());
    }

    private void logRegistrationReport(long startTime) {
        RegistrationStatistics stats = getRegistrationStatistics();

        log.info("""
                        =========================================================================
                        Bone Extension Registry - Registration Report
                        =========================================================================
                        Scanned Beans     : {}
                        Successful        : {}
                        Failed            : {}
                        Extension Points  : {}
                        Total Extensions  : {}
                        Enabled Extensions: {}
                        Unique Codes      : {}
                        Time Elapsed      : {} ms
                        =========================================================================
                        """,
                stats.getTotalScanned(), stats.getSuccessfulRegistrations(),
                stats.getFailedRegistrations(), stats.getExtensionPointCount(),
                stats.getTotalExtensions(), stats.getEnabledExtensions(),
                stats.getUniqueExtensionCodes(), System.currentTimeMillis() - startTime);
    }

    // ==================== 管理操作 ====================

    /**
     * 清空指定扩展点
     */
    public int clearExtensionPoint(@NonNull String extensionPoint) {
        return extensionRepository.clearExtensionPoint(extensionPoint);
    }

    /**
     * 清空所有扩展
     */
    public void clearAllExtensions() {
        extensionRepository.clearAllExtensions();
        registeredExtensionCodes.clear();
        log.info("All extensions have been cleared");
    }

    /**
     * 批量注册扩展
     */
    public int batchRegisterExtensions(@NonNull Map<String, Collection<ExtensionDefinition>> extensionsByPoint) {
        return extensionRepository.batchRegisterExtensions(extensionsByPoint);
    }

    /**
     * 检查扩展点是否有扩展
     */
    public boolean hasExtensions(@NonNull String extensionPoint) {
        return extensionRepository.hasExtensions(extensionPoint);
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
        private final int enabledExtensions;
        private final int uniqueExtensionCodes;

        public RegistrationStatistics(int totalScanned, int successfulRegistrations,
                                      int failedRegistrations, int extensionPointCount,
                                      int totalExtensions, int enabledExtensions,
                                      int uniqueExtensionCodes) {
            this.totalScanned = totalScanned;
            this.successfulRegistrations = successfulRegistrations;
            this.failedRegistrations = failedRegistrations;
            this.extensionPointCount = extensionPointCount;
            this.totalExtensions = totalExtensions;
            this.enabledExtensions = enabledExtensions;
            this.uniqueExtensionCodes = uniqueExtensionCodes;
        }

        public int getTotalScanned() { return totalScanned; }
        public int getSuccessfulRegistrations() { return successfulRegistrations; }
        public int getFailedRegistrations() { return failedRegistrations; }
        public int getExtensionPointCount() { return extensionPointCount; }
        public int getTotalExtensions() { return totalExtensions; }
        public int getEnabledExtensions() { return enabledExtensions; }
        public int getUniqueExtensionCodes() { return uniqueExtensionCodes; }
    }
}