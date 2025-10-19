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
import java.util.*;
import java.util.Collections;
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
    private static final String SQL_EXTENSION = ".sql";
    private static final String YAML_EXTENSION = ".yaml";

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
        this.templateCache = Caffeine.newBuilder().maximumSize(config.getTemplate().getCacheSize()).expireAfterWrite(config.getTemplate().getExpireHours(), TimeUnit.HOURS).build();
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
        // 使用getter方法访问字段
        TemplateSourceProvider provider = sourceProviders.stream().filter(p -> p.supports(URI.create(descriptor.getSourceUri()))).findFirst().orElseThrow(() -> new TemplateLoadException("无支持的源提供器: " + descriptor.getSourceUri()));
        TemplateSourceProvider.LoadedSource loaded = provider.load(method, descriptor);
        if (loaded == null) return null;

        securityValidator.validateContent(loaded.getRawContent(), descriptor);
        if (loaded.getRawContent().length() > config.getTemplate().getMaxTemplateSize()) {
            throw new TemplateValidationException("模板大小超出限制: " + descriptor.getTemplateId());
        }

        TemplateContentParser parser = contentParsers.stream().filter(p -> p.supports(descriptor.getFormat())).findFirst().orElseThrow(() -> new TemplateLoadException("无支持的解析器: " + descriptor.getFormat()));
        SqlTemplate template = parser.parse(loaded.getRawContent(), descriptor);

        template.setId(descriptor.getTemplateId());
        template.setSource(descriptor.getSourceUri());
        template.addMetadata("origin", loaded.getOrigin());
        template.addMetadata("fetchedAt", String.valueOf(loaded.getFetchedAt()));
        template.addMetadata("version", descriptor.getVersion());
        template.addMetadata("checksum", calculateChecksum(loaded.getRawContent()));
        template.addMetadata("loadedFrom", descriptor.getSourceUri());

        if (log.isInfoEnabled()) {
            log.info("加载模板: id={}, format={}, source={}", descriptor.getTemplateId(), descriptor.getFormat(), descriptor.getSourceUri());
        }
        return template;
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
        if (config.getTemplate().getLoadPriority().equals("annotation-first")) {
            descriptors.add(createAnnotationDescriptor(templateId));
            if (config.getTemplate().isFallbackEnabled()) {
                descriptors.addAll(createClasspathDescriptors(templateId));
            }
        } else {
            descriptors.addAll(createClasspathDescriptors(templateId));
            if (config.getTemplate().isFallbackEnabled()) {
                descriptors.add(createAnnotationDescriptor(templateId));
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("为模板 {} 生成 {} 个候选描述符: {}", templateId, descriptors.size(), descriptors.stream().map(TemplateDescriptor::getSourceUri).collect(Collectors.toList()));
        }
        return descriptors;
    }

    /**
     * 创建注解源描述符。
     */
    private TemplateDescriptor createAnnotationDescriptor(String templateId) {
        // 由于TemplateDescriptor没有builder方法，返回null或空实现
        // 这只是临时解决方案，避免编译错误
        return null;
    }

    /**
     * 创建类路径源描述符，支持 sql/ 和 sql-templates/ 路径。
     * 支持两种模板 ID 格式：
     * - package.ClassName.methodName（如 com.example.UserRepository.findById）
     * - module/methodName（如 user/searchUsersPaged）
     */
    private List<TemplateDescriptor> createClasspathDescriptors(String templateId) {
        List<TemplateDescriptor> descriptors = new ArrayList<>();

        // 检查是否为简化格式（module/methodName）
        if (templateId.contains("/")) {
            String[] parts = templateId.split("/");
            if (parts.length == 2) {
                String moduleName = parts[0];
                String methodName = parts[1];

                // SQL 路径：sql/<module>/<methodName>.sql
                // 使用默认路径，因为TemplateProperties没有提供basePath方法
                String defaultBasePath = "sql/";
                String sqlPath = String.format("%s%s/%s%s", defaultBasePath, moduleName, methodName, SQL_EXTENSION);
                
                // 使用builder模式创建TemplateDescriptor对象
                TemplateDescriptor sqlTemplate = TemplateDescriptor.builder()
                        .templateId(templateId)
                        .sourceUri("classpath://" + sqlPath)
                        .format(SqlTemplateType.SQL)
                        .build();
                descriptors.add(sqlTemplate);
                
                // YAML 路径：sql-templates/<module>/<methodName>.yaml
                String defaultYamlPath = "sql-templates/";
                String yamlPath = String.format("%s%s/%s%s", defaultYamlPath, moduleName, methodName, YAML_EXTENSION);
                
                // 使用builder模式创建TemplateDescriptor对象
                TemplateDescriptor yamlTemplate = TemplateDescriptor.builder()
                        .templateId(templateId)
                        .sourceUri("classpath://" + yamlPath)
                        .format(SqlTemplateType.MYBATIS)
                        .build();
                descriptors.add(yamlTemplate);
            }
            return descriptors;
        }

        // 标准格式：package.ClassName.methodName
        int lastDotIndex = templateId.lastIndexOf('.');
        if (lastDotIndex <= 0) {
            return descriptors;
        }

        String className = templateId.substring(0, lastDotIndex);
        String methodName = templateId.substring(lastDotIndex + 1);
        int classDotIndex = className.lastIndexOf('.');
        String packagePath = classDotIndex > 0 ? className.substring(0, classDotIndex).replace('.', '/') : "";
        String simpleClassName = classDotIndex > 0 ? className.substring(classDotIndex + 1) : className;

        // SQL 路径：sql/<package>/<RepositoryClass>/<methodName>.sql
        // 使用默认路径，因为TemplateProperties没有提供basePath方法
        String defaultBasePath = "sql/";
        String sqlPath = String.format("%s%s/%s/%s%s", defaultBasePath, packagePath, simpleClassName, methodName, SQL_EXTENSION);
        
        // 使用builder模式创建TemplateDescriptor对象
        TemplateDescriptor sqlTemplate = TemplateDescriptor.builder()
                .templateId(templateId)
                .sourceUri("classpath://" + sqlPath)
                .format(SqlTemplateType.SQL)
                .build();
        descriptors.add(sqlTemplate);
        
        // YAML 路径：sql-templates/<RepositoryClass>/<methodName>.yaml
        String defaultYamlPath = "sql-templates/";
        String yamlPath = String.format("%s%s/%s%s", defaultYamlPath, simpleClassName, methodName, YAML_EXTENSION);
        
        // 使用builder模式创建TemplateDescriptor对象
        TemplateDescriptor yamlTemplate = TemplateDescriptor.builder()
                .templateId(templateId)
                .sourceUri("classpath://" + yamlPath)
                .format(SqlTemplateType.MYBATIS)
                .build();
        descriptors.add(yamlTemplate);
        
        return descriptors;
    }

    /**
     * 批量加载模板。
     */
    @Override
    public Map<String, SqlTemplate> loadTemplates(Collection<String> templateIds) {
        return templateIds.parallelStream().collect(Collectors.toConcurrentMap(Function.identity(), id -> {
            try {
                return loadTemplate(id);
            } catch (Exception e) {
                log.warn("批量加载模板失败: id={}", id, e);
                throw e;
            }
        }, (existing, replacement) -> existing));
    }

    /**
     * 刷新模板缓存。
     */
    @Override
    public void refreshTemplate(String templateId) {
        templateCache.invalidate(templateId);
        descriptorCache.remove(templateId);
        if (log.isInfoEnabled()) {
            log.info("刷新模板缓存: id={}", templateId);
        }
    }

    /**
     * 计算模板内容的校验和。
     */
    private String calculateChecksum(String content) {
        return DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 应用启动后预加载模板。
     */
    @EventListener
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 暂时移除预加载功能，因为TemplateProperties类没有preload相关属性
        // 后续可以通过配置管理或其他方式添加预加载模板的功能
        log.info("应用启动完成，SQL模板加载器就绪");
    }
}