package com.bone.engine.extension.api.model.definition;

import lombok.*;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 扩展定义 - 企业级最佳实践精简版
 *
 * 设计原则：
 * 1. ✅ 专注核心：只包含路由必需的维度匹配功能
 * 2. ✅ 性能优先：缓存优化 + 模式预编译
 * 3. ✅ 代码简洁：移除冗余字段，易于维护
 * 4. ✅ 扩展友好：支持自定义维度和表达式
 */
@Getter
@Setter
@ToString(exclude = {"instance", "compiledPatterns", "cachedHashCode"})
@EqualsAndHashCode(exclude = {"createTime", "compiledPatterns", "cachedHashCode"})
public class ExtensionDefinition implements Serializable, Comparable<ExtensionDefinition> {

    private static final long serialVersionUID = 1L;
    private static final String WILDCARD = "*";

    // ==================== 核心标识字段 ====================
    /** 扩展唯一编码 */
    private String code;

    /** 扩展点接口类名 */
    private String extensionPoint;

    /** 实现类名 */
    private String implementationClass;

    /** 扩展实现实例 */
    private Object instance;

    /** 扩展描述 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;

    // ==================== 维度匹配字段 ====================
    /** 租户标识（支持通配符） */
    private String tenant = WILDCARD;

    /** 业务编码（支持通配符） */
    private String bizCode = WILDCARD;

    /** 用例标识 */
    private String useCase = WILDCARD;

    /** 场景标识 */
    private String scenario = WILDCARD;

    /** 环境标识 */
    private String env = WILDCARD;

    /** 自定义维度规则 */
    private final Map<String, String> dimensionRules = new ConcurrentHashMap<>();

    /** 条件表达式 */
    private String condition;

    // ==================== 路由控制字段 ====================
    /** 是否默认实现 */
    private boolean defaultImpl = false;

    /** 权重（0-100） */
    private int weight = 100;

    /** 优先级（越小优先级越高） */
    private int priority = 100;

    /** 是否启用 */
    private boolean enabled = true;

    /** 生效开始时间（字符串格式） */
    private String startTime;

    /** 生效结束时间（字符串格式） */
    private String endTime;

    // ==================== 性能优化字段 ====================
    private transient Pattern tenantPattern;
    private transient Pattern bizCodePattern;
    private transient Pattern useCasePattern;
    private transient Pattern scenarioPattern;
    private transient Pattern envPattern;
    private transient Predicate<Object> conditionPredicate;
    private transient Integer cachedHashCode;
    private transient String cachedKey;
    private transient Boolean cachedWildcard;
    private transient Integer cachedMatchScore;

    // ==================== 构造方法 ====================
    public ExtensionDefinition() {
        this.createTime = LocalDateTime.now();
    }

    // ==================== 维度操作方法 ====================

    public void setDimensionRule(@NonNull String key, @Nullable String value) {
        if (value == null || !StringUtils.hasText(value) || WILDCARD.equals(value)) {
            dimensionRules.remove(key);
        } else {
            dimensionRules.put(key, value);
        }
        clearCaches();
    }

    public void setDimensionRules(@NonNull Map<String, String> rules) {
        dimensionRules.clear();
        rules.forEach((key, value) -> {
            if (StringUtils.hasText(value) && !WILDCARD.equals(value)) {
                dimensionRules.put(key, value);
            }
        });
        clearCaches();
    }

    @Nullable
    public String getDimension(@NonNull String key) {
        return dimensionRules.get(key);
    }

    // ==================== 模式编译方法 ====================

    public void compilePatterns() {
        this.tenantPattern = compilePattern(tenant);
        this.bizCodePattern = compilePattern(bizCode);
        this.useCasePattern = compilePattern(useCase);
        this.scenarioPattern = compilePattern(scenario);
        this.envPattern = compilePattern(env);
    }

    private Pattern compilePattern(String pattern) {
        if (pattern == null || WILDCARD.equals(pattern)) {
            return null; // 通配符不需要模式
        }
        try {
            // 将简单的通配符*转换为正则表达式.*
            String regex = pattern.replace("*", ".*");
            return Pattern.compile(regex);
        } catch (Exception e) {
            // 如果编译失败，使用精确匹配
            return Pattern.compile(Pattern.quote(pattern));
        }
    }

    public void setConditionPredicate(Predicate<Object> predicate) {
        this.conditionPredicate = predicate;
    }

    // ==================== 核心匹配方法 ====================

    public boolean isWildcard() {
        if (cachedWildcard == null) {
            cachedWildcard = WILDCARD.equals(tenant) &&
                    WILDCARD.equals(bizCode) &&
                    WILDCARD.equals(useCase) &&
                    WILDCARD.equals(scenario) &&
                    WILDCARD.equals(env) &&
                    dimensionRules.isEmpty() &&
                    !StringUtils.hasText(condition);
        }
        return cachedWildcard;
    }

    public int getMatchScore() {
        if (cachedMatchScore == null) {
            int score = 0;
            if (!WILDCARD.equals(tenant)) score += 1000;
            if (!WILDCARD.equals(bizCode)) score += 100;
            if (!WILDCARD.equals(useCase)) score += 50;
            if (!WILDCARD.equals(scenario)) score += 10;
            if (!WILDCARD.equals(env)) score += 1;
            score += dimensionRules.size() * 5;
            if (StringUtils.hasText(condition)) score += 20;
            cachedMatchScore = score;
        }
        return cachedMatchScore;
    }

