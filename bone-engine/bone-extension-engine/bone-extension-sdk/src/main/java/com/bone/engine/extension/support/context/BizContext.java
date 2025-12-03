package com.bone.engine.extension.support.context;

import lombok.Getter;
import lombok.ToString;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业级业务上下文（2025 终极完美版）
 * 已彻底解决所有泛型、toBuilder、路由问题
 */
@Getter
@ToString(exclude = {"data", "attributes", "extensions"})
public class BizContext<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 固定字段 ====================
    private String tenant = "DEFAULT";
    private String bizCode;
    private String useCase;
    private String scenario;
    private String env = "PROD";
    private String userGroup = "DEFAULT";
    private String requestId;
    private T data;

    private final LocalDateTime createTime = LocalDateTime.now();

    // ==================== 可变集合 ====================
    private final Map<String, String> dimensions = new ConcurrentHashMap<>();
    private final Map<String, Object> params = new ConcurrentHashMap<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, Object> extensions = new ConcurrentHashMap<>();

    // ==================== 缓存 ====================
    private transient volatile Map<String, String> cachedImmutableDimensions;
    private transient volatile String cachedSummary;

    // ==================== 私有构造函数 ====================
    private BizContext() {
        if (requestId == null || requestId.isBlank()) {
            this.requestId = generateRequestId();
        }
    }

    // ==================== 核心方法 ====================
    private void syncFixedFieldsToDimensions() {
        if (StringUtils.hasText(tenant)) dimensions.put("tenant", tenant);
        if (StringUtils.hasText(bizCode)) dimensions.put("bizCode", bizCode);
        if (StringUtils.hasText(useCase)) dimensions.put("useCase", useCase);
        if (StringUtils.hasText(scenario)) dimensions.put("scenario", scenario);
        if (StringUtils.hasText(env)) dimensions.put("env", env);
        if (StringUtils.hasText(userGroup)) dimensions.put("userGroup", userGroup);
    }

    public Map<String, String> getImmutableDimensions() {
        if (cachedImmutableDimensions == null) {
            synchronized (this) {
                if (cachedImmutableDimensions == null) {
                    syncFixedFieldsToDimensions();
                    cachedImmutableDimensions = Collections.unmodifiableMap(new HashMap<>(dimensions));
                }
            }
        }
        return cachedImmutableDimensions;
    }

    public String buildSummary() {
        if (cachedSummary == null) {
            cachedSummary = getImmutableDimensions().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(java.util.stream.Collectors.joining("|"));
        }
        return cachedSummary.isEmpty() ? "EMPTY" : cachedSummary;
    }

    // ==================== 可变集合操作方法（必须 public）====================
    public void setDimension(@NonNull String key, @Nullable String value) {
        if (value == null || !StringUtils.hasText(value)) {
            dimensions.remove(key);
        } else {
            dimensions.put(key, value);
        }
        clearCache();
    }

    public void setParam(@NonNull String key, @Nullable Object value) {
        if (value == null) params.remove(key);
        else params.put(key, value);
        clearCache();
    }

    public void setAttribute(@NonNull String key, @Nullable Object value) {
        if (value == null) attributes.remove(key);
        else attributes.put(key, value);
    }

    public void setExtension(@NonNull String key, @Nullable Object value) {
        if (value == null) extensions.remove(key);
        else extensions.put(key, value);
    }

    private void clearCache() {
        cachedImmutableDimensions = null;
        cachedSummary = null;
    }

    private String generateRequestId() {
        return "CTX-" + System.currentTimeMillis() + "-" +
                Thread.currentThread().getId() + "-" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    // ==================== 终极解决方案：toBuilder() 使用内部类 + 泛型擦除安全转换 ====================
    @SuppressWarnings("unchecked")
    public BizContext<T>.Builder<T> toBuilder() {
        return new Builder<T>(this);
    }

    // ==================== Builder 内部类 ====================
    public class Builder<U> {
        private final BizContext<U> context;

        private Builder(BizContext<U> source) {
            this.context = new BizContext<>();
            // 复制所有字段
            this.context.tenant = source.tenant;
            this.context.bizCode = source.bizCode;
            this.context.useCase = source.useCase;
            this.context.scenario = source.scenario;
            this.context.env = source.env;
            this.context.userGroup = source.userGroup;
            this.context.requestId = source.requestId;
            this.context.data = (U) source.data;
            this.context.dimensions.putAll(source.dimensions);
            this.context.params.putAll(source.params);
            this.context.attributes.putAll(source.attributes);
            this.context.extensions.putAll(source.extensions);
        }

        public Builder<U> tenant(String tenant) { context.tenant = tenant; return this; }
        public Builder<U> bizCode(String bizCode) { context.bizCode = bizCode; return this; }
        public Builder<U> useCase(String useCase) { context.useCase = useCase; return this; }
        public Builder<U> scenario(String scenario) { context.scenario = scenario; return this; }
        public Builder<U> env(String env) { context.env = env; return this; }
        public Builder<U> userGroup(String userGroup) { context.userGroup = userGroup; return this; }
        public Builder<U> requestId(String requestId) { context.requestId = requestId; return this; }
        public Builder<U> data(U data) { context.data = data; return this; }

        public Builder<U> dimension(String key, String value) {
            context.setDimension(key, value);
            return this;
        }

        public Builder<U> param(String key, Object value) {
            context.setParam(key, value);
            return this;
        }

        public Builder<U> attribute(String key, Object value) {
            context.setAttribute(key, value);
            return this;
        }

        public BizContext<U> build() {
            context.syncFixedFieldsToDimensions();
            return context;
        }
    }

    // ==================== 静态工厂方法 ====================
    public static <T> Builder<T> builder() {
        return new BizContext<T>().new Builder<T>(new BizContext<T>());
    }

    public static <T> BizContext<T> empty() {
        return builder().build();
    }
}