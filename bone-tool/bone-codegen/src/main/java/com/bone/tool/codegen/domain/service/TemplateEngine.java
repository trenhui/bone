package com.bone.tool.codegen.domain.service;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.resource.loader.FileResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板引擎服务类
 * 使用Velocity作为代码生成的模板引擎
 */
@Component
public class TemplateEngine implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(TemplateEngine.class);
    private final VelocityEngine velocityEngine;
    
    // 模板缓存，提高性能
    private final Map<String, Template> templateCache = new ConcurrentHashMap<>();
    
    // 配置属性，支持外部化配置
    @Value("${codegen.template.loader.classpath:true}")
    private boolean enableClasspathLoader;
    
    @Value("${codegen.template.loader.file:false}")
    private boolean enableFileLoader;
    
    @Value("${codegen.template.file.path:}")
    private String fileLoaderPath;
    
    @Value("${codegen.template.cache.size:100}")
    private int cacheSize;
    
    @Value("${codegen.velocity.properties:}")
    private Map<String, String> velocityProperties = new HashMap<>();
    
    /**
     * 构造函数
     * 创建Velocity模板引擎实例
     */
    public TemplateEngine() {
        // 创建Velocity引擎实例
        this.velocityEngine = new VelocityEngine();
    }
    
    /**
     * Spring初始化方法
     * 替代构造函数中的初始化逻辑，更好地支持Spring生命周期管理
     */
    @Override
    public void afterPropertiesSet() {
        try {
            // 配置基本属性
            velocityEngine.setProperty("input.encoding", "UTF-8");
            velocityEngine.setProperty("output.encoding", "UTF-8");
            velocityEngine.setProperty("resource.manager.cache.enabled", "true");
            velocityEngine.setProperty("resource.manager.cache.size", String.valueOf(cacheSize));
            
            // 配置模板加载器
            configureResourceLoaders();
            
            // 添加自定义Velocity属性
            if (!velocityProperties.isEmpty()) {
                velocityProperties.forEach(velocityEngine::setProperty);
            }
            
            // 初始化引擎
            velocityEngine.init();
            
            log.info("Velocity模板引擎初始化完成，类路径加载器: {}, 文件加载器: {}, 文件路径: {}", 
                     enableClasspathLoader, enableFileLoader, fileLoaderPath);
        } catch (Exception e) {
            log.error("Velocity模板引擎初始化失败", e);
            throw new RuntimeException("模板引擎初始化失败", e);
        }
    }
    
    /**
     * 配置资源加载器
     * 支持类路径和文件系统两种加载方式
     */
    private void configureResourceLoaders() {
        StringBuilder loaderNames = new StringBuilder();
        
        // 配置类路径加载器
        if (enableClasspathLoader) {
            velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
            velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
            loaderNames.append("classpath");
        }
        
        // 配置文件系统加载器
        if (enableFileLoader && fileLoaderPath != null && !fileLoaderPath.isEmpty()) {
            if (loaderNames.length() > 0) {
                loaderNames.append(",");
            }
            loaderNames.append("file");
            velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, loaderNames.toString());
            velocityEngine.setProperty("file.resource.loader.class", FileResourceLoader.class.getName());
            velocityEngine.setProperty("file.resource.loader.path", fileLoaderPath);
            velocityEngine.setProperty("file.resource.loader.cache", "true");
            velocityEngine.setProperty("file.resource.loader.modificationCheckInterval", "10"); // 10秒检查一次修改
        }
    }

    /**
     * 根据模板生成代码
     * @param templatePath 模板路径
     * @param contextParams 上下文参数
     * @return 生成的代码内容
     * @throws IllegalArgumentException 当输入参数无效时
     * @throws RuntimeException 当生成过程发生错误时
     */
    public String generate(String templatePath, Map<String, Object> contextParams) {
        // 参数验证
        if (templatePath == null || templatePath.trim().isEmpty()) {
            throw new IllegalArgumentException("模板路径不能为空");
        }
        
        try {
            // 获取模板（使用缓存优化性能）
            Template template = getTemplate(templatePath);
            
            // 创建上下文
            VelocityContext context = new VelocityContext();
            
            // 添加参数
            if (contextParams != null) {
                contextParams.forEach(context::put);
            }
            
            // 生成代码
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            writer.flush();
            
            return writer.toString();
            
        } catch (Exception e) {
            log.error("模板生成失败，模板路径: {}, 参数: {}", templatePath, contextParams, e);
            throw new RuntimeException("模板生成失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取模板（支持缓存）
     * @param templatePath 模板路径
     * @return 模板对象
     */
    private Template getTemplate(String templatePath) {
        // 先从缓存获取
        return templateCache.computeIfAbsent(templatePath, path -> {
            try {
                Template template = velocityEngine.getTemplate(path, "UTF-8");
                log.debug("成功加载模板: {}", path);
                return template;
            } catch (Exception e) {
                log.error("加载模板失败: {}", path, e);
                throw new RuntimeException("加载模板失败: " + path, e);
            }
        });
    }
    
    /**
     * 清除模板缓存
     * 用于开发环境或模板更新时
     */
    public void clearTemplateCache() {
        templateCache.clear();
        // VelocityEngine没有直接的clearTemplateCache方法，重置资源管理器以清除内部缓存
        try {
            velocityEngine.setProperty("resource.manager.cache.enabled", "false");
            velocityEngine.setProperty("resource.manager.cache.enabled", "true");
            log.info("模板缓存已清除");
        } catch (Exception e) {
            log.warn("清除Velocity内部缓存时出错", e);
            // 即使出错，至少我们的自定义缓存已经被清除
        }
    }

    // 模板路径常量，便于维护
    private static final String TEMPLATE_PATH_SAAS = "templates/saas/";
    private static final String TEMPLATE_PATH_DDD = "templates/ddd/";
    
    /**
     * 生成SaaS模式实体类代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateEntityCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_SAAS + "entity.java.vm", contextParams);
    }

    /**
     * 生成DTO类代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateDtoCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_SAAS + "dto.java.vm", contextParams);
    }

    /**
     * 生成Mapper接口代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateMapperCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_SAAS + "mapper.java.vm", contextParams);
    }

    /**
     * 生成Service接口代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateServiceInterfaceCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_SAAS + "service.java.vm", contextParams);
    }

    /**
     * 生成Service实现代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateServiceImplCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_SAAS + "serviceImpl.java.vm", contextParams);
    }

    /**
     * 生成Controller代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateControllerCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_SAAS + "controller.java.vm", contextParams);
    }

    /**
     * 生成SQL脚本
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateSqlCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_SAAS + "sql.vm", contextParams);
    }

    /**
     * 生成DDD聚合根代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateAggregateCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_DDD + "aggregate.java.vm", contextParams);
    }

    /**
     * 生成DDD实体代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateDddEntityCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_DDD + "entity.java.vm", contextParams);
    }

    /**
     * 生成DDD领域服务代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateDomainServiceCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_DDD + "domainService.java.vm", contextParams);
    }

    /**
     * 生成DDD应用服务代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateAppServiceCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_DDD + "appService.java.vm", contextParams);
    }

    /**
     * 生成DDD仓储接口代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateRepositoryCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_DDD + "repository.java.vm", contextParams);
    }

    /**
     * 生成DDD仓储实现代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateRepositoryImplCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_DDD + "repositoryImpl.java.vm", contextParams);
    }

    /**
     * 生成DDD控制器代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateDddControllerCode(Map<String, Object> contextParams) {
        return generate(TEMPLATE_PATH_DDD + "controller.java.vm", contextParams);
    }
    
    /**
     * 异步生成代码
     * 用于生成大量代码文件时，避免阻塞主线程
     * @param templatePath 模板路径
     * @param contextParams 上下文参数
     * @return 包含生成结果的CompletableFuture
     */
    public java.util.concurrent.CompletableFuture<String> generateAsync(String templatePath, Map<String, Object> contextParams) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> generate(templatePath, contextParams));
    }
    
    /**
     * 批量生成代码
     * 用于一次生成多个模板文件
     * @param templatePaths 模板路径列表
     * @param contextParams 上下文参数
     * @return 模板路径到生成结果的映射
     */
    public Map<String, String> generateBatch(List<String> templatePaths, Map<String, Object> contextParams) {
        Map<String, String> results = new HashMap<>(templatePaths.size());
        for (String templatePath : templatePaths) {
            results.put(templatePath, generate(templatePath, contextParams));
        }
        return results;
    }
    
    /**
     * 验证模板是否存在
     * @param templatePath 模板路径
     * @return 如果模板存在返回true，否则返回false
     */
    public boolean templateExists(String templatePath) {
        try {
            velocityEngine.getTemplate(templatePath, "UTF-8");
            return true;
        } catch (Exception e) {
            log.debug("模板不存在: {}", templatePath);
            return false;
        }
    }
}