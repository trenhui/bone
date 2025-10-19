package com.bone.metadata.sdk.support.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * SQL 配置属性类，映射 metadata.sdk.sql 前缀的配置。
 * 支持多源回退和缓存配置，遵循 Spring Boot 最佳实践。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "metadata.sdk.sql")
public class SqlConfigProperties {

    private static final String DEFAULT_BASE_PATH = "classpath:/sql/";
    private static final String DEFAULT_YAML_PATH = "classpath:/sql-templates/";
    private static final String DEFAULT_LOAD_PRIORITY = "annotation-first";

    /**
     * 是否启用 SQL 功能。
     */
    private boolean enabled = true;

    /**
     * SQL 执行器配置。
     */
    private Executor executor = new Executor();

    /**
     * 数据库配置。
     */
    private Database database = new Database();

    /**
     * SQL 模板加载配置。
     */
    private Template template = new Template();

    /**
     * 缓存配置。
     */
    private Cache cache = new Cache();
    
    public Cache getCache() {
        return cache;
    }

    /**
     * 多租户配置。
     */
    private Tenant tenant = new Tenant();

    /**
     * SQL 监控配置。
     */
    private Monitor monitor = new Monitor();

    /**
     * 安全配置。
     */
    private Security security = new Security();

    @Data
    public static class Executor {
        private Allow allow = new Allow();

        @Data
        public static class Allow {
            /**
             * 是否允许 DML 操作（INSERT、UPDATE、DELETE）。
             */
            private boolean dml = true;

            /**
             * 是否允许批量操作。
             */
            private boolean batch = true;

            /**
             * 最大返回行数。
             */
            @Min(value = 1, message = "Max rows must be positive")
            private int maxRows = 10000;
        }
    }

    @Data
    public static class Database {
        /**
         * 数据库类型（MYSQL、H2、POSTGRESQL、ORACLE、SQLSERVER、SQLITE）。
         */
        @NotBlank(message = "Database type cannot be empty")
        @Pattern(regexp = "MYSQL|H2|POSTGRESQL|ORACLE|SQLSERVER|SQLITE", message = "Invalid database type")
        private String type = "MYSQL";

        /**
         * 是否自动检测数据库方言。
         */
        private boolean autoDetectDialect = true;

        /**
         * 默认数据源名称。
         */
        @NotBlank(message = "Default datasource cannot be empty")
        private String defaultDs = "primary";

        /**
         * 批量操作大小。
         */
        @Min(value = 1, message = "Batch size must be positive")
        private int batchSize = 1000;
    }

    @Data
    public static class Template {
        /**
         * SQL 模板基础路径（如 classpath:/sql/）。
         */
        @NotBlank(message = "SQL template base path cannot be empty")
        private String basePath = DEFAULT_BASE_PATH;

        /**
         * YAML 模板基础路径（如 classpath:/sql-templates/）。
         */
        @NotBlank(message = "YAML template base path cannot be empty")
        private String yamlPath = DEFAULT_YAML_PATH;

        /**
         * 模板加载优先级（annotation-first 或 classpath-first）。
         */
        @NotBlank(message = "Load priority cannot be empty")
        @Pattern(regexp = "annotation-first|classpath-first", message = "Invalid load priority")
        private String loadPriority = DEFAULT_LOAD_PRIORITY;

        /**
         * 是否启用多源回退。
         */
        private boolean fallbackEnabled = true;

        /**
         * 是否启用类路径扫描。
         */
        private boolean enableClasspathScan = true;

        /**
         * 是否启用注解模板。
         */
        private boolean enableAnnotationTemplates = true;

        /**
         * 预加载模板列表。
         */
        @NotNull(message = "Preload templates cannot be null")
        private List<String> preload = new ArrayList<>();

        /**
         * 缓存大小。
         */
        @Min(value = 100, message = "Cache size cannot be less than 100")
        private int cacheSize = 2000;

        /**
         * 最大模板大小（字节）。
         */
        @Min(value = 1024, message = "Maximum template size cannot be less than 1KB")
        private int maxTemplateSize = 1048576;

