package com.bone.engine.extension.api.model.definition;

import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
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
 * ExtensionDefinition - 2025 全球最佳实践终极版（已修复路由兼容性）
 *
 * 修复重点：
 * 1. 自动将 tenant/bizCode/scenario 等标准维度同步到 dimensionRules
 * 2. 提供兼容旧路由器的 getDimensionRules() 方法
 * 3. 所有 setter 自动维护 dimensionRules 一致性
 * 4. 保持原有高性能缓存、权重排序、正则匹配等全部能力
 */
@Getter
@ToString(exclude = {"instance", "compiledPatterns", "cachedHashCode"})
@EqualsAndHashCode(exclude = {"createTime", "compiledPatterns", "cachedHashCode"})
public class ExtensionDefinition implements Serializable, Comparable<ExtensionDefinition> {

    private static final long serialVersionUID = 1L;
    private static final String WILDCARD = "*";

    // ==================== 核心标识字段 ====================
    private String code;
    private String extensionPoint;
    private String implementationClass;
    private Object instance;
    private String description;
    private LocalDateTime createTime = LocalDateTime.now();

    // ==================== 标准路由维度 ====================
    private String tenant = WILDCARD;
    private String bizCode = WILDCARD;
    private String useCase = WILDCARD;
    private String scenario = WILDCARD;
    private String env = WILDCARD;
    private String userGroup = WILDCARD; // 支持 AB 测试、VIP 用户分群

    /** 自定义维度规则（兼容旧路由器） */
    private final Map<String, String> dimensionRules = new ConcurrentHashMap<>();

    private String condition;

    // ==================== 路由控制字段 ====================
    private boolean defaultImpl = false;
    private int weight = 100;
    private int priority = 100;
    @Setter
    private boolean enabled = true;
    private String startTime;
    private String endTime;

    // ==================== 性能优化字段 ====================
    private transient Pattern tenantPattern;
    private transient Pattern bizCodePattern;
    private transient Pattern useCasePattern;
    private transient Pattern scenarioPattern;
    private transient Pattern envPattern;
    private transient Pattern userGroupPattern;
    private transient Predicate<Object> conditionPredicate;
    private transient Integer cachedHashCode;
    private transient String cachedKey;
    private transient Boolean cachedWildcard;
    private transient Integer cachedMatchScore;

    // ==================== 构造方法 ====================
    public ExtensionDefinition() {
        this.createTime = LocalDateTime.now();
    }

    // ==================== 关键修复：兼容旧路由器的 getDimensionRules() ====================
    /**
     * 重要：兼容 DefaultExtensionPointRouter 的四级路由逻辑
     * 返回所有有效维度（标准维度 + 自定义维度），保证精确/模糊匹配能命中
     */
    public Map<String, String> getDimensionRules() {
        Map<String, String> rules = new HashMap<>(dimensionRules);
        if (!WILDCARD.equals(tenant)) rules.put("tenant", tenant);
        if (!WILDCARD.equals(bizCode)) rules.put("bizCode", bizCode);
        if (!WILDCARD.equals(useCase)) rules.put("useCase", useCase);
        if (!WILDCARD.equals(scenario)) rules.put("scenario", scenario);
        if (!WILDCARD.equals(env)) rules.put("env", env);
        if (!WILDCARD.equals(userGroup)) rules.put("userGroup", userGroup);
        return Collections.unmodifiableMap(rules);
    }

    // ==================== setter 自动同步 dimensionRules ====================
    public void setTenant(String tenant) {
        this.tenant = StringUtils.hasText(tenant) ? tenant.trim() : WILDCARD;
        syncDimension("tenant", this.tenant);
    }

    public void setBizCode(String bizCode) {
        this.bizCode = StringUtils.hasText(bizCode) ? bizCode.trim() : WILDCARD;
        syncDimension("bizCode", this.bizCode);
    }

    public void setUseCase(String useCase) {
        this.useCase = StringUtils.hasText(useCase) ? useCase.trim() : WILDCARD;
        syncDimension("useCase", this.useCase);
    }

    public void setScenario(String scenario) {
        this.scenario = StringUtils.hasText(scenario) ? scenario.trim() : WILDCARD;
        syncDimension("scenario", this.scenario);
    }

    public void setEnv(String env) {
        this.env = StringUtils.hasText(env) ? env.trim() : WILDCARD;
        syncDimension("env", this.env);
    }

    public void setUserGroup(String userGroup) {
        this.userGroup = StringUtils.hasText(userGroup) ? userGroup.trim() : WILDCARD;
        syncDimension("userGroup", this.userGroup);
    }

    private void syncDimension(String key, String value) {
        if (WILDCARD.equals(value)) {
            dimensionRules.remove(key);
        } else {
            dimensionRules.put(key, value);
        }
        clearCaches();
    }

