package com.bone.engine.extension.core.register;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.api.annotation.ExtensionPoint;
import com.bone.engine.extension.api.exception.ExtensionRegistrationException;
import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.model.definition.ExtensionPointDefinition;
import com.bone.engine.extension.core.router.DefaultExtPointRouter;
import com.bone.engine.extension.support.repository.ExtensionRepository;
import com.bone.engine.extension.support.repository.ExtensionRepositoryFactory;
import com.bone.engine.extension.support.utils.AviatorExpressionEvaluator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Bone Extension SDK v2.0 GA - 终极扩展点注册中心
 * <p>
 * 融合业界最强实践的生产级实现（Dubbo + SOFAArk + Blade + 蚂蚁金服 + 字节系）
 * </p>
 *
 * @author Bone Engine Team
 * @since 2.0.0-GA 2025-11-20
 */
@Component
@Slf4j
public class ExtensionRegister implements ApplicationContextAware, SmartInitializingSingleton {

    // ==================== 常量 ====================
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern WILDCARD_PATTERN = Pattern.compile(".*");

    // ==================== 核心组件 ====================
    private ApplicationContext applicationContext;
    private Environment environment;

    // 双模型注册中心
    private final Map<String, ExtensionPointDefinition> pointRegistry = new ConcurrentHashMap<>();
    private final ExtensionRepository repository = ExtensionRepositoryFactory.getDefault();

    // 表达式引擎（性能之王）
    private final AviatorExpressionEvaluator exprEvaluator = new AviatorExpressionEvaluator();

