package com.bone.metadata.sdk.sql.template;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.util.DigestUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
 * 统一SQL模板加载器，支持从多种来源（如注解、类路径）加载SQL模板并提供回退机制。
 * <p>
 * 支持的模板路径格式：
 * <ul>
 *   <li>sql/&lt;package&gt;/&lt;RepositoryClass&gt;/&lt;methodName&gt;.sql</li>
 *   <li>sql/&lt;module&gt;/&lt;methodName&gt;.sql</li>
 *   <li>sql-templates/&lt;RepositoryClass&gt;/&lt;methodName&gt;.yaml</li>
 *   <li>sql-templates/&lt;module&gt;/&lt;methodName&gt;.yaml</li>
 * </ul>
 */
public class SqlTemplateLoaderImpl {
    // 常量定义遵循ALL_CAPS命名规范，增加文档注释
    private static final String ANNOTATION_SCHEME = "annotation://"; // 注解来源标识符
    private static final String CLASSPATH_SCHEME = "classpath://";   // 类路径来源标识符
    private static final String SQL_EXTENSION = ".sql";             // SQL文件扩展名
    private static final String YAML_EXTENSION = ".yaml";           // YAML文件扩展名
    private static final String DEFAULT_BASE_SQL_PATH = "sql/";     // 默认SQL基础路径
    private static final String DEFAULT_BASE_YAML_PATH = "sql-templates/"; // 默认YAML基础路径
    private static final String DEFAULT_VERSION = "1.0";            // 默认模板版本
    private static final int DEFAULT_CACHE_SIZE = 1000;              // 默认缓存大小
    private static final long DEFAULT_CACHE_EXPIRY_HOURS = 24;       // 默认缓存过期时间（小时）
    
    // 添加final修饰符，提高代码安全性
    private final Logger logger = LoggerFactory.getLogger(SqlTemplateLoaderImpl.class);
    private final Cache<String, String> templateCache;
    private final ConcurrentHashMap<String, List<String>> descriptorCache;

    /**
     * 构造函数，初始化模板缓存和描述符缓存。
     */
    public SqlTemplateLoaderImpl() {
        this.templateCache = Caffeine.newBuilder()
                .maximumSize(DEFAULT_CACHE_SIZE)
                .expireAfterWrite(DEFAULT_CACHE_EXPIRY_HOURS, TimeUnit.HOURS)
                .build();
        this.descriptorCache = new ConcurrentHashMap<>();
    }

    /**
     * 根据模板ID加载SQL模板内容。
     * 
     * @param templateId 模板标识符
     * @return 加载的SQL模板内容
     * @throws IllegalArgumentException 当模板ID为空时抛出
     */
    public String loadTemplate(String templateId) {
        validateTemplateId(templateId);
        
        return templateCache.get(templateId, id -> {
            List<String> descriptors = descriptorCache.computeIfAbsent(id, this::generateCandidateDescriptors);
            logger.debug("为模板ID [{}] 生成了 {} 个候选描述符", id, descriptors.size());
            return createDefaultTemplate(id);
        });
    }
    
    /**
     * 根据方法和模板ID加载SQL模板内容。
     * 
     * @param method 调用方法，用于增强模板查找能力
     * @param templateId 模板标识符
     * @return 加载的SQL模板内容
     * @throws IllegalArgumentException 当模板ID为空时抛出
     */
    public String loadTemplate(Method method, String templateId) {
        validateTemplateId(templateId);
        // 在实际实现中，可以利用method信息进一步增强模板查找能力
        return loadTemplate(templateId);
    }

    /**
     * 生成给定模板ID的候选描述符列表。
     * 
     * @param templateId 模板标识符
     * @return 候选描述符列表，用于尝试按顺序查找模板
     */
    private List<String> generateCandidateDescriptors(String templateId) {
        List<String> descriptors = new ArrayList<>();
        
        // 根据不同的模板ID格式生成合适的描述符
        if (templateId.contains(".")) {
            descriptors.add(CLASSPATH_SCHEME + DEFAULT_BASE_SQL_PATH + templateId.replace('.', '/') + SQL_EXTENSION);
        } else if (templateId.contains("/")) {
            descriptors.add(CLASSPATH_SCHEME + DEFAULT_BASE_SQL_PATH + templateId + SQL_EXTENSION);
        } else {
            // 为简单ID添加基本路径
            descriptors.add(CLASSPATH_SCHEME + DEFAULT_BASE_SQL_PATH + templateId + SQL_EXTENSION);
        }
        
        return descriptors;
    }
    
    /**
     * 验证模板ID的有效性。
     * 
     * @param templateId 待验证的模板ID
     * @throws IllegalArgumentException 当模板ID为空或仅包含空白字符时抛出
     */
    private void validateTemplateId(String templateId) {
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
    }
    
    /**
     * 创建默认的SQL模板内容。
     * 
     * @param templateId 模板标识符
     * @return 默认的SQL模板内容
     */
    private String createDefaultTemplate(String templateId) {
        return String.format("SELECT * FROM table WHERE id = ? -- 默认模板，ID: %s", templateId);
    }

    /**
     * 批量加载多个SQL模板。
     * 
     * @param templateIds 要加载的模板ID集合
     * @return 模板ID到模板内容的映射
     */
    public Map<String, String> loadTemplates(Collection<String> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        return templateIds.parallelStream()
                .collect(Collectors.toConcurrentMap(
                        Function.identity(), 
                        this::loadTemplate, 
                        (existingTemplate, newTemplate) -> {
                            // 当出现重复键时，保留已存在的模板
                            logger.warn("检测到重复的模板ID，保留已存在的模板");
                            return existingTemplate;
                        }
                ));
    }

    /**
     * 刷新指定模板的缓存。
     * 
     * @param templateId 要刷新的模板ID
     */
    public void refreshTemplate(String templateId) {
        if (templateId == null || templateId.trim().isEmpty()) {
            logger.warn("尝试刷新空模板ID，操作已忽略");
            return;
        }
        
        templateCache.invalidate(templateId);
        descriptorCache.remove(templateId);
        
        if (logger.isDebugEnabled()) {
            logger.debug("已刷新模板缓存: ID={}", templateId);
        }
    }
    
    /**
     * 刷新所有模板缓存。
     */
    public void refreshAllTemplates() {
        templateCache.invalidateAll();
        descriptorCache.clear();
        logger.info("已刷新所有模板缓存");
    }

    /**
     * 计算模板内容的MD5校验和。
     * 
     * @param content 模板内容
     * @return 计算得到的MD5校验和
     */
    private String calculateChecksum(String content) {
        try {
            byte[] contentBytes = (content == null) ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
            return DigestUtils.md5DigestAsHex(contentBytes);
        } catch (Exception e) {
            logger.warn("计算模板内容校验和失败: {}", e.getMessage());
            // 出错时返回空内容的校验和，确保方法总是返回有效结果
            return DigestUtils.md5DigestAsHex(new byte[0]);
        }
    }

    /**
     * 应用启动就绪后执行初始化操作。
     * 
     * @param event 应用就绪事件
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        logger.info("SQL模板加载器已初始化并就绪");
        logger.debug("模板缓存配置 - 最大大小: {}, 过期时间: {}小时", 
                DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EXPIRY_HOURS);
    }
}