        /**
         * 缓存过期时间（小时）。
         */
        @Min(value = 1, message = "Cache expiry time cannot be less than 1 hour")
        private int expireHours = 24;

        /**
         * 异步预加载。
         */
        private boolean asyncPreload = true;

        /**
         * 缓存宽限期（Stale-While-Revalidate）。
         */
        @NotNull(message = "Stale-while-revalidate duration cannot be null")
        private Duration staleWhileRevalidate = Duration.ofMinutes(5);
    }

    @Data
    public static class Cache {
        /**
         * 表达式缓存大小。
         */
        @Min(value = 100, message = "Expression cache size must be at least 100")
        private int expressionCacheSize = 10000;

        /**
         * AST 缓存大小。
         */
        @Min(value = 100, message = "AST cache size must be at least 100")
        private int astCacheSize = 2000;
        
        public int getSpelCacheSize() {
            return expressionCacheSize;
        }
        
        public int getAstCacheSize() {
            return astCacheSize;
        }

        /**
         * SpEL 缓存大小。
         */
        @Min(value = 100, message = "SpEL cache size must be at least 100")
        private int spelCacheSize = 1000;

        /**
         * 缓存过期时间（小时）。
         */
        @Min(value = 1, message = "Cache expiry time cannot be less than 1 hour")
        private int expireHours = 24;
        
        public int getExpireHours() {
            return expireHours;
        }
    }

    @Data
    public static class Tenant {
        /**
         * 是否启用多租户支持。
         */
        private boolean enabled = false;

        /**
         * 租户头名称。
         */
        @NotBlank(message = "Tenant header name cannot be empty")
        private String headerName = "X-Tenant-Id";

        /**
         * 租户列名称。
         */
        @NotBlank(message = "Tenant column name cannot be empty")
        private String columnName = "tenant_id";

        /**
         * 排除的表名列表。
         */
        @NotNull(message = "Excluded tables cannot be null")
        private List<String> excludedTables = new ArrayList<>();
    }

    @Data
    public static class Monitor {
        /**
         * 是否启用 SQL 监控。
         */
        private boolean enabled = true;

        /**
         * 监控端点。
         */
        @NotBlank(message = "Monitor endpoint cannot be empty")
        private String endpoint = "/sql-monitor";

        /**
         * 慢查询阈值（毫秒）。
         */
        @Min(value = 100, message = "Slow query threshold must be at least 100ms")
        private long slowQueryThresholdMs = 500;

        /**
         * 是否启用 Prometheus 监控。
         */
        private boolean prometheusEnabled = true;
    }

    @Data
    public static class Security {
        /**
         * 允许访问的表名列表。
         */
        @NotNull(message = "Allowed tables cannot be null")
        private List<String> allowedTables = new ArrayList<>();

        /**
         * 允许的美元符号模式。
         */
        @NotBlank(message = "Allowed dollar pattern cannot be empty")
        private String allowedDollarPattern = "^[a-zA-Z0-9_]+$";

        /**
         * 允许的主机列表。
         */
        @NotNull(message = "Allowed hosts cannot be null")
        private Set<String> allowedHosts = Set.of("*.company.com", "localhost");
    }
    
    public TemplateProperties getTemplate() {
        return new TemplateProperties();
    }
    
    public SecurityProperties getSecurity() {
        return new SecurityProperties();
    }
    
    public TenantProperties getTenant() {
        return new TenantProperties();
    }
    
    public static class SecurityProperties {
        public boolean isEnabled() {
            return true;
        }
        
        public List<String> getAllowedHosts() {
            return Collections.emptyList();
        }
    }
    
    public static class TenantProperties {
        public boolean isEnabled() {
            return true;
        }
    }
    
    public static class TemplateProperties {
        public int getMaxTemplateSize() {
            return 1024 * 1024; // 默认1MB
        }
        
        public int getCacheSize() {
            return 1000;
        }
        
        public int getExpireHours() {
            return 24;
        }
        
        public boolean isFallbackEnabled() {
            return true;
        }
        
        public String getLoadPriority() {
            return "annotation-first";
        }
    }
}