    // ==================== 注册统计 ====================
    private final AtomicInteger totalScanned = new AtomicInteger();
    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger failedCount = new AtomicInteger();
    private final Set<String> registeredCodes = ConcurrentHashMap.newKeySet();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.environment = applicationContext.getEnvironment();
    }

    @PostConstruct
    private void init() {
        log.info("Bone Extension SDK v2.0 GA - ExtensionRegister initializing...");
    }

    @Override
    public void afterSingletonsInstantiated() {
        long start = System.currentTimeMillis();
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Extension.class);

        log.info("Starting registration of {} extension beans", beans.size());

        beans.values().forEach(bean -> {
            totalScanned.incrementAndGet();
            try {
                registerExtension(bean);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failedCount.incrementAndGet();
                log.error("Failed to register extension bean: {}", bean.getClass().getName(), e);
            }
        });

        log.info("""
                Bone Extension SDK v2.0 GA - Registration Completed
                =================================================
                Total Scanned : {}
                Success       : {}
                Failed        : {}
                Time Cost     : {}ms
                =================================================""",
                totalScanned.get(), successCount.get(), failedCount.get(),
                System.currentTimeMillis() - start);

        // 触发路由预热
        Optional.ofNullable(applicationContext.getBean(DefaultExtPointRouter.class))
                .ifPresent(DefaultExtPointRouter::warmupAll);
    }

    /**
     * 核心注册方法 - 支持热更新、安全校验、预编译、环境变量解析
     */
    public synchronized void registerExtension(Object extensionImpl) {
        Class<?> implClass = AopUtils.isAopProxy(extensionImpl)
                ? AopUtils.getTargetClass(extensionImpl)
                : extensionImpl.getClass();

        Extension extAnn = AnnotatedElementUtils.findMergedAnnotation(implClass, Extension.class);
        if (extAnn == null) return;

        // 解析环境变量
        Extension resolved = resolvePlaceholders(extAnn);

        // 查找扩展点接口
        Class<?> pointInterface = findExtensionPointInterface(implClass);
        if (pointInterface == null) {
            throw new ExtensionRegistrationException("No @ExtensionPoint interface found for " + implClass.getName());
        }

        String pointCode = pointInterface.getName();

        // 创建扩展点定义
        ExtensionPointDefinition pointDef = pointRegistry.computeIfAbsent(pointCode, k ->
                ExtensionPointDefinition.builder()
                        .code(pointCode)
                        .interfaceType(pointInterface)
                        .singleton(true)
                        .timeout(30)
                        .build());

        // 构建运行时模型
        ExtensionDefinition def = ExtensionDefinition.builder()
                .code(StringUtils.hasText(resolved.value()) ? resolved.value() : implClass.getSimpleName())
                .point(pointDef)
                .implClass(implClass)
                .instance(extensionImpl)
                .tenant(normalize(resolved.tenant()))
                .bizCode(normalize(resolved.bizCode()))
                .useCase(normalize(resolved.useCase()))
                .scenario(normalize(resolved.scenario()))
                .env(normalize(resolved.env()))
                .version(resolved.version())
                .order(resolved.order())
                .weight(resolved.weight())
                .traffic(resolved.traffic())
                .primary(resolved.primary())
                .enabled(resolved.enabled())
                .condition(resolved.condition())
                .tags(resolved.tags())
                .startTime(resolved.startTime())
                .endTime(resolved.endTime())
                .async(resolved.async())
                .timeout(resolved.timeout())
                .build();

        // 预编译 + 校验
        compileRoutingRules(def);
        validateAndAdjustStatus(def);

        // 检查重复注册
        if (!pointDef.getExtensions().containsKey(def.getCode())) {
            pointDef.getExtensions().put(def.getCode(), def);
            repository.registerExtension(pointCode, def);
            registeredCodes.add(def.getCode());

            log.info("Extension registered: {} -> {} [tenant={}, biz={}, order={}, traffic={}]",
                    pointCode, def.getCode(), def.getTenant(), def.getBizCode(),
                    def.getOrder(), def.getTraffic());
        } else {
            throw new ExtensionRegistrationException("Duplicate extension code: " + def.getCode());
        }
    }

    private Extension resolvePlaceholders(Extension ann) {
        return new Extension() {
            @Override public Class<? extends Annotation> annotationType() { return Extension.class; }
            @Override public String value() { return resolve(ann.value()); }
            @Override public String description() { return resolve(ann.description()); }
            @Override public String tenant() { return resolve(ann.tenant()); }
            @Override public String bizCode() { return resolve(ann.bizCode()); }
            @Override public String useCase() { return resolve(ann.useCase()); }
            @Override public String scenario() { return resolve(ann.scenario()); }
            @Override public String env() { return resolve(ann.env()); }
            @Override public String version() { return resolve(ann.version()); }
            @Override public int order() { return ann.order(); }
            @Override public int weight() { return ann.weight(); }
            @Override public int traffic() { return ann.traffic(); }
            @Override public boolean primary() { return ann.primary(); }
            @Override public boolean enabled() { return ann.enabled(); }
            @Override public String condition() { return resolve(ann.condition()); }
            @Override public String[] tags() { return Arrays.stream(ann.tags()).map(this::resolve).toArray(String[]::new); }
            @Override public String startTime() { return resolve(ann.startTime()); }
            @Override public String endTime() { return resolve(ann.endTime()); }
            @Override public boolean async() { return ann.async(); }
            @Override public int timeout() { return ann.timeout(); }

            private String resolve(String value) {
                return StringUtils.hasText(value) ? environment.resolvePlaceholders(value) : value;
            }
        };
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) && !"*".equals(value) ? value : "*";
    }

    private Class<?> findExtensionPointInterface(Class<?> implClass) {
        Set<Class<?>> candidates = new LinkedHashSet<>();
        collectInterfaces(implClass, candidates);

        List<Class<?>> pointInterfaces = candidates.stream()
                .filter(i -> AnnotatedElementUtils.hasAnnotation(i, ExtensionPoint.class))
                .toList();

        if (pointInterfaces.isEmpty()) {
            throw new ExtensionRegistrationException("No @ExtensionPoint interface found for " + implClass.getName());
        }
        if (pointInterfaces.size() > 1) {
            throw new ExtensionRegistrationException("Multiple @ExtensionPoint interfaces found for " + implClass.getName());
        }
        return pointInterfaces.get(0);
    }

    private void collectInterfaces(Class<?> clazz, Set<Class<?>> interfaces) {
        if (clazz == null || clazz == Object.class) return;
        interfaces.addAll(Arrays.asList(clazz.getInterfaces()));
        collectInterfaces(clazz.getSuperclass(), interfaces);
    }

    private void compileRoutingRules(ExtensionDefinition def) {
        def.setTenantPattern(compilePattern(def.getTenant()));
        def.setBizCodePattern(compilePattern(def.getBizCode()));
        def.setUseCasePattern(compilePattern(def.getUseCase()));
        def.setScenarioPattern(compilePattern(def.getScenario()));
        def.setEnvPattern(compilePattern(def.getEnv()));

        if (StringUtils.hasText(def.getCondition())) {
            def.setConditionPredicate(exprEvaluator.compile(def.getCondition()));
        }
    }

    private Pattern compilePattern(String pattern) {
        if (pattern == null || "*".equals(pattern)) {
            return WILDCARD_PATTERN;
        }
        return Pattern.compile(pattern.replace("*", ".*"));
    }

    private void validateAndAdjustStatus(ExtensionDefinition def) {
        LocalDateTime now = LocalDateTime.now();

        if (StringUtils.hasText(def.getStartTime())) {
            try {
                LocalDateTime start = LocalDateTime.parse(def.getStartTime(), DATE_TIME_FORMATTER);
                if (now.isBefore(start)) {
                    def.setEnabled(false);
                    log.info("Extension {} disabled until {}", def.getCode(), def.getStartTime());
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid startTime format: {}", def.getStartTime());
            }
        }

        if (StringUtils.hasText(def.getEndTime())) {
            try {
                LocalDateTime end = LocalDateTime.parse(def.getEndTime(), DATE_TIME_FORMATTER);
                if (now.isAfter(end)) {
                    def.setEnabled(false);
                    log.info("Extension {} expired at {}", def.getCode(), def.getEndTime());
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid endTime format: {}", def.getEndTime());
            }
        }
    }

    // ==================== 热更新支持 ====================
    public synchronized boolean unregisterExtension(String pointCode, String extCode) {
        ExtensionPointDefinition pointDef = pointRegistry.get(pointCode);
        if (pointDef != null && pointDef.getExtensions().remove(extCode) != null) {
            repository.removeExtension(pointCode, extCode);
            registeredCodes.remove(extCode);
            log.info("Extension unregistered: {} -> {}", pointCode, extCode);
            return true;
        }
        return false;
    }

    public Map<String, ExtensionPointDefinition> getAllPointDefinitions() {
        return Collections.unmodifiableMap(pointRegistry);
    }

    public int getRegisteredCount() {
        return registeredCodes.size();
    }
}