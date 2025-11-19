package com.bone.engine.extension.api.model;

import java.time.LocalDateTime;
import java.util.*;

import com.bone.engine.extension.api.annotation.Extension;
import lombok.Data;

/**
 * 扩展点定义类
 * 
 * 用于封装和管理扩展点的完整配置信息，包含路由匹配规则、
 * 优先级控制、灰度发布策略等核心功能
 * 
 * <h3>主要功能：</h3>
 * <ul>
 *   <li><strong>多维度路由：</strong>支持租户、业务域、用例、场景等多维度路由匹配</li>
 *   <li><strong>动态条件：</strong>支持Spring EL表达式进行动态匹配判断</li>
 *   <li><strong>权重控制：</strong>支持基于权重的流量分配和灰度发布</li>
 *   <li><strong>时间范围：</strong>支持按时间范围控制扩展生效</li>
 *   <li><strong>参数约束：</strong>支持基于入参的精确匹配</li>
 * </ul>
 * 
 * @author Bone Engine Team
 * @version 2.1.0
 * @see Extension
 */
@Data
public class ExtensionImplementation {
    
    // 基本信息
    private String name;
    private String description;
    private String version;
    private boolean enabled;
    
    // 租户信息
    private String tenantCode;
    private Set<String> multiTenantCodes;
    private String userGroup;
    private Set<String> multiUserGroups;
    
    // 业务信息
    private String bizCode;
    private Set<String> multiBizCodes;
    private String useCase;
    private Set<String> multiUseCases;
    private String scenario;
    private Set<String> multiScenarios;
    
    // 环境信息
    private String env;
    private Set<String> multiEnvs;
    
    // 路由控制
    private String group;
    private int priority;
    private int weight;
    private int trafficRate;
    private boolean isDefault;
    
    // 条件匹配
    private String condition;
    private Map<String, String> tags;
    private Map<String, String> paramConstraints;
    
    // 时间控制
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    // 特殊配置
    private String dataSource;
    private String paymentMethod;
    private boolean asyncExecution;
    private long maxExecutionTime;
    
    // 元数据
    private final Date createTime;
    private final Date updateTime;
    private String className;
    private String beanName;
}