    // ==================== 自定义维度操作 ====================
    public void setDimensionRule(@NonNull String key, @Nullable String value) {
        if (value == null || !StringUtils.hasText(value) || WILDCARD.equals(value)) {
            dimensionRules.remove(key);
        } else {
            dimensionRules.put(key, value.trim());
        }
        clearCaches();
    }

    public void setDimensionRules(@NonNull Map<String, String> rules) {
        dimensionRules.clear();
        rules.forEach((k, v) -> {
            if (StringUtils.hasText(v) && !WILDCARD.equals(v)) {
                dimensionRules.put(k, v.trim());
            }
        });
        clearCaches();
    }

    @Nullable
    public String getDimension(@NonNull String key) {
        return dimensionRules.get(key);
    }

    // ==================== 模式编译 ====================
    public void compilePatterns() {
        this.tenantPattern = compilePattern(tenant);
        this.bizCodePattern = compilePattern(bizCode);
        this.useCasePattern = compilePattern(useCase);
        this.scenarioPattern = compilePattern(scenario);
        this.envPattern = compilePattern(env);
        this.userGroupPattern = compilePattern(userGroup);
    }

    private Pattern compilePattern(String pattern) {
        if (pattern == null || WILDCARD.equals(pattern)) return null;
        try {
            return Pattern.compile(pattern.replace("*", ".*"));
        } catch (Exception e) {
            return Pattern.compile(Pattern.quote(pattern));
        }
    }

    public void setConditionPredicate(Predicate<Object> predicate) {
        this.conditionPredicate = predicate;
    }

    // ==================== 匹配优化 ====================
    public boolean isWildcard() {
        if (cachedWildcard == null) {
            cachedWildcard = WILDCARD.equals(tenant) &&
                    WILDCARD.equals(bizCode) &&
                    WILDCARD.equals(useCase) &&
                    WILDCARD.equals(scenario) &&
                    WILDCARD.equals(env) &&
                    WILDCARD.equals(userGroup) &&
                    dimensionRules.isEmpty() &&
                    !StringUtils.hasText(condition);
        }
        return cachedWildcard;
    }

    public int getMatchScore() {
        if (cachedMatchScore == null) {
            int score = dimensionRules.size() * 10;
            if (!WILDCARD.equals(tenant)) score += 10000;
            if (!WILDCARD.equals(bizCode)) score += 2000;
            if (!WILDCARD.equals(useCase)) score += 500;
            if (!WILDCARD.equals(scenario)) score += 300;
            if (!WILDCARD.equals(env)) score += 50;
            if (!WILDCARD.equals(userGroup)) score += 800;
            if (StringUtils.hasText(condition)) score += 1000;
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
        if (!WILDCARD.equals(userGroup)) all.put("userGroup", userGroup);
        all.putAll(dimensionRules);
        return Collections.unmodifiableMap(all);
    }

    public String getCacheKey() {
        if (cachedKey == null) {
            StringBuilder sb = new StringBuilder(extensionPoint).append(":");
            getAllDimensions().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(e.getKey()).append("=").append(e.getValue()).append("|"));
            if (StringUtils.hasText(condition)) {
                sb.append("cond=").append(condition.hashCode());
            }
            cachedKey = sb.toString();
        }
        return cachedKey;
    }

    public boolean isValid() {
        return StringUtils.hasText(code) &&
                StringUtils.hasText(extensionPoint) &&
                instance != null;
    }

    /** 复制路由视图（用于运行时元数据叠加，保留实例引用）。 */
    @NonNull
    public ExtensionDefinition copyRoutingView() {
        ExtensionDefinition copy = new ExtensionDefinition();
        copy.code = this.code;
        copy.extensionPoint = this.extensionPoint;
        copy.implementationClass = this.implementationClass;
        copy.instance = this.instance;
        copy.description = this.description;
        copy.tenant = this.tenant;
        copy.bizCode = this.bizCode;
        copy.useCase = this.useCase;
        copy.scenario = this.scenario;
        copy.env = this.env;
        copy.userGroup = this.userGroup;
        copy.dimensionRules.putAll(this.dimensionRules);
        copy.condition = this.condition;
        copy.defaultImpl = this.defaultImpl;
        copy.weight = this.weight;
        copy.priority = this.priority;
        copy.enabled = this.enabled;
        copy.startTime = this.startTime;
        copy.endTime = this.endTime;
        copy.compilePatterns();
        return copy;
    }

