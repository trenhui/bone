package com.bone.engine.extension.register;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.config.ExtensionProperties;
import com.bone.engine.extension.event.ExtensionEventPublisher;
import com.bone.engine.extension.ExtPointConstants;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.util.ExtensionKeyGenerator;
import com.bone.engine.extension.version.ExtensionVersionManager;
import com.bone.engine.extension.utils.ExtPointUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 扩展提供者注册器，负责扫描、注册和管理所有的扩展实现
 * 与Spring容器深度集成，自动发现并注册带有@Extension注解的组件
 * 集成事件通知机制，在注册过程中发布相应事件
 * 支持基于配置的灵活定制，包括版本管理、包扫描路径等
 * 
 * @see ExtPoint 扩展点标记注解
 * @see Extension 扩展提供者标记注解
 * @since 1.0.0
 */
@Component
@EnableConfigurationProperties(ExtensionProperties.class)
public class ExtensionRegister implements ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(ExtensionRegister.class);

    private ApplicationContext applicationContext;
    
    // 从Spring容器注入的核心组件
    private final ExtPointRepository extPointRepository;
    private final ExtensionEventPublisher eventPublisher;
    private final ExtensionProperties configProperties;
    
    // 版本管理器，可选注入
    private ExtensionVersionManager versionManager;
    
    // 缓存已注册的扩展提供者，避免重复注册
    private final Set<Object> registeredProviders = ConcurrentHashMap.newKeySet();
    
    // 统一的扩展点注册服务
    private final ExtensionRegistry extensionRegistry;
    
    // 用于并行注册的线程池
    private ExecutorService registrationExecutor;
    
    // 跟踪注册失败的扩展提供者
    private final List<Map<String, Object>> registrationFailures = Collections.synchronizedList(new ArrayList<>());

    /**
     * 构造函数，通过Spring注入核心组件
     * 
     * @param extPointRepository 扩展点仓库，非空
     * @param eventPublisher 扩展事件发布器，非空
     * @param configProperties 扩展配置属性，非空
     */
    @Autowired
    public ExtensionRegister(ExtPointRepository extPointRepository, 
                           ExtensionEventPublisher eventPublisher,
                           ExtensionProperties configProperties) {
        // 创建统一的扩展点注册服务
        this.extensionRegistry = new ExtensionRegistry(extPointRepository, eventPublisher);
        Assert.notNull(extPointRepository, "ExtPointRepository must not be null");
        Assert.notNull(eventPublisher, "ExtensionEventPublisher must not be null");
        Assert.notNull(configProperties, "ExtensionProperties must not be null");
        this.extPointRepository = extPointRepository;
        this.eventPublisher = eventPublisher;
        this.configProperties = configProperties;
        log.info("ExtensionRegister initialized with config: scanPackages={}",
                Arrays.toString(configProperties.getScan().getBasePackages()));
    }
    
    /**
     * 初始化注册器的线程池
     */
    @PostConstruct
    public void initializeExecutor() {
        // 创建线程池用于并行注册扩展
        if (false) { // 默认为false，不启用并行注册
            int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
            int maxPoolSize = Math.max(4, corePoolSize * 2);
            registrationExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "extension-registration-" + counter.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
            );
            log.info("Extension registration executor initialized with corePoolSize={}, maxPoolSize={}",
                    corePoolSize, maxPoolSize);
        }
    }
    
    /**
     * 在Bean销毁时关闭线程池
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        if (registrationExecutor != null) {
            registrationExecutor.shutdown();
            try {
                if (!registrationExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    registrationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                registrationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Extension registration executor shutdown");
        }
    }
    
    /**
     * 注入版本管理器
     * 
     * @param versionManager 扩展点版本管理器
     */
    @Autowired(required = false)
    public void setVersionManager(ExtensionVersionManager versionManager) {
        this.versionManager = versionManager;
        log.info("Extension version manager injected: {}", versionManager != null ? versionManager.getClass().getSimpleName() : "null");
    }
    
    /**
     * 初始化时自动注册所有标记了@Extension注解的Bean
     */
    @PostConstruct
    public void init() {
        Assert.notNull(applicationContext, "ApplicationContext must not be null");
        
        final long startTime = System.currentTimeMillis();
        try {
            log.info("Starting extension provider registration process");
            
            // 扫描并注册所有扩展提供者
            scanAndRegisterExtensions();
            
            final long endTime = System.currentTimeMillis();
            log.info("Extension provider registration completed in {}ms. Total registered: {}, Failures: {}", 
                    (endTime - startTime), registeredProviders.size(), registrationFailures.size());
            
            // 如果有注册失败的扩展，记录详细信息
            if (!registrationFailures.isEmpty()) {
                log.warn("{} extension providers failed to register: {}", 
                        registrationFailures.size(), 
                        registrationFailures.stream()
                            .map(failure -> failure.get("className") + " - " + failure.get("error"))
                            .collect(Collectors.joining(", ")));
            }
        } catch (Exception e) {
            log.error("Failed to initialize extension provider registration", e);
            throw new RuntimeException("Failed to initialize extension provider registration", e);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 注册单个扩展提供者
     * 
     * @param extProvider 扩展提供者实例
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws IllegalStateException 当注册失败时抛出
     */
    public void registerExtension(Object extProvider) {
        // 1. 参数验证
        Assert.notNull(extProvider, "Extension provider must not be null");
        
        // 2. 检查是否已经注册过
        if (registeredProviders.contains(extProvider)) {
            if (true) { // 默认为启用日志
                log.debug("Extension provider already registered: {}", extProvider.getClass().getName());
            }
            return; // 避免重复注册
        }
        
        final long startTime = System.currentTimeMillis();
        try {
            // 3. 获取实际的类（处理代理对象）
            Class<?> extProviderClass = AopUtils.isAopProxy(extProvider) 
                    ? ClassUtils.getUserClass(extProvider) 
                    : extProvider.getClass();
            
            String providerClassName = extProviderClass.getCanonicalName();
            if (true) { // 默认为启用日志
                log.debug("Registering extension provider: {}", providerClassName);
            }
            
            // 4. 检查@Extension注解
            Extension extAnnotation = AnnotationUtils.findAnnotation(extProviderClass, Extension.class);
            if (extAnnotation == null) {
                 if (false) { // 默认为不启用安全检查
                     throw new IllegalArgumentException("Extension provider must be annotated with @Extension: " + providerClassName);
                 }
                  log.warn("Class {} does not have @Extension annotation, skipping registration", providerClassName);
                  return;
            }
            
            // 5. 获取扩展点接口 - 支持多接口实现
            List<Class<?>> extPointInterfaces = findExtPointInterfaces(extProviderClass);
            if (CollectionUtils.isEmpty(extPointInterfaces)) {
                if (false) { // 默认为不启用安全检查
                    throw new IllegalStateException("Extension provider must implement at least one interface annotated with @ExtPoint: " + providerClassName);
                }
                log.warn("Class {} does not implement any @ExtPoint interfaces, skipping registration", providerClassName);
                return;
            }
            
            // 6. 为每个扩展点接口注册实现
            for (Class<?> extPointInterface : extPointInterfaces) {
                String interfaceName = extPointInterface.getCanonicalName();
                
                // 7. 检查类型兼容性
                if (!extPointInterface.isInstance(extProvider)) {
                    String errorMsg = "Extension provider does not implement the extension point interface: " + interfaceName;
                    if (false) { // 默认为不启用安全检查
                        throw new IllegalArgumentException(errorMsg);
                    }
                    log.warn(errorMsg);
                    continue;
                }
                
                // 8. 生成唯一的注册键
                String registrationKey = generateRegistrationKey(interfaceName, extProvider);
                
                // 9. 发布注册前事件
                if (true) { // 默认为启用异步事件
                    eventPublisher.publishBeforeRegister(this, interfaceName, providerClassName);
                }
                
                // 10. 使用标准的put方法注册扩展
                extPointRepository.put(registrationKey, extProvider);
                registeredProviders.add(extProvider);
                
                // 11. 如果版本管理器存在且版本管理功能启用，注册版本信息
                if (versionManager != null && false) { // 默认为不启用版本管理
                    String version = extAnnotation.version();
                    try {
                        // 确保类型安全，强制转换为ExtPoint类型
                        @SuppressWarnings("unchecked")
                        Class<? extends ExtPoint> extPointClass = (Class<? extends ExtPoint>) extPointInterface;
                        
                        // 注册版本扩展
                        versionManager.registerVersionExtension(extPointClass, version, extProvider);
                        
                        // 发布版本注册事件
                        if (true) { // 默认为启用异步事件
                            eventPublisher.publishVersionRegister(this, interfaceName, version, providerClassName);
                        }
                        
                        // 检查是否为默认实现（bizCode为DEFAULT时视为默认实现）
                        if (ExtPointConstants.DEFAULT_VALUE.equals(extAnnotation.bizCode())) {
                            versionManager.setDefaultVersion(extPointClass, version);
                                if (true) { // 默认为启用日志
                                log.debug("Set default version {} for extension point {}", version, interfaceName);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to register version information for {} version {}", providerClassName, version, e);
                        if (false) { // 默认为不启用安全检查
                            throw new IllegalStateException("Failed to register version information", e);
                        }
                    }
                }
                
                // 12. 发布注册成功事件
                if (true) { // 默认为启用异步事件
                    eventPublisher.publishAfterRegister(this, interfaceName, providerClassName);
                }
                
                // 13. 记录注册信息，包含版本信息
                if (true) { // 默认为启用日志
                    log.debug("Registered extension provider {} for interface {} with version {}",
                            providerClassName, interfaceName, extAnnotation.version());
                }
            }
            
            final long endTime = System.currentTimeMillis();
            if (true) { // 默认为启用日志
                log.info("Successfully registered extension provider: {} (took {}ms)", 
                        providerClassName, (endTime - startTime));
            }
        } catch (Exception e) {
            // 记录注册失败信息
            Map<String, Object> failureInfo = new HashMap<>();
            failureInfo.put("className", extProvider.getClass().getName());
            failureInfo.put("error", e.getMessage());
            registrationFailures.add(failureInfo);
            
            // 严格模式下抛出异常，否则仅记录日志
            if (false) { // 默认为不启用安全检查
                throw new IllegalStateException("Failed to register extension provider: " + extProvider.getClass().getName(), e);
            }
            log.error("Failed to register extension provider: {}", extProvider.getClass().getName(), e);
        }
    }
    
    /**
     * 扫描并注册所有扩展提供者
     * 
     * @throws IOException 如果扫描过程中发生IO错误
     * @throws ClassNotFoundException 如果找不到类
     */
    private void scanAndRegisterExtensions() throws IOException, ClassNotFoundException {
        final long scanStartTime = System.currentTimeMillis();
        int scanCount = 0;
        int beanCount = 0;
        
        // 从配置的包路径扫描
        String[] configuredScanPackages = configProperties.getScan().getBasePackages();
        if (configuredScanPackages != null && configuredScanPackages.length > 0) {
            log.info("Scanning extensions from configured packages: {}", Arrays.toString(configuredScanPackages));
            // 并行注册暂不支持，使用串行方式
            if (false && registrationExecutor != null) {
                // 并行扫描多个包
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String scanPackage : configuredScanPackages) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            scanPackageAndRegister(scanPackage);
                        } catch (Exception e) {
                            log.error("Failed to scan package {}", scanPackage, e);
                            Map<String, Object> failure = new HashMap<>();
                            failure.put("className", "Package:" + scanPackage);
                            failure.put("error", e.getMessage());
                            registrationFailures.add(failure);
                        }
                    }, registrationExecutor));
                }
                // 等待所有扫描完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } else {
                // 串行扫描
                for (String scanPackage : configuredScanPackages) {
                    if (StringUtils.hasText(scanPackage)) {
                        try {
                            scanPackageAndRegister(scanPackage);
                            scanCount++;
                        } catch (Exception e) {
                            log.error("Failed to scan package {}", scanPackage, e);
                            Map<String, Object> failure = new HashMap<>();
                            failure.put("className", "Package:" + scanPackage);
                            failure.put("error", e.getMessage());
                            registrationFailures.add(failure);
                        }
                    }
                }
            }
        } else {
            // 默认扫描机制
            log.info("Scanning extensions using default mechanism");
        }

        // 从Spring容器中获取所有标记了@Extension注解的Bean
        Map<String, Object> extensionBeans = applicationContext.getBeansWithAnnotation(Extension.class);
        beanCount = extensionBeans.size();
        log.info("Found {} extension providers to register", beanCount);
        
        // 并行注册暂不支持，使用串行方式
        if (false && registrationExecutor != null && !extensionBeans.isEmpty()) {
            // 并行注册Spring Bean
            List<CompletableFuture<Void>> futures = extensionBeans.values().stream()
                .map(bean -> CompletableFuture.runAsync(() -> {
                    try {
                        registerExtension(bean);
                    } catch (Exception e) {
                        log.warn("Failed to register extension bean {}", bean.getClass().getName(), e);
                        Map<String, Object> failure = new HashMap<>();
                        failure.put("className", bean.getClass().getName());
                        failure.put("error", e.getMessage());
                        registrationFailures.add(failure);
                    }
                }, registrationExecutor))
                .collect(Collectors.toList());
            
            // 等待所有注册完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } else {
                    // 串行注册
            ExtensionRegistry.RegistrationResult result = extensionRegistry.registerAll(extensionBeans.values());
            registrationFailures.addAll(result.getFailures());
            log.info("Batch registration result: total={}, success={}, failed={}", 
                    result.getTotal(), result.getSuccess(), result.getFailed());
        }
        
        final long scanEndTime = System.currentTimeMillis();
        log.debug("Extension scanning and registration completed in {}ms. Scanned {} packages, processed {} beans",
                (scanEndTime - scanStartTime), scanCount, beanCount);
        
        // 发布注册完成事件（使用现有方法）
        log.info("Extension registration completed. Total extensions registered: {}", registeredProviders.size());
    }
    
    
    
    /**
     * 扫描指定包并注册扩展
     * 
     * @param basePackage 基础包路径
     * @throws IOException 如果扫描过程中发生IO错误
     * @throws ClassNotFoundException 如果找不到类
     */
    private void scanPackageAndRegister(String basePackage) throws IOException, ClassNotFoundException {
        // 包扫描实现
        String searchPath = "classpath*:" + basePackage.replace('.', '/') + "/**/*.class";
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(searchPath);
        
        MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory();
        
        for (Resource resource : resources) {
            if (resource.isReadable()) {
                MetadataReader reader = readerFactory.getMetadataReader(resource);
                String className = reader.getClassMetadata().getClassName();
                
                // 检查类是否包含@Extension注解
                if (reader.getAnnotationMetadata().isAnnotated(Extension.class.getName())) {
                    try {
                        Class<?> clazz = ClassUtils.forName(className, getClass().getClassLoader());
                        // 获取所有实现的扩展点接口
                            List<Class<?>> extPointInterfaces = ExtPointUtils.findExtPointInterfaces(clazz);
                            if (!extPointInterfaces.isEmpty()) {
                                // 获取Bean实例并注册
                                Object provider = applicationContext.getBean(clazz);
                                if (!extensionRegistry.registerExtension(provider)) {
                                    Map<String, Object> failure = new HashMap<>();
                                    failure.put("className", className);
                                    failure.put("error", "Registration rejected");
                                    registrationFailures.add(failure);
                                }
                            }
                    } catch (Exception e) {
                        log.warn("Failed to register extension class: {}", className, e);
                    }
                }
            }
        }
    }
    
    /**
     * 查找实现类中所有带有@ExtPoint注解的接口
     * 
     * @param implementationClass 实现类
     * @return 带有@ExtPoint注解的接口列表
     */
    private List<Class<?>> findExtPointInterfaces(Class<?> implementationClass) {
        return ExtPointUtils.findExtPointInterfaces(implementationClass);
    }
    
    /**
     * 生成唯一的注册键
     * 
     * @param interfaceName 接口名称
     * @param provider 提供者实例
     * @return 唯一的注册键
     */
    private String generateRegistrationKey(String interfaceName, Object provider) {
        return ExtensionKeyGenerator.generateExtensionKey(interfaceName, provider);
    }
    
    /**
     * 获取扩展点注册服务
     * 
     * @return 扩展点注册服务实例
     */
    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }
    
    /**
     * 获取已注册的扩展提供者数量
     * 
     * @return 已注册的扩展提供者数量
     */
    public int getRegisteredProviderCount() {
        return extensionRegistry.getRegisteredProviderCount();
    }
    
    /**
     * 清除注册缓存
     */
    public void clearRegisteredProviders() {
        registeredProviders.clear();
        log.info("Cleared extension provider registration cache");
    }
    
    /**
     * 内部注册扩展方法
     * 
     * @param interfaceClass 扩展点接口类
     * @param provider 扩展提供者
     */
    private void registerExtensionInternal(Class<?> interfaceClass, Object provider) {
        // 使用统一的注册服务
        @SuppressWarnings("unchecked")
        Class<Object> typedInterface = (Class<Object>) interfaceClass;
        extensionRegistry.registerImplementation(typedInterface, provider);
        
        // 处理版本信息（保留特定于版本管理的逻辑）
        try {
            Extension extAnnotation = AnnotationUtils.findAnnotation(provider.getClass(), Extension.class);
            if (extAnnotation != null && versionManager != null) {
                String version = extAnnotation.version();
                @SuppressWarnings("unchecked")
                Class<? extends ExtPoint> extPointClass = (Class<? extends ExtPoint>) interfaceClass;
                versionManager.registerVersionExtension(extPointClass, version, provider);
            }
        } catch (Exception e) {
            log.warn("Failed to process version information for {}", provider.getClass().getSimpleName(), e);
        }
    }
    
    /**
     * 手动注册扩展提供者（用于动态注册场景）
     * 
     * @param interfaceClass 扩展点接口类
     * @param provider 扩展提供者实例
     * @param <T> 扩展点类型
     */
    public <T> void registerExtension(Class<T> interfaceClass, T provider) {
        Assert.notNull(interfaceClass, "Interface class must not be null");
        Assert.notNull(provider, "Provider must not be null");
        
        if (!interfaceClass.isAnnotationPresent(ExtPoint.class)) {
            throw new IllegalArgumentException("Interface must be annotated with @ExtPoint: " + interfaceClass.getName());
        }
        
        if (!interfaceClass.isInstance(provider)) {
            throw new IllegalArgumentException("Provider does not implement the interface: " + interfaceClass.getName());
        }
        
        // 发布注册前事件
        eventPublisher.publishBeforeRegister(this, interfaceClass.getCanonicalName(), provider.getClass().getCanonicalName());
        
        // 使用统一的注册服务
        extensionRegistry.registerImplementation(interfaceClass, provider);
        
        // 处理版本相关逻辑
        try {
            Extension extAnnotation = AnnotationUtils.findAnnotation(provider.getClass(), Extension.class);
            if (extAnnotation != null && versionManager != null) {
                String version = extAnnotation.version();
                
                @SuppressWarnings("unchecked")
                Class<? extends ExtPoint> extPointClass = (Class<? extends ExtPoint>) interfaceClass;
                
                // 注册版本扩展
                versionManager.registerVersionExtension(extPointClass, version, provider);
                
                // 发布版本注册事件
                eventPublisher.publishVersionRegister(this, interfaceClass.getCanonicalName(), version, 
                        provider.getClass().getCanonicalName());
                
                // 设置默认或推荐版本
                if (ExtPointConstants.DEFAULT_VALUE.equals(extAnnotation.bizCode())) {
                    versionManager.setDefaultVersion(extPointClass, version);
                }
                
                // 暂时注释掉推荐版本设置，等待API完善
                // if (extAnnotation.recommended()) {
                //     versionManager.setRecommendedVersion(extPointClass, version);
                // }
            }
        } catch (Exception e) {
            log.warn("Failed to process version information for manually registered provider {}", 
                    provider.getClass().getSimpleName(), e);
        }
        
        // 发布注册成功事件
        eventPublisher.publishAfterRegister(this, interfaceClass.getCanonicalName(), provider.getClass().getCanonicalName());
        
        log.info("Manually registered extension provider: {} for interface: {}", 
                provider.getClass().getSimpleName(), interfaceClass.getSimpleName());
    }
    
    /**
     * 更新扩展点的版本信息
     * 
     * @param extPointClass 扩展点接口类
     * @param version 版本号
     * @param isDefault 是否设为默认版本
     * @param isRecommended 是否设为推荐版本
     * @param <T> 扩展点类型
     */
    public <T extends ExtPoint> void updateExtensionVersion(Class<T> extPointClass, String version, 
                                                           boolean isDefault, boolean isRecommended) {
        if (versionManager == null) {
            log.warn("Version manager not available, cannot update extension version");
            return;
        }
        
        try {
            if (isDefault) {
                versionManager.setDefaultVersion(extPointClass, version);
                log.info("Updated default version for {} to {}", extPointClass.getName(), version);
            }
            
            // 暂时注释掉推荐版本设置，等待API完善
            // if (isRecommended) {
            //     versionManager.setRecommendedVersion(extPointClass, version);
            //     log.info("Updated recommended version for {} to {}", extPointClass.getName(), version);
            // }
        } catch (Exception e) {
            log.error("Failed to update extension version for {}", extPointClass.getName(), e);
        }
    }
}
