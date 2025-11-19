package com.bone.engine.extension.core.router;

import com.bone.engine.extension.support.context.BizContext;
import java.util.Objects;

/**
 * 路由键，用于唯一标识扩展点路由请求
 * <p>
 * 提供统一的路由键实现，避免在不同组件中重复定义
 */
public class RouteKey {
    private final Class<?> extPointClass;
    private final String tenantCode;
    private final String bizCode;
    private final String useCase;
    private final String scenario;
    private final String env;
    private final String group;
    private final int hashCode;
    
    /**
     * 构造函数，从扩展点类和业务上下文创建路由键
     * 
     * @param extPointClass 扩展点接口类
     * @param context 业务上下文
     */
    public RouteKey(Class<?> extPointClass, BizContext<?> context) {
        this.extPointClass = extPointClass;
        this.tenantCode = context != null ? context.getTenantCode() : null;
        this.bizCode = context != null ? context.getBizCode() : null;
        this.useCase = context != null ? context.getUseCase() : null;
        this.scenario = context != null ? context.getScenario() : null;
        this.env = "default";
        this.group = "default";
        this.hashCode = Objects.hash(extPointClass, tenantCode, 
                                   bizCode, useCase, 
                                   scenario, env, group);
    }
    
    /**
     * 构造函数，指定所有路由参数
     * 
     * @param extPointClass 扩展点接口类
     * @param tenantCode 租户代码
     * @param bizCode 业务代码
     * @param useCase 用例
     * @param scenario 场景
     * @param env 环境
     * @param group 分组
     */
    public RouteKey(Class<?> extPointClass, String tenantCode, String bizCode, 
                   String useCase, String scenario, String env, String group) {
        this.extPointClass = extPointClass;
        this.tenantCode = tenantCode;
        this.bizCode = bizCode;
        this.useCase = useCase;
        this.scenario = scenario;
        this.env = env;
        this.group = group;
        this.hashCode = Objects.hash(extPointClass, tenantCode, 
                                   bizCode, useCase, 
                                   scenario, env, group);
    }
    /**
     * 静态工厂方法，创建路由键
     * @param context 业务上下文
     * @param extPointClass 扩展点接口类
     * @return 路由键实例
     */
    public static RouteKey create(BizContext<?> context, Class<?> extPointClass) {
        return new RouteKey(extPointClass, context);
    }
    public Class<?> getExtPointClass() {
        return extPointClass;
    }
    
    public String getTenantCode() {
        return tenantCode;
    }
    
    public String getBizCode() {
        return bizCode;
    }
    
    public String getUseCase() {
        return useCase;
    }
    
    public String getScenario() {
        return scenario;
    }
    
    public String getEnv() {
        return env;
    }
    
    public String getGroup() {
        return group;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteKey routeKey = (RouteKey) o;
        return Objects.equals(extPointClass, routeKey.extPointClass) &&
               Objects.equals(tenantCode, routeKey.tenantCode) &&
               Objects.equals(bizCode, routeKey.bizCode) &&
               Objects.equals(useCase, routeKey.useCase) &&
               Objects.equals(scenario, routeKey.scenario) &&
               Objects.equals(env, routeKey.env) &&
               Objects.equals(group, routeKey.group);
    }
    
    @Override
    public int hashCode() {
        return hashCode;
    }
    
    @Override
    public String toString() {
        return "RouteKey{" +
               "extPointClass=" + (extPointClass != null ? extPointClass.getSimpleName() : "null") +
               ", tenantCode='" + tenantCode + '\'' +
               ", bizCode='" + bizCode + '\'' +
               ", useCase='" + useCase + '\'' +
               ", scenario='" + scenario + '\'' +
               ", env='" + env + '\'' +
               ", group='" + group + '\'' +
               '}';
    }
}