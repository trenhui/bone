package com.bone.engine.extension.definition;

import java.util.*;

import lombok.Data;

/**
 * 扩展点文档定义类
 * 
 * 用于封装和管理扩展点接口文档信息的完整结构，包含详细描述、
 * 使用说明、参数说明、返回值定义等核心文档属性
 * 
 * <h3>主要功能：</h3>
 * <ul>
 *   <li><strong>完整文档结构：</strong>支持标题、描述、使用场景等完整文档信息</li>
 *   <li><strong>参数说明：</strong>内置参数定义和验证规则</li>
 *   <li><strong>返回值说明：</strong>支持返回值类型和错误码定义</li>
 *   <li><strong>示例代码：</strong>提供基础和高级使用示例</li>
 *   <li><strong>FAQ管理：</strong>内置常见问题解答和变更历史记录</li>
 * </ul>
 * 
 * @author Bone Engine Team
 * @version 2.1.0
 * @see com.bone.engine.extension.annotation.ExtPointDoc
 */
@Data
public class ExtensionDocumentation {
    
    // 基本文档信息
    private String title;
    private String domain;
    private String category;
    private String description;
    private String usage;
    private String bestPractices;
    
    // 扩展点信息
    private String name;
    private String version;
    
    // 示例代码
    private String example;
    private String advancedExample;
    
    // 性能和维护
    private String performanceTips;
    private String notes;
    
    // 作者和创建信息
    private String creator;
    private String createDate;
    private String lastUpdater;
    private String lastUpdateDate;
    
    // 参数、返回值、FAQ和变更历史
    private List<Param> params;
    private Return returnInfo;
    private List<FAQ> faqs;
    private List<Change> changes;
    
    // 元数据
    private final Date createTime;
    private final Date updateTime;
    private String className;
    private String beanName;
}