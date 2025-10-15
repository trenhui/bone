package com.bone.tool.codegen.domain.service;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.Map;

/**
 * 模板引擎服务类
 * 使用Velocity作为代码生成的模板引擎
 */
public class TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(TemplateEngine.class);
    private final VelocityEngine velocityEngine;

    /**
     * 构造函数
     * 初始化Velocity模板引擎
     */
    public TemplateEngine() {
        // 创建Velocity引擎
        velocityEngine = new VelocityEngine();
        
        // 配置模板加载器为类路径加载器
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        
        // 配置字符编码
        velocityEngine.setProperty("input.encoding", "UTF-8");
        velocityEngine.setProperty("output.encoding", "UTF-8");
        
        // 初始化引擎
        velocityEngine.init();
        
        log.info("Velocity模板引擎初始化完成");
    }

    /**
     * 根据模板生成代码
     * @param templatePath 模板路径
     * @param contextParams 上下文参数
     * @return 生成的代码内容
     */
    public String generate(String templatePath, Map<String, Object> contextParams) {
        try {
            // 获取模板
            Template template = velocityEngine.getTemplate(templatePath, "UTF-8");
            
            // 创建上下文
            VelocityContext context = new VelocityContext();
            
            // 添加参数
            if (contextParams != null) {
                contextParams.forEach(context::put);
            }
            
            // 生成代码
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            
            return writer.toString();
            
        } catch (Exception e) {
            log.error("模板生成失败，模板路径: {}", templatePath, e);
            throw new RuntimeException("模板生成失败: " + e.getMessage());
        }
    }

    /**
     * 生成SaaS模式实体类代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateEntityCode(Map<String, Object> contextParams) {
        return generate("templates/saas/entity.java.vm", contextParams);
    }

    /**
     * 生成DTO类代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateDtoCode(Map<String, Object> contextParams) {
        return generate("templates/saas/dto.java.vm", contextParams);
    }

    /**
     * 生成Mapper接口代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateMapperCode(Map<String, Object> contextParams) {
        return generate("templates/saas/mapper.java.vm", contextParams);
    }

    /**
     * 生成Service接口代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateServiceInterfaceCode(Map<String, Object> contextParams) {
        return generate("templates/saas/service.java.vm", contextParams);
    }

    /**
     * 生成Service实现代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateServiceImplCode(Map<String, Object> contextParams) {
        return generate("templates/saas/serviceImpl.java.vm", contextParams);
    }

    /**
     * 生成Controller代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateControllerCode(Map<String, Object> contextParams) {
        return generate("templates/saas/controller.java.vm", contextParams);
    }

    /**
     * 生成SQL脚本
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateSqlCode(Map<String, Object> contextParams) {
        return generate("templates/saas/sql.vm", contextParams);
    }

    /**
     * 生成DDD聚合根代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateAggregateCode(Map<String, Object> contextParams) {
        return generate("templates/ddd/aggregate.java.vm", contextParams);
    }

    /**
     * 生成DDD实体代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateDddEntityCode(Map<String, Object> contextParams) {
        return generate("templates/ddd/entity.java.vm", contextParams);
    }

    /**
     * 生成DDD领域服务代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateDomainServiceCode(Map<String, Object> contextParams) {
        return generate("templates/ddd/domainService.java.vm", contextParams);
    }

    /**
     * 生成DDD应用服务代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateAppServiceCode(Map<String, Object> contextParams) {
        return generate("templates/ddd/appService.java.vm", contextParams);
    }

    /**
     * 生成DDD仓储接口代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateRepositoryCode(Map<String, Object> contextParams) {
        return generate("templates/ddd/repository.java.vm", contextParams);
    }

    /**
     * 生成DDD仓储实现代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateRepositoryImplCode(Map<String, Object> contextParams) {
        return generate("templates/ddd/repositoryImpl.java.vm", contextParams);
    }

    /**
     * 生成DDD控制器代码
     * @param contextParams 上下文参数
     * @return 生成的代码
     */
    public String generateDddControllerCode(Map<String, Object> contextParams) {
        return generate("templates/ddd/controller.java.vm", contextParams);
    }
}