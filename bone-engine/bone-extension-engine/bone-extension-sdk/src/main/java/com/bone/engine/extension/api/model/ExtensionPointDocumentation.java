package com.bone.engine.extension.api.model;

import java.util.*;

import com.bone.engine.extension.api.annotation.ExtensionDoc;
import lombok.Data;

/**
 * 扩展实现文档定义类
 * 
 * 用于封装和管理扩展实现类文档信息的完整结构，包含详细描述、
 * 适用场景、配置依赖、性能考量等核心文档属性
 * 
 * <h3>主要功能：</h3>
 * <ul>
 *   <li><strong>完整文档结构：</strong>支持标题、描述、适用场景等完整文档信息</li>
 *   <li><strong>FAQ管理：</strong>内置常见问题解答和变更历史记录</li>
 *   <li><strong>配置指导：</strong>提供配置依赖和推荐配置说明</li>
 *   <li><strong>性能指导：</strong>包含性能考量和资源使用说明</li>
 *   <li><strong>版本管理：</strong>支持版本信息和作者信息追踪</li>
 * </ul>
 * 
 * @author Bone Engine Team
 * @version 2.1.0
 * @see ExtensionDoc
 */
@Data
public class ExtensionPointDocumentation {
    
    // 基本文档信息
    private String title;
    private String description;
    private String applicableScenarios;
    private String scenarios;
    private String differences;
    private String implementationDetails;
    
    // 配置和依赖
    private String configurationDependencies;
    private String recommendedConfig;
    
    // 性能相关
    private String performanceConsiderations;
    private String performance;
    private String resourceUsage;
    
    // 注意事项和限制
    private String notes;
    private String limitations;
    
    // 版本和作者信息
    private String version;
    private String author;
    private String createDate;
    
    // FAQ和变更历史
    private List<FAQ> faqs;
    private List<Change> changes;
    
    // 元数据
    private final Date createTime;
    private final Date updateTime;
    private String className;
    private String beanName;
}