    public Map<String, String> getAllDimensions() {
        Map<String, String> all = new LinkedHashMap<>();

        if (!WILDCARD.equals(tenant)) all.put("tenant", tenant);
        if (!WILDCARD.equals(bizCode)) all.put("bizCode", bizCode);
        if (!WILDCARD.equals(useCase)) all.put("useCase", useCase);
        if (!WILDCARD.equals(scenario)) all.put("scenario", scenario);
        if (!WILDCARD.equals(env)) all.put("env", env);

        all.putAll(dimensionRules);
        return Collections.unmodifiableMap(all);
    }

    // ==================== 实用方法 ====================

    public String getCacheKey() {
        if (cachedKey == null) {
            StringBuilder key = new StringBuilder();
            key.append(extensionPoint).append(":");

            getAllDimensions().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> key.append(e.getKey()).append("=").append(e.getValue()).append("|"));

            if (StringUtils.hasText(condition)) {
                key.append("condition=").append(condition.hashCode());
            }

            cachedKey = key.toString();
        }
        return cachedKey;
    }

    public boolean isValid() {
        return StringUtils.hasText(code) &&
                StringUtils.hasText(extensionPoint) &&
                instance != null;
    }

    // ==================== 清空缓存 ====================
    private void clearCaches() {
        cachedHashCode = null;
        cachedKey = null;
        cachedWildcard = null;
        cachedMatchScore = null;
    }

    // ==================== Builder模式 ====================

    public static Builder builder() {
        return new Builder();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {
        private final ExtensionDefinition definition = new ExtensionDefinition();

        public Builder code(String code) {
            definition.code = code;
            return this;
        }

        public Builder extensionPoint(String extensionPoint) {
            definition.extensionPoint = extensionPoint;
            return this;
        }

        public Builder implementationClass(String implementationClass) {
            definition.implementationClass = implementationClass;
            return this;
        }

        public Builder instance(Object instance) {
            definition.instance = instance;
            return this;
        }

        public Builder description(String description) {
            definition.description = description;
            return this;
        }

        public Builder tenant(String tenant) {
            definition.tenant = StringUtils.hasText(tenant) ? tenant : WILDCARD;
            return this;
        }

        public Builder bizCode(String bizCode) {
            definition.bizCode = StringUtils.hasText(bizCode) ? bizCode : WILDCARD;
            return this;
        }

        public Builder useCase(String useCase) {
            definition.useCase = StringUtils.hasText(useCase) ? useCase : WILDCARD;
            return this;
        }

        public Builder scenario(String scenario) {
            definition.scenario = StringUtils.hasText(scenario) ? scenario : WILDCARD;
            return this;
        }

        public Builder env(String env) {
            definition.env = StringUtils.hasText(env) ? env : WILDCARD;
            return this;
        }

        public Builder dimension(String key, String value) {
            definition.setDimensionRule(key, value);
            return this;
        }

        public Builder dimensions(Map<String, String> dimensions) {
            definition.setDimensionRules(dimensions);
            return this;
        }

        public Builder condition(String condition) {
            definition.condition = condition;
            return this;
        }

        public Builder defaultImpl(boolean defaultImpl) {
            definition.defaultImpl = defaultImpl;
            return this;
        }

        public Builder weight(int weight) {
            definition.weight = Math.max(0, Math.min(100, weight));
            return this;
        }

        public Builder priority(int priority) {
            definition.priority = Math.max(1, priority);
            return this;
        }

        public Builder enabled(boolean enabled) {
            definition.enabled = enabled;
            return this;
        }

        public Builder startTime(String startTime) {
            definition.startTime = startTime;
            return this;
        }

        public Builder endTime(String endTime) {
            definition.endTime = endTime;
            return this;
        }

        public ExtensionDefinition build() {
            if (!definition.isValid()) {
                throw new IllegalArgumentException("ExtensionDefinition参数无效");
            }
            definition.compilePatterns();
            return definition;
        }
    }

    // ==================== Comparable接口实现 ====================

    @Override
    public int compareTo(@NonNull ExtensionDefinition other) {
        // 1. 默认实现排在最后
        if (this.defaultImpl != other.defaultImpl) {
            return Boolean.compare(other.defaultImpl, this.defaultImpl);
        }

        // 2. 匹配分数越高越靠前
        int scoreCompare = Integer.compare(other.getMatchScore(), this.getMatchScore());
        if (scoreCompare != 0) {
            return scoreCompare;
        }

        // 3. 优先级越小越靠前
        int priorityCompare = Integer.compare(this.priority, other.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        // 4. 权重越大越靠前
        int weightCompare = Integer.compare(other.weight, this.weight);
        if (weightCompare != 0) {
            return weightCompare;
        }

        // 5. 按编码排序
        return this.code.compareTo(other.code);
    }

    // ==================== hashCode和equals ====================

    @Override
    public int hashCode() {
        if (cachedHashCode == null) {
            cachedHashCode = Objects.hash(code, extensionPoint, tenant, bizCode,
                    useCase, scenario, env, dimensionRules);
        }
        return cachedHashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExtensionDefinition that = (ExtensionDefinition) o;

        // 快速比较hashCode
        if (this.hashCode() != that.hashCode()) {
            return false;
        }

        // 核心字段比较
        return Objects.equals(code, that.code) &&
                Objects.equals(extensionPoint, that.extensionPoint) &&
                Objects.equals(tenant, that.tenant) &&
                Objects.equals(bizCode, that.bizCode) &&
                Objects.equals(useCase, that.useCase) &&
                Objects.equals(scenario, that.scenario) &&
                Objects.equals(env, that.env) &&
                Objects.equals(dimensionRules, that.dimensionRules);
    }
}