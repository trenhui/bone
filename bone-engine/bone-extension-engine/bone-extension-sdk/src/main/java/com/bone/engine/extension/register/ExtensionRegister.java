package com.bone.engine.extension.register;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.config.ExtensionProperties;
import com.bone.engine.extension.event.ExtensionEventPublisher;
import com.bone.engine.extension.repository.ExtPointRepository;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ExtensionProperties configProperties;
    

    
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
        Assert.notNull(configProperties, "ExtensionProperties must not be null");
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
                    (endTime - startTime), extensionRegistry.getRegisteredProviderCount(), registrationFailures.size());
            
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
        try {
            // 直接调用extensionRegistry进行注册，移除重复的注册逻辑实现
            boolean registered = extensionRegistry.registerExtension(extProvider);
            if (registered) {
                log.debug("Successfully registered extension provider via ExtensionRegistry: {}", 
                        extProvider.getClass().getName());
            }
        } catch (Exception e) {
            // 记录注册失败信息
            Map<String, Object> failureInfo = new HashMap<>();
            failureInfo.put("className", extProvider != null ? extProvider.getClass().getName() : "null");
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
        log.info("Extension registration completed. Total extensions registered: {}", extensionRegistry.getRegisteredProviderCount());
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
                        // 获取Bean实例并注册
                        Object provider = applicationContext.getBean(clazz);
                        if (!extensionRegistry.registerExtension(provider)) {
                            Map<String, Object> failure = new HashMap<>();
                            failure.put("className", className);
                            failure.put("error", "Registration rejected");
                            registrationFailures.add(failure);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to register extension class: {}", className, e);
                    }
                }
            }
        }
    }
    
    // 直接使用ExtPointUtils.findExtPointInterfaces方法，无需本地封装
    

    
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
        extensionRegistry.clearRegisteredProviders();
        log.info("Cleared extension provider registration cache");
    }
    
    /**
     * 内部注册扩展方法
     * 
     * @param interfaceClass 扩展点接口类
     * @param provider 扩展提供者
     */
    // 不再需要此内部方法，功能已由ExtensionRegistry提供
    
    /**
     * 注册指定接口的扩展实现
     * 
     * @param interfaceClass 扩展点接口类
     * @param provider 扩展实现
     * @param <T> 扩展点类型
     */
    public <T> void registerExtension(Class<T> interfaceClass, T provider) {
        // 验证参数
        Assert.notNull(interfaceClass, "Interface class must not be null");
        Assert.notNull(provider, "Provider must not be null");
        
        // 验证类型兼容性
        if (!interfaceClass.isInstance(provider)) {
            throw new IllegalArgumentException("Provider does not implement the interface: " + interfaceClass.getName());
        }
        
        // 使用统一的注册服务
        extensionRegistry.registerImplementation(interfaceClass, provider);
        
    
        
        log.info("Manually registered extension provider: {} for interface: {}", 
                provider.getClass().getSimpleName(), interfaceClass.getSimpleName());
    }
    

}