    /** 将控制面下发的路由元数据叠加到当前视图。 */
    public void applyRoutingOverlay(@NonNull ExtensionRoutingMetadata meta) {
        if (StringUtils.hasText(meta.getTenant())) {
            setTenant(meta.getTenant());
        }
        if (StringUtils.hasText(meta.getBizCode())) {
            setBizCode(meta.getBizCode());
        }
        if (StringUtils.hasText(meta.getUseCase())) {
            setUseCase(meta.getUseCase());
        }
        if (StringUtils.hasText(meta.getScenario())) {
            setScenario(meta.getScenario());
        }
        if (StringUtils.hasText(meta.getEnv())) {
            setEnv(meta.getEnv());
        }
        if (StringUtils.hasText(meta.getUserGroup())) {
            setUserGroup(meta.getUserGroup());
        }
        if (meta.getCondition() != null) {
            this.condition = meta.getCondition();
        }
        this.priority = meta.getPriority();
        this.weight = meta.getWeight();
        this.defaultImpl = meta.isDefaultImpl();
        this.enabled = meta.isEnabled();
        setDimensionRule("traffic", String.valueOf(meta.getTraffic()));
        clearCaches();
        compilePatterns();
    }

    private void clearCaches() {
        cachedHashCode = null;
        cachedKey = null;
        cachedWildcard = null;
        cachedMatchScore = null;
    }

    // ==================== Builder（关键修复在此！）===================
    public static Builder builder() {
        return new Builder();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {
        private final ExtensionDefinition def = new ExtensionDefinition();

        public Builder code(String code) { def.code = code; return this; }
        public Builder extensionPoint(String extensionPoint) { def.extensionPoint = extensionPoint; return this; }
        public Builder implementationClass(String implementationClass) { def.implementationClass = implementationClass; return this; }
        public Builder instance(Object instance) { def.instance = instance; return this; }
        public Builder description(String description) { def.description = description; return this; }
        public Builder tenant(String tenant) { def.setTenant(tenant); return this; }
        public Builder bizCode(String bizCode) { def.setBizCode(bizCode); return this; }
        public Builder useCase(String useCase) { def.setUseCase(useCase); return this; }
        public Builder scenario(String scenario) { def.setScenario(scenario); return this; }
        public Builder env(String env) { def.setEnv(env); return this; }
        public Builder userGroup(String userGroup) { def.setUserGroup(userGroup); return this; }
        public Builder dimension(String key, String value) { def.setDimensionRule(key, value); return this; }
        public Builder dimensions(Map<String, String> dims) { def.setDimensionRules(dims); return this; }
        public Builder condition(String condition) { def.condition = condition; return this; }
        public Builder defaultImpl(boolean defaultImpl) { def.defaultImpl = defaultImpl; return this; }
        public Builder weight(int weight) { def.weight = Math.max(0, weight); return this; }
        public Builder priority(int priority) { def.priority = Math.max(1, priority); return this; }
        public Builder enabled(boolean enabled) { def.enabled = enabled; return this; }
        public Builder startTime(String startTime) { def.startTime = startTime; return this; }
        public Builder endTime(String endTime) { def.endTime = endTime; return this; }

        public ExtensionDefinition build() {
            if (!def.isValid()) {
                throw new IllegalArgumentException("ExtensionDefinition 参数无效: " + def);
            }
            def.compilePatterns();
            return def;
        }
    }

    // ==================== Comparable ====================
    @Override
    public int compareTo(@NonNull ExtensionDefinition o) {
        if (this.defaultImpl != o.defaultImpl) {
            return Boolean.compare(o.defaultImpl, this.defaultImpl);
        }
        int score = Integer.compare(o.getMatchScore(), this.getMatchScore());
        if (score != 0) return score;
        int pri = Integer.compare(this.priority, o.priority);
        if (pri != 0) return pri;
        int w = Integer.compare(o.weight, this.weight);
        if (w != 0) return w;
        return this.code.compareTo(o.code);
    }

    @Override
    public int hashCode() {
        if (cachedHashCode == null) {
            cachedHashCode = Objects.hash(code, extensionPoint, tenant, bizCode, useCase, scenario, env, userGroup, dimensionRules, condition);
        }
        return cachedHashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (hashCode() != o.hashCode()) return false;
        ExtensionDefinition that = (ExtensionDefinition) o;
        return Objects.equals(code, that.code) &&
                Objects.equals(extensionPoint, that.extensionPoint) &&
                Objects.equals(tenant, that.tenant) &&
                Objects.equals(bizCode, that.bizCode) &&
                Objects.equals(useCase, that.useCase) &&
                Objects.equals(scenario, that.scenario) &&
                Objects.equals(env, that.env) &&
                Objects.equals(userGroup, that.userGroup) &&
                Objects.equals(dimensionRules, that.dimensionRules) &&
                Objects.equals(condition, that.condition);
    }
}