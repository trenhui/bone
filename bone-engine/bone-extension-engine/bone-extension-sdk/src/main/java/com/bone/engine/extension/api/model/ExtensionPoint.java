package com.bone.engine.extension.api.model;

import java.util.Date;

import com.bone.engine.extension.api.annotation.ExtPoint;
import lombok.Data;

/**
 * 扩展点接口定义类
 * 
 * 用于封装和管理扩展点接口的完整配置信息，包含基本描述、
 * 废弃状态、事务配置等核心属性
 * 
 * <h3>主要功能：</h3>
 * <ul>
 *   <li><strong>扩展点标识：</strong>通过名称和描述唯一标识扩展点接口</li>
 *   <li><strong>废弃管理：</strong>跟踪扩展点的废弃状态和迁移路径</li>
 *   <li><strong>事务控制：</strong>统一管理扩展点实现的事务性要求</li>
 *   <li><strong>元数据管理：</strong>跟踪扩展点的创建和更新信息</li>
 *   <li><strong>状态验证：</strong>提供扩展点的有效性和完整性检查</li>
 * </ul>
 * 
 * @author Bone Engine Team
 * @version 2.1.0
 * @see ExtPoint
 */
@Data
public class ExtensionPoint {
    
    // 基本信息
    private String name;
    private String description;
    
    // 废弃信息
    private String deprecatedSince;
    private String deprecatedIn;
    
    // 事务配置
    private boolean transactional;
    
    // 元数据
    private final Date createTime;
    private final Date updateTime;
    private String className;
    private String beanName;
}