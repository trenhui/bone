package com.bone.metadata.sdk.sql.template;

import com.bone.metadata.sdk.domain.enums.SqlTemplateType;
import com.bone.metadata.sdk.domain.exception.TemplateLoadException;
import com.bone.metadata.sdk.domain.exception.TemplateNotFoundException;
import com.bone.metadata.sdk.domain.exception.TemplateValidationException;
import com.bone.metadata.sdk.sql.template.parser.TemplateContentParser;
import com.bone.metadata.sdk.sql.template.provider.TemplateSourceProvider;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.util.DigestUtils;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一SQL模板加载器，支持多源（注解、类路径）加载和回退机制。
 * 支持路径：
 * - sql/<package>/<RepositoryClass>/<methodName>.sql 或 sql/<module>/<methodName>.sql
 * - sql-templates/<RepositoryClass>/<methodName>.yaml 或 sql-templates/<module>/<methodName>.yaml
 */
public class UnifiedSqlTemplateLoader implements SqlTemplateLoader {
    private static final Logger log = LoggerFactory.getLogger(UnifiedSqlTemplateLoader.class);
    private static final String ANNOTATION_PREFIX = "annotation://";
    private static final String CLASSPATH_PREFIX = "classpath://";
    private static final String SQL_EXTENSION = ".sql";
    private static final String YAML_EXTENSION = ".yaml";
    private static final String DEFAULT_BASE_SQL_PATH = "sql/";
    private static final String DEFAULT_BASE_YAML_PATH = "sql-templates/";
    private static final String DEFAULT_VERSION = "1.0";

    private final SqlConfigProperties config;
    private final List<TemplateSourceProvider> sourceProviders;
    private final List<TemplateContentParser> contentParsers;
    private final TemplateSecurityValidator securityValidator;
    private final Cache<String, SqlTemplate> templateCache;
    private final ConcurrentHashMap<String, List<TemplateDescriptor>> descriptorCache;

    /**
     * 构造函数，初始化缓存。
     */
    public UnifiedSqlTemplateLoader(SqlConfigProperties config, List<TemplateSourceProvider> sourceProviders, List<TemplateContentParser> contentParsers, TemplateSecurityValidator securityValidator) {
        this.config = config;
        this.sourceProviders = sourceProviders;
        this.contentParsers = contentParsers;
        this.securityValidator = securityValidator;
        SqlConfigProperties.TemplateProperties templateProps = config.getTemplateProperties();
        this.templateCache = Caffeine.newBuilder().maximumSize(templateProps.getCacheSize()).expireAfterWrite(templateProps.getExpireHours(), TimeUnit.HOURS).build();
        this.descriptorCache = new ConcurrentHashMap<>();
    }

    /**
     * 加载SQL模板，支持多源回退（注解优先，类路径次之）。
     */
    @Override
    public SqlTemplate loadTemplate(String templateId) throws TemplateLoadException {
        return loadTemplate(null, templateId);
    }

    @Override
    public SqlTemplate loadTemplate(Method method, String templateId) throws TemplateLoadException {
        return templateCache.get(templateId, id -> {
            List<TemplateDescriptor> descriptors = descriptorCache.computeIfAbsent(templateId, this::generateCandidateDescriptors);
            TemplateNotFoundException lastException = null;

            for (TemplateDescriptor descriptor : descriptors) {
                try {
                    SqlTemplate template = loadTemplateWithFallback(method, descriptor);
                    if (template != null) {
                        log.info("成功加载模板: id={}, source={}", templateId, descriptor.getSourceUri());
                        return template;
                    }
                } catch (TemplateNotFoundException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("模板未在 {} 找到: {}", descriptor.getSourceUri(), templateId);
                    }
                    lastException = e;
                } catch (TemplateLoadException e) {
                    throw new TemplateLoadException("加载模板失败: id=" + templateId + ", source=" + descriptor.getSourceUri(), e);
                }
            }

            throw new TemplateNotFoundException("所有源均未找到模板: " + templateId + ", 尝试的路径: " + descriptors.stream().map(TemplateDescriptor::getSourceUri).collect(Collectors.toList()), lastException);
        });
    }

    private SqlTemplate loadTemplateWithFallback(Method method, TemplateDescriptor descriptor) throws TemplateLoadException {
        securityValidator.validateDescriptor(descriptor);
        
        // 获取适合的源提供器
        TemplateSourceProvider provider = findSourceProvider(descriptor.getSourceUri());
        TemplateSourceProvider.LoadedSource loaded = provider.load(method, descriptor);
        if (loaded == null) return null;

        // 验证和解析内容
        validateTemplateContent(loaded.getRawContent(), descriptor);
        SqlTemplate template = parseTemplateContent(loaded.getRawContent(), descriptor);

        // 设置模板元数据
        populateTemplateMetadata(template, descriptor, loaded);

        if (log.isInfoEnabled()) {
            log.info("加载模板: id={}, format={}, source={}", descriptor.getTemplateId(), descriptor.getFormat(), descriptor.getSourceUri());
        }
        return template;
    }
    
    /**
     * 查找适合指定URI的源提供器。
     */
    private TemplateSourceProvider findSourceProvider(String sourceUri) throws TemplateLoadException {
        return sourceProviders.stream()
                .filter(p -> p.supports(URI.create(sourceUri)))
                .findFirst()
                .orElseThrow(() -> new TemplateLoadException("无支持的源提供器: " + sourceUri));
    }
    
    /**
     * 验证模板内容是否符合安全要求和大小限制。
     */
    private void validateTemplateContent(String content, TemplateDescriptor descriptor) throws TemplateLoadException {
        securityValidator.validateContent(content, descriptor);
        SqlConfigProperties.TemplateProperties templateProps = config.getTemplateProperties();
        if (content.length() > templateProps.getMaxTemplateSize()) {
            throw new TemplateValidationException("模板大小超出限制: " + descriptor.getTemplateId() + ", 最大允许: " + templateProps.getMaxTemplateSize() + " 字符");
        }
    }
    
    /**
     * 解析模板内容。
     */
    private SqlTemplate parseTemplateContent(String content, TemplateDescriptor descriptor) throws TemplateLoadException {
        TemplateContentParser parser = contentParsers.stream()
                .filter(p -> p.supports(descriptor.getFormat()))
                .findFirst()
                .orElseThrow(() -> new TemplateLoadException("无支持的解析器: " + descriptor.getFormat()));
        return parser.parse(content, descriptor);
    }
    
    /**
     * 为模板设置元数据。
     */
    private void populateTemplateMetadata(SqlTemplate template, TemplateDescriptor descriptor, TemplateSourceProvider.LoadedSource loaded) {
        template.setId(descriptor.getTemplateId());
        template.setSource(descriptor.getSourceUri());
        template.addMetadata("origin", loaded.getOrigin());
        template.addMetadata("fetchedAt", String.valueOf(loaded.getFetchedAt()));
        template.addMetadata("version", descriptor.getVersion());
        template.addMetadata("checksum", calculateChecksum(loaded.getRawContent()));
        template.addMetadata("loadedFrom", descriptor.getSourceUri());
    }

    /**
     * 生成候选模板描述符列表，支持多源回退。
     * 支持两种模板 ID 格式：
     * - 标准格式：package.ClassName.methodName
     * - 简化格式：module/methodName（如 user/searchUsersPaged）
     */
    private List<TemplateDescriptor> generateCandidateDescriptors(String templateId) {
        // 验证模板 ID 格式
        if (!templateId.contains(".") && !templateId.contains("/")) {
            throw new IllegalArgumentException("模板ID格式无效，期望格式 'package.ClassName.methodName' 或 'module/methodName': " + templateId);
        }

        List<TemplateDescriptor> descriptors = new ArrayList<>();
        SqlConfigProperties.TemplateProperties templateProps = config.getTemplateProperties();
        
        // 根据加载优先级添加描述符
        boolean annotationFirst = "annotation-first".equals(templateProps.getLoadPriority());
        
        // 先添加优先级高的源
        descriptors.addAll(annotationFirst ? 
            Collections.singletonList(createAnnotationDescriptor(templateId)) : 
            createClasspathDescriptors(templateId));
        
        // 如果启用了回退，添加次要源
        if (templateProps.isFallbackEnabled()) {
            descriptors.addAll(annotationFirst ? 
                createClasspathDescriptors(templateId) : 
                Collections.singletonList(createAnnotationDescriptor(templateId)));
        }

        if (log.isDebugEnabled()) {
            List<String> sourceUris = descriptors.stream()
                .map(TemplateDescriptor::getSourceUri)
                .collect(Collectors.toList());
            log.debug("为模板 {} 生成 {} 个候选描述符: {}", templateId, descriptors.size(), sourceUris);
        }
        return descriptors;
    }

    /**
     * 创建注解源描述符。
     */
    private TemplateDescriptor createAnnotationDescriptor(String templateId) {
        return createTemplateDescriptor(templateId, ANNOTATION_PREFIX + templateId, SqlTemplateType.SQL);
    }

    /**
     * 创建类路径源描述符，支持 sql/ 和 sql-templates/ 路径。
     * 支持两种模板 ID 格式：
     * - package.ClassName.methodName（如 com.example.UserRepository.findById）
     * - module/methodName（如 user/searchUsersPaged）
     */
    private List<TemplateDescriptor> createClasspathDescriptors(String templateId) {
        List<TemplateDescriptor> descriptors = new ArrayList<>();

        try {
            if (templateId.contains("/")) {
                // 处理简化格式：module/methodName
                return createDescriptorsForSimplifiedFormat(templateId, descriptors);
            } else if (templateId.contains(".")) {
                // 处理标准格式：package.ClassName.methodName
                return createDescriptorsForStandardFormat(templateId, descriptors);
            } else {
                log.warn("无效的模板ID格式: {}. 期望 'package.ClassName.methodName' 或 'module/methodName'", templateId);
                return descriptors;
            }
        } catch (Exception e) {
            log.error("创建类路径描述符失败: templateId={}, 错误: {}", templateId, e.getMessage(), e);
            return descriptors;
        }
    }

    /**
     * 为简化格式模板ID创建描述符：module/methodName
     */
    private List<TemplateDescriptor> createDescriptorsForSimplifiedFormat(String templateId, List<TemplateDescriptor> descriptors) {
        String[] parts = templateId.split("/");
        if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
            log.warn("无效的简化格式模板ID: {}. 期望格式 'module/methodName'", templateId);
            return descriptors;
        }

        String moduleName = parts[0];
        String methodName = parts[1];

        // SQL 路径：sql/<module>/<methodName>.sql
        String sqlPath = String.format("%s%s/%s%s", DEFAULT_BASE_SQL_PATH, moduleName, methodName, SQL_EXTENSION);
        descriptors.add(createTemplateDescriptor(templateId, CLASSPATH_PREFIX + sqlPath, SqlTemplateType.SQL));
        
        // YAML 路径：sql-templates/<module>/<methodName>.yaml
        String yamlPath = String.format("%s%s/%s%s", DEFAULT_BASE_YAML_PATH, moduleName, methodName, YAML_EXTENSION);
        descriptors.add(createTemplateDescriptor(templateId, CLASSPATH_PREFIX + yamlPath, SqlTemplateType.MYBATIS));
        
        return descriptors;
    }

    /**
     * 为标准格式模板ID创建描述符：package.ClassName.methodName
     */
    private List<TemplateDescriptor> createDescriptorsForStandardFormat(String templateId, List<TemplateDescriptor> descriptors) {
        int lastDotIndex = templateId.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == templateId.length() - 1) {
            log.warn("无效的标准格式模板ID: {}", templateId);
            return descriptors;
        }

        String className = templateId.substring(0, lastDotIndex);
        String methodName = templateId.substring(lastDotIndex + 1);
        
        // 验证方法名
        if (methodName.trim().isEmpty()) {
            log.warn("无效的方法名在模板ID中: {}", templateId);
            return descriptors;
        }
        
        int classDotIndex = className.lastIndexOf('.');
        String packagePath = classDotIndex > 0 ? className.substring(0, classDotIndex).replace('.', '/') : "";
        String simpleClassName = classDotIndex > 0 ? className.substring(classDotIndex + 1) : className;

        // SQL 路径：sql/<package>/<RepositoryClass>/<methodName>.sql
        String sqlPath = String.format("%s%s/%s/%s%s", DEFAULT_BASE_SQL_PATH, packagePath, simpleClassName, methodName, SQL_EXTENSION);
        descriptors.add(createTemplateDescriptor(templateId, CLASSPATH_PREFIX + sqlPath, SqlTemplateType.SQL));
        
        // YAML 路径：sql-templates/<RepositoryClass>/<methodName>.yaml
        String yamlPath = String.format("%s%s/%s%s", DEFAULT_BASE_YAML_PATH, simpleClassName, methodName, YAML_EXTENSION);
        descriptors.add(createTemplateDescriptor(templateId, CLASSPATH_PREFIX + yamlPath, SqlTemplateType.MYBATIS));
        
        return descriptors;
    }
    
    /**
     * 创建模板描述符的辅助方法，避免重复代码。
     */
    private TemplateDescriptor createTemplateDescriptor(String templateId, String sourceUri, SqlTemplateType format) {
        return createTemplateDescriptor(templateId, sourceUri, format, null, null);
    }
    
    /**
     * 创建模板描述符的完整版本，包含更多元数据。
     */
    private TemplateDescriptor createTemplateDescriptor(String templateId, String sourceUri, SqlTemplateType format, 
                                                      String moduleName, String methodName) {
        Map<String, String> tags = new ConcurrentHashMap<>();
        if (moduleName != null) {
            tags.put("module", moduleName);
        }
        if (methodName != null) {
            tags.put("method", methodName);
        }
        
        return TemplateDescriptor.builder()
                .templateId(templateId)
                .sourceUri(sourceUri)
                .format(format)
                .tags(tags)
                .version(DEFAULT_VERSION)
                .checksum("")
                .ttl(Duration.ZERO)
                .fallbackId(null)
                .build();
    }

    /**
     * 批量加载模板。
     */
    @Override
    public Map<String, SqlTemplate> loadTemplates(Collection<String> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        return templateIds.parallelStream()
                .collect(Collectors.toConcurrentMap(
                        Function.identity(), 
                        this::loadTemplateWithExceptionHandling, 
                        (existing, replacement) -> existing
                ));
    }
    
    /**
     * 处理单个模板加载的异常情况。
     */
    private SqlTemplate loadTemplateWithExceptionHandling(String templateId) {
        try {
            return loadTemplate(templateId);
        } catch (TemplateNotFoundException e) {
            log.warn("模板未找到: id={}, 错误详情: {}", templateId, e.getMessage());
            throw new RuntimeException("模板未找到: " + templateId, e);
        } catch (TemplateLoadException e) {
            log.error("模板加载失败: id={}, 错误类型: {}, 错误详情: {}", 
                    templateId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("模板加载失败: " + templateId, e);
        } catch (Exception e) {
            log.error("批量加载模板时发生未知错误: id={}", templateId, e);
            throw new RuntimeException("加载模板时发生未知错误: " + templateId, e);
        }
    }

    /**
     * 刷新指定模板缓存。
     */
    @Override
    public void refreshTemplate(String templateId) {
        if (templateId == null || templateId.trim().isEmpty()) {
            log.warn("尝试刷新空模板ID，忽略操作");
            return;
        }
        
        templateCache.invalidate(templateId);
        descriptorCache.remove(templateId);
        
        if (log.isInfoEnabled()) {
            log.info("刷新模板缓存: id={}", templateId);
        }
    }
    
    /**
     * 刷新所有模板缓存。
     */
    public void refreshAllTemplates() {
        templateCache.invalidateAll();
        descriptorCache.clear();
        log.info("刷新所有模板缓存完成");
    }

    /**
     * 计算模板内容的MD5校验和。
     */
    private String calculateChecksum(String content) {
        try {
            if (content == null) {
                return DigestUtils.md5DigestAsHex(new byte[0]);
            }
            return DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("计算校验和失败: {}", e.getMessage());
            return DigestUtils.md5DigestAsHex(new byte[0]);
        }
    }

    /**
     * 应用启动后执行初始化操作。
     * 使用@EventListener注解自动监听ApplicationReadyEvent事件。
     */
    @EventListener
    public void onApplicationReadyEvent(ApplicationReadyEvent event) {
        log.info("应用启动完成，SQL模板加载器就绪");
        log.info("模板配置: 缓存大小={}, 过期时间={}小时, 回退机制={}, 加载优先级={}", 
                config.getTemplateProperties().getCacheSize(),
                config.getTemplateProperties().getExpireHours(),
                config.getTemplateProperties().isFallbackEnabled() ? "启用" : "禁用",
                config.getTemplateProperties().getLoadPriority());
    }
}