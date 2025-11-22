package com.bone.engine.extension.api.model.definition;

import com.bone.engine.extension.support.config.ExtPointConstants;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 扩展实现核心定义 - 路由匹配对象
 */
@Data
@Builder
@Accessors(chain = true)
public class ExtensionDefinition implements Comparable<ExtensionDefinition>, Serializable {
    private static final long serialVersionUID = 1L;

    // ==================== 基础信息 ====================
    private String code;
    private ExtensionPointDefinition point;
    private Class<?> implClass;
    private Object instance;

    // ==================== 路由维度 ====================
    private String tenant = "*";
    private String bizCode = "*";
    private String useCase = "*";
    private String scenario = "*";
    private String env = "*";
    private String version = "1.0.0";

    // ==================== 路由控制 ====================
    private int order = 100;
    private int weight = 100;
    private int traffic = 100;
    private boolean enabled = true;

    // ==================== 高级配置 ====================
    private String condition = "";
    private String[] tags = {};
    private String startTime = "";
    private String endTime = "";
    private boolean async = false;
    private int timeout = 0;

    // ==================== 运行时字段（非持久化） ====================
    /**
     * 租户匹配模式
     */
    private transient Pattern tenantPattern;

    /**
     * 业务编码匹配模式
     */
    private transient Pattern bizCodePattern;

    /**
     * 用例匹配模式
     */
    private transient Pattern useCasePattern;

    /**
     * 场景匹配模式
     */
    private transient Pattern scenarioPattern;

    /**
     * 环境匹配模式
     */
    private transient Pattern envPattern;

    /**
     * 条件表达式谓词
     */
    private transient Predicate<Object> conditionPredicate;

    /**
     * 获取业务身份标识
     */
    public String getBizIdentity() {
        return tenant + ExtPointConstants.SEPARATOR
                + bizCode + ExtPointConstants.SEPARATOR
                + useCase + ExtPointConstants.SEPARATOR
                + scenario;
    }

    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 检查是否匹配所有条件（通配符）
     */
    public boolean isWildcard() {
        return "*".equals(tenant) && "*".equals(bizCode) &&
                "*".equals(useCase) && "*".equals(scenario) &&
                "*".equals(env);
    }

    /**
     * 获取匹配优先级（匹配条件越具体，优先级越高）
     */
    public int getMatchPriority() {
        int priority = 0;
        if (!"*".equals(tenant)) priority += 1000;
        if (!"*".equals(bizCode)) priority += 100;
        if (!"*".equals(useCase)) priority += 10;
        if (!"*".equals(scenario)) priority += 1;
        return priority;
    }

    @Override
    public int compareTo(ExtensionDefinition other) {
        // 先按优先级排序
        int priorityCompare = Integer.compare(other.getMatchPriority(), this.getMatchPriority());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        // 再按order排序
        return Integer.compare(this.order, other.order);
    }

    // ==================== Builder 自定义方法 ====================

    public static class ExtensionDefinitionBuilder {

        /**
         * 设置租户并自动编译模式
         */
        public ExtensionDefinitionBuilder tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }

        /**
         * 设置业务编码并自动编译模式
         */
        public ExtensionDefinitionBuilder bizCode(String bizCode) {
            this.bizCode = bizCode;
            return this;
        }

        /**
         * 设置用例并自动编译模式
         */
        public ExtensionDefinitionBuilder useCase(String useCase) {
            this.useCase = useCase;
            return this;
        }

        /**
         * 设置场景并自动编译模式
         */
        public ExtensionDefinitionBuilder scenario(String scenario) {
            this.scenario = scenario;
            return this;
        }

        /**
         * 设置环境并自动编译模式
         */
        public ExtensionDefinitionBuilder env(String env) {
            this.env = env;
            return this;
        }
    }
}