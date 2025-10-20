package org.bone.engine.metadata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP协议接口模型 - 定义互操作性协议接口配置
 * 支持数据摄取、元数据标记、系统互操作等能力
 * 
 * @author Bone Engine Team
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MCPInterface {

    // ================ 核心属性 ================
    
    /**
     * 数据摄取端点
     */
    private String dataIngestion;
    
    /**
     * 元数据标记端点
     */
    private String metadataTagging;
    
    /**
     * 互操作性端点
     */
    private String interoperability;
    
    /**
     * API版本
     */
    private String apiVersion;
    
    /**
     * 协议类型
     */
    private String protocolType;
    
    /**
     * 认证配置
     */
    private AuthConfig authConfig;
    
    /**
     * 数据格式配置
     */
    private DataFormatConfig dataFormat;
    
    /**
     * 端点超时配置
     */
    private Map<String, Integer> endpointTimeouts;
    
    /**
     * 重试策略配置
     */
    private RetryPolicyConfig retryPolicy;
    
    /**
     * 限流配置
     */
    private RateLimitConfig rateLimit;
    
    /**
     * 事件通知配置
     */
    private EventNotificationConfig eventNotification;
    
    /**
     * 健康检查配置
     */
    private HealthCheckConfig healthCheck;
    
    /**
     * 负载均衡配置
     */
    private LoadBalancingConfig loadBalancing;
    
    /**
     * 扩展配置
     */
    private Map<String, Object> extensions;
    
    // ================ 构造方法与辅助方法 ================
    
    public MCPInterface() {
        this.apiVersion = "v1"; 
        this.protocolType = "REST"; 
        this.authConfig = new AuthConfig();
        this.dataFormat = new DataFormatConfig();
        this.endpointTimeouts = new HashMap<>();
        this.retryPolicy = new RetryPolicyConfig();
        this.rateLimit = new RateLimitConfig();
        this.eventNotification = new EventNotificationConfig();
        this.healthCheck = new HealthCheckConfig();
        this.loadBalancing = new LoadBalancingConfig();
        this.extensions = new HashMap<>();
    }
    
    /**
     * 设置端点超时
     */
    public MCPInterface setEndpointTimeout(String endpointName, Integer timeoutMs) {
        if (this.endpointTimeouts == null) {
            this.endpointTimeouts = new HashMap<>();
        }
        this.endpointTimeouts.put(endpointName, timeoutMs);
        return this;
    }
    
    /**
     * 获取端点超时
     */
    public Integer getEndpointTimeout(String endpointName, Integer defaultValue) {
        if (this.endpointTimeouts != null && this.endpointTimeouts.containsKey(endpointName)) {
            return this.endpointTimeouts.get(endpointName);
        }
        return defaultValue;
    }
    
    /**
     * 检查是否启用了数据摄取
     */
    public boolean hasDataIngestionEnabled() {
        return this.dataIngestion != null && !this.dataIngestion.trim().isEmpty();
    }
    
    /**
     * 检查是否启用了元数据标记
     */
    public boolean hasMetadataTaggingEnabled() {
        return this.metadataTagging != null && !this.metadataTagging.trim().isEmpty();
    }
    
    /**
     * 检查是否启用了互操作性
     */
    public boolean hasInteroperabilityEnabled() {
        return this.interoperability != null && !this.interoperability.trim().isEmpty();
    }
    
    // ================ 内部类定义 ================
    
    /**
     * 认证配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthConfig {
        
        /**
         * 认证类型
         */
        private String type;
        
        /**
         * API密钥配置
         */
        private ApiKeyConfig apiKey;
        
        /**
         * OAuth配置
         */
        private OAuthConfig oauth;
        
        /**
         * JWT配置
         */
        private JwtConfig jwt;
        
        /**
         * 基本认证配置
         */
        private BasicAuthConfig basicAuth;
        
        /**
         * 认证头部
         */
        private Map<String, String> headers;
        
        public AuthConfig() {
            this.type = "NONE";
            this.apiKey = new ApiKeyConfig();
            this.oauth = new OAuthConfig();
            this.jwt = new JwtConfig();
            this.basicAuth = new BasicAuthConfig();
            this.headers = new HashMap<>();
        }
    }
    
    /**
     * API密钥配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiKeyConfig {
        
        /**
         * API密钥名称
         */
        private String name;
        
        /**
         * API密钥值
         */
        private String value;
        
        /**
         * 密钥位置
         */
        private String location;
    }
    
    /**
     * OAuth配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OAuthConfig {
        
        /**
         * 授权URL
         */
        private String authUrl;
        
        /**
         * 令牌URL
         */
        private String tokenUrl;
        
        /**
         * 客户端ID
         */
        private String clientId;
        
        /**
         * 客户端密钥
         */
        private String clientSecret;
        
        /**
         * 授权范围
         */
        private String scope;
        
        /**
         * 授权类型
         */
        private String grantType;
    }
    
    /**
     * JWT配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JwtConfig {
        
        /**
         * JWT密钥
         */
        private String secret;
        
        /**
         * JWT颁发者
         */
        private String issuer;
        
        /**
         * JWT过期时间
         */
        private Integer expirationTime;
        
        /**
         * 算法类型
         */
        private String algorithm;
    }
    
    /**
     * 基本认证配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BasicAuthConfig {
        
        /**
         * 用户名
         */
        private String username;
        
        /**
         * 密码
         */
        private String password;
    }
    
    /**
     * 数据格式配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataFormatConfig {
        
        /**
         * 请求格式
         */
        private String requestFormat;
        
        /**
         * 响应格式
         */
        private String responseFormat;
        
        /**
         * 字符编码
         */
        private String charset;
        
        /**
         * 压缩配置
         */
        private CompressionConfig compression;
        
        public DataFormatConfig() {
            this.requestFormat = "JSON";
            this.responseFormat = "JSON";
            this.charset = "UTF-8";
            this.compression = new CompressionConfig();
        }
    }
    
    /**
     * 压缩配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompressionConfig {
        
        /**
         * 是否启用
         */
        private Boolean enabled;
        
        /**
         * 压缩算法
         */
        private String algorithm;
        
        public CompressionConfig() {
            this.enabled = Boolean.FALSE;
            this.algorithm = "GZIP";
        }
    }
    
    /**
     * 重试策略配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RetryPolicyConfig {
        
        /**
         * 最大重试次数
         */
        private Integer maxRetries;
        
        /**
         * 初始延迟（毫秒）
         */
        private Integer initialDelay;
        
        /**
         * 最大延迟（毫秒）
         */
        private Integer maxDelay;
        
        /**
         * 延迟因子
         */
        private Double delayFactor;
        
        /**
         * 重试状态码
         */
        private int[] retryableStatusCodes;
        
        public RetryPolicyConfig() {
            this.maxRetries = 3;
            this.initialDelay = 1000;
            this.maxDelay = 30000;
            this.delayFactor = 2.0;
            this.retryableStatusCodes = new int[]{429, 500, 502, 503, 504};
        }
    }
    
    /**
     * 限流配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RateLimitConfig {
        
        /**
         * 每秒请求数
         */
        private Integer requestsPerSecond;
        
        /**
         * 每分钟请求数
         */
        private Integer requestsPerMinute;
        
        /**
         * 是否启用
         */
        private Boolean enabled;
        
        public RateLimitConfig() {
            this.requestsPerSecond = 100;
            this.requestsPerMinute = 6000;
            this.enabled = Boolean.FALSE;
        }
    }
    
    /**
     * 事件通知配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EventNotificationConfig {
        
        /**
         * 是否启用
         */
        private Boolean enabled;
        
        /**
         * 通知URL
         */
        private String webhookUrl;
        
        /**
         * 通知事件
         */
        private String[] events;
        
        /**
         * 通知格式
         */
        private String format;
        
        public EventNotificationConfig() {
            this.enabled = Boolean.FALSE;
            this.events = new String[0];
            this.format = "JSON";
        }
    }
    
    /**
     * 健康检查配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HealthCheckConfig {
        
        /**
         * 健康检查URL
         */
        private String url;
        
        /**
         * 检查间隔（秒）
         */
        private Integer interval;
        
        /**
         * 超时时间（毫秒）
         */
        private Integer timeout;
        
        /**
         * 是否启用
         */
        private Boolean enabled;
        
        public HealthCheckConfig() {
            this.interval = 30;
            this.timeout = 5000;
            this.enabled = Boolean.TRUE;
        }
    }
    
    /**
     * 负载均衡配置内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoadBalancingConfig {
        
        /**
         * 负载均衡策略
         */
        private String strategy;
        
        /**
         * 服务器地址列表
         */
        private String[] servers;
        
        /**
         * 是否启用
         */
        private Boolean enabled;
        
        public LoadBalancingConfig() {
            this.strategy = "ROUND_ROBIN";
            this.servers = new String[0];
            this.enabled = Boolean.FALSE;
        }
    }
}