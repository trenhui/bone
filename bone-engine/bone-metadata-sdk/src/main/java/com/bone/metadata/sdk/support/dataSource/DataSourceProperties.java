package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * DataSourceProperties - Comprehensive data source configuration properties class.
 * <p>
 * Provides functionality for advanced data source management including:
 * <ul>
 *   <li>Master-slave separation configuration</li>
 *   <li>Connection pool management</li>
 *   <li>Health checking mechanisms</li>
 *   <li>Dynamic data source switching</li>
 *   <li>Routing strategy configuration</li>
 * </ul>
 * <p>
 * This class is designed with flexibility in mind while maintaining a clear separation of concerns
 * between different configuration aspects. It provides validation capabilities to ensure
 * configuration consistency and correctness.
 */
@ConfigurationProperties(prefix = "bone.metadata.datasource")
public class DataSourceProperties {

    private static final Logger log = LoggerFactory.getLogger(DataSourceProperties.class);

    /**
     * Whether multi-data source functionality is enabled.
     *
     * @see #isEnabled()
     * @see #setEnabled(boolean)
     */
    private boolean enabled = true;

    /**
     * Name of the default data source.
     *
     * @see #getDefaultDataSource()
     * @see #setDefaultDataSource(String)
     */
    private String defaultDataSource = "master";

    /**
     * Whether strict mode is enabled. When strict mode is on, operations will throw exceptions
     * if data sources are unavailable.
     *
     * @see #isStrict()
     * @see #setStrict(boolean)
     */
    private boolean strict = false;

    /**
     * Health check interval in milliseconds.
     *
     * @see #getHealthCheckIntervalMs()
     * @see #setHealthCheckIntervalMs(long)
     * @see #getHealthCheckInterval(TimeUnit)
     */
    private long healthCheckIntervalMs = 30000;

    /**
     * Connection timeout in milliseconds.
     *
     * @see #getConnectionTimeoutMs()
     * @see #setConnectionTimeoutMs(int)
     * @see #getConnectionTimeout(TimeUnit)
     */
    private int connectionTimeoutMs = 5000;

    /**
     * Number of health check retry attempts.
     *
     * @see #getHealthCheckRetryCount()
     * @see #setHealthCheckRetryCount(int)
     */
    private int healthCheckRetryCount = 2;

    /**
     * Maximum number of data sources that can be registered.
     *
     * @see #getMaxDataSourceCount()
     * @see #setMaxDataSourceCount(int)
     */
    private int maxDataSourceCount = 50;

    /**
     * Mapping of data source configurations, where keys are data source names and values are
     * the corresponding configuration objects.
     *
     * @see #getDataSources()
     * @see #setDataSources(Map)
     * @see #getDataSourceConfig(String)
     * @see #hasDataSource(String)
     * @see #getDataSourceCount()
     */
    private Map<String, DataSourceConfig> dataSources;

    /**
     * Read-write separation configuration settings.
     *
     * @see #getReadWriteConfig()
     * @see #setReadWriteConfig(ReadWriteConfig)
     */
    private ReadWriteConfig readWriteConfig = new ReadWriteConfig();

    /**
     * Connection pool configuration settings.
     *
     * @see #getPool()
     * @see #setPool(PoolConfig)
     */
    private PoolConfig pool = new PoolConfig();

    /**
     * Data source routing strategy to determine how data source selection is performed.
     *
     * @see #getRoutingStrategy()
     * @see #setRoutingStrategy(RoutingStrategy)
     */
    private RoutingStrategy routingStrategy = RoutingStrategy.ANNOTATION;

    /**
     * Gets whether multi-data source functionality is enabled.
     *
     * @return true if multi-data source is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 缓存已创建的数据源实例，避免重复创建
     */
    private final Map<String, javax.sql.DataSource> dataSourceCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 从缓存获取或创建数据源
     * @param name 数据源名称
     * @return 数据源实例
     */
    public javax.sql.DataSource getDataSource(String name) {
        DataSourceConfig config = getDataSources().get(name);
        if (config == null) {
            throw new RuntimeException("DataSource not found: " + name);
        }
        return dataSourceCache.computeIfAbsent(name, key -> buildDataSource(config));
    }

    /**
     * 从缓存获取或创建数据源
     * @param name 数据源名称
     * @param config 数据源配置
     * @return 数据源实例
     */
    public javax.sql.DataSource getDataSource(String name, DataSourceConfig config) {
        return dataSourceCache.computeIfAbsent(name, key -> buildDataSource(config));
    }

    /**
     * 构建数据源实例
     * @param config 数据源配置
     * @return 数据源实例，如果配置不完整则返回null
     */
    private javax.sql.DataSource buildDataSource(DataSourceConfig config) {
        if (config == null) {
            log.warn("Invalid datasource configuration, config is null");
            return null;
        }

        // 检查必要的属性
        if (!StringUtils.hasText(config.getUrl())) {
            log.warn("Invalid datasource configuration, url is required");
            return null;
        }

        try {
            // 使用Spring Boot的DataSourceBuilder创建数据源
            DataSourceBuilder<?> builder = DataSourceBuilder.create();

            // 设置基础属性
            builder.url(config.getUrl());

            if (StringUtils.hasText(config.getUsername())) {
                builder.username(config.getUsername());
            }

            if (StringUtils.hasText(config.getPassword())) {
                builder.password(config.getPassword());
            }

            if (StringUtils.hasText(config.getDriverClassName())) {
                builder.driverClassName(config.getDriverClassName());
            }

            // 设置类型（如果指定）
            // 检查并设置数据源类型（如果有）
            if (config.getType() != null) {
                String typeName = config.getType().toString();
                try {
                    Class<? extends javax.sql.DataSource> type = (Class<? extends javax.sql.DataSource>) Class.forName(typeName);
                    builder.type(type);
                } catch (ClassNotFoundException e) {
                    log.warn("Could not load data source type: {}", typeName, e);
                }
            }

            // 构建数据源
            javax.sql.DataSource dataSource = builder.build();

            // 如果有额外属性，可以在这里设置
            // 由于DataSourceBuilder没有直接的方法来设置额外属性，我们需要根据具体的数据源类型进行类型转换和设置

            return dataSource;
        } catch (Exception e) {
            log.error("Failed to build datasource: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build datasource: " + e.getMessage(), e);
        }
    }

    /**
     * 获取数据源缓存
     * @return 数据源缓存映射
     */
    public Map<String, javax.sql.DataSource> getDataSourceCache() {
        return dataSourceCache;
    }

    /**
     * Sets whether multi-data source functionality is enabled.
     *
     * @param enabled true to enable multi-data source, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the name of the default data source.
     *
     * @return the default data source name
     */
    public String getDefaultDataSource() {
        return defaultDataSource;
    }

    /**
     * Sets the name of the default data source.
     *
     * @param defaultDataSource the default data source name
     */
    public void setDefaultDataSource(String defaultDataSource) {
        this.defaultDataSource = defaultDataSource;
    }

    /**
     * Gets whether strict mode is enabled.
     *
     * @return true if strict mode is enabled, false otherwise
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Sets whether strict mode is enabled.
     *
     * @param strict true to enable strict mode, false to disable
     */
    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    /**
     * Gets the health check interval in milliseconds.
     *
     * @return the health check interval in milliseconds
     */
    public long getHealthCheckIntervalMs() {
        return healthCheckIntervalMs;
    }

    /**
     * Sets the health check interval in milliseconds.
     *
     * @param healthCheckIntervalMs the health check interval in milliseconds
     */
    public void setHealthCheckIntervalMs(long healthCheckIntervalMs) {
        this.healthCheckIntervalMs = healthCheckIntervalMs;
    }

    /**
     * Gets the health check interval in the specified time unit.
     *
     * @param timeUnit the time unit to convert to
     * @return the health check interval in the specified time unit
     */
    public long getHealthCheckInterval(TimeUnit timeUnit) {
        Assert.notNull(timeUnit, "TimeUnit must not be null");
        return timeUnit.convert(healthCheckIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Gets the connection timeout in milliseconds.
     *
     * @return the connection timeout in milliseconds
     */
    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Sets the connection timeout in milliseconds.
     *
     * @param connectionTimeoutMs the connection timeout in milliseconds
     */
    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    /**
     * Gets the connection timeout in the specified time unit.
     *
     * @param timeUnit the time unit to convert to
     * @return the connection timeout in the specified time unit
     */
    public long getConnectionTimeout(TimeUnit timeUnit) {
        Assert.notNull(timeUnit, "TimeUnit must not be null");
        return timeUnit.convert(connectionTimeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Gets the number of health check retry attempts.
     *
     * @return the number of retry attempts
     */
    public int getHealthCheckRetryCount() {
        return healthCheckRetryCount;
    }

    /**
     * Sets the number of health check retry attempts.
     *
     * @param healthCheckRetryCount the number of retry attempts
     */
    public void setHealthCheckRetryCount(int healthCheckRetryCount) {
        this.healthCheckRetryCount = healthCheckRetryCount;
    }

    /**
     * Gets the maximum number of data sources that can be registered.
     *
     * @return the maximum number of data sources
     */
    public int getMaxDataSourceCount() {
        return maxDataSourceCount;
    }

    /**
     * Sets the maximum number of data sources that can be registered.
     *
     * @param maxDataSourceCount the maximum number of data sources
     */
    public void setMaxDataSourceCount(int maxDataSourceCount) {
        this.maxDataSourceCount = maxDataSourceCount;
    }

    /**
     * Validates the configuration properties.
     * This method checks for common configuration errors and consistency issues,
     * and also validates all nested configuration objects.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        validateBasicProperties();

        // Validate nested configurations
        if (readWriteConfig != null) {
            readWriteConfig.validate();
        }

        if (pool != null) {
            pool.validate();
        }

        // Validate individual data source configurations
        validateDataSources();
    }

    /**
     * Validates the basic properties of this configuration.
     *
     * @throws IllegalArgumentException if validation fails
     */
    private void validateBasicProperties() {
        if (defaultDataSource == null || defaultDataSource.trim().isEmpty()) {
            throw new IllegalArgumentException("Default data source name must not be empty");
        }

        if (maxDataSourceCount <= 0) {
            throw new IllegalArgumentException("Maximum data source count must be greater than 0");
        }

        if (healthCheckRetryCount < 0) {
            throw new IllegalArgumentException("Health check retry count must be non-negative");
        }

        if (healthCheckIntervalMs <= 0) {
            throw new IllegalArgumentException("Health check interval must be greater than 0");
        }

        if (connectionTimeoutMs <= 0) {
            throw new IllegalArgumentException("Connection timeout must be greater than 0");
        }
    }

    /**
     * Validates all individual data source configurations.
     *
     * @throws IllegalArgumentException if validation fails
     */
    private void validateDataSources() {
        if (dataSources != null) {
            // Check if the number of data sources exceeds the maximum allowed
            if (dataSources.size() > maxDataSourceCount) {
                throw new IllegalArgumentException(
                    String.format("Number of data sources (%d) exceeds maximum allowed (%d)",
                        dataSources.size(), maxDataSourceCount));
            }

            // Validate each data source configuration
            for (Map.Entry<String, DataSourceConfig> entry : dataSources.entrySet()) {
                String dataSourceName = entry.getKey();
                DataSourceConfig config = entry.getValue();

                if (config == null) {
                    throw new IllegalArgumentException(
                        String.format("Configuration for data source '%s' cannot be null", dataSourceName));
                }

                try {
                    config.validate();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        String.format("Invalid configuration for data source '%s': %s",
                            dataSourceName, e.getMessage()), e);
                }
            }
        }
    }

    /**
     * Checks if the configuration has a data source with the specified name.
     *
     * @param dataSourceName the name of the data source to check
     * @return true if a data source with the specified name exists, false otherwise
     */
    public boolean hasDataSource(String dataSourceName) {
        return dataSources != null && dataSources.containsKey(dataSourceName);
    }

    /**
     * Gets the configuration for a specific data source.
     *
     * @param dataSourceName the name of the data source
     * @return the data source configuration, or null if not found
     */
    public DataSourceConfig getDataSourceConfig(String dataSourceName) {
        return dataSources != null ? dataSources.get(dataSourceName) : null;
    }

    /**
     * Gets the number of configured data sources.
     *
     * @return the number of data sources
     */
    public int getDataSourceCount() {
        return dataSources != null ? dataSources.size() : 0;
    }

    /**
     * Gets the data source configurations mapping.
     *
     * @return mapping of data source names to their configurations
     */
    public Map<String, DataSourceConfig> getDataSources() {
        return dataSources;
    }

    /**
     * Sets the data source configurations mapping.
     *
     * @param dataSources mapping of data source names to their configurations
     */
    public void setDataSources(Map<String, DataSourceConfig> dataSources) {
        this.dataSources = dataSources;
    }

    /**
     * Gets the read-write separation configuration.
     *
     * @return the read-write configuration
     */
    public ReadWriteConfig getReadWriteConfig() {
        return readWriteConfig;
    }

    /**
     * Sets the read-write separation configuration.
     *
     * @param readWriteConfig the read-write configuration
     */
    public void setReadWriteConfig(ReadWriteConfig readWriteConfig) {
        this.readWriteConfig = readWriteConfig;
    }

    /**
     * Gets the connection pool configuration.
     *
     * @return the connection pool configuration
     */
    public PoolConfig getPool() {
        return pool;
    }

    /**
     * Sets the connection pool configuration.
     *
     * @param pool the connection pool configuration
     */
    public void setPool(PoolConfig pool) {
        this.pool = pool;
    }

    /**
     * Gets the data source routing strategy.
     *
     * @return the routing strategy
     */
    public RoutingStrategy getRoutingStrategy() {
        return routingStrategy;
    }

    /**
     * Sets the data source routing strategy.
     *
     * @param routingStrategy the routing strategy
     */
    public void setRoutingStrategy(RoutingStrategy routingStrategy) {
        this.routingStrategy = routingStrategy;
    }

    /**
     * Individual data source configuration class.
     * Contains settings for a specific data source including connection details and type.
     * This class provides configuration for a single database connection with appropriate
     * validation to ensure required properties are set correctly.
     */
    public static class DataSourceConfig {
        /**
         * JDBC connection URL for the data source.
         *
         * @see #getUrl()
         * @see #setUrl(String)
         */
        private String url;

        /**
         * Username for authenticating with the data source.
         *
         * @see #getUsername()
         * @see #setUsername(String)
         */
        private String username;

        /**
         * Password for authenticating with the data source.
         *
         * @see #getPassword()
         * @see #setPassword(String)
         */
        private String password;

        /**
         * Fully qualified class name of the JDBC driver.
         *
         * @see #getDriverClassName()
         * @see #setDriverClassName(String)
         */
        private String driverClassName;

        /**
         * Type of the data source (master or slave).
         *
         * @see #getType()
         * @see #setType(DataSourceType)
         */
        private DataSourceType type = DataSourceType.MASTER;

        /**
         * Additional data source properties for customization.
         *
         * @see #getProperties()
         * @see #setProperties(Map)
         */
        private Map<String, String> properties;

        /**
         * Gets the JDBC connection URL.
         *
         * @return the JDBC URL
         */
        public String getUrl() {
            return url;
        }

        /**
         * Sets the JDBC connection URL.
         *
         * @param url the JDBC URL
         */
        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * Gets the username for authentication.
         *
         * @return the username
         */
        public String getUsername() {
            return username;
        }

        /**
         * Sets the username for authentication.
         *
         * @param username the username
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * Gets the password for authentication.
         *
         * @return the password
         */
        public String getPassword() {
            return password;
        }

        /**
         * Sets the password for authentication.
         *
         * @param password the password
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * Gets the JDBC driver class name.
         *
         * @return the driver class name
         */
        public String getDriverClassName() {
            return driverClassName;
        }

        /**
         * Sets the JDBC driver class name.
         *
         * @param driverClassName the driver class name
         */
        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        /**
         * Gets the data source type.
         *
         * @return the data source type
         */
        public DataSourceType getType() {
            return type;
        }

        /**
         * Sets the data source type.
         *
         * @param type the data source type
         */
        public void setType(DataSourceType type) {
            this.type = type;
        }

        /**
         * Gets additional data source properties.
         *
         * @return the properties map
         */
        public Map<String, String> getProperties() {
            return properties;
        }

        /**
         * Sets additional data source properties.
         *
         * @param properties the properties map
         */
        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        /**
         * Validates this data source configuration.
         *
         * @throws IllegalArgumentException if validation fails
         */
        public void validate() {
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("Data source URL must not be empty");
            }
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("Data source username must not be empty");
            }
            if (driverClassName == null || driverClassName.trim().isEmpty()) {
                throw new IllegalArgumentException("Data source driver class name must not be empty");
            }
            if (type == null) {
                throw new IllegalArgumentException("Data source type must be specified");
            }
        }
    }

    /**
     * Read-write separation configuration class.
     * Provides settings for implementing read-write separation patterns across multiple data sources.
     * This class configures how read and write operations are directed to appropriate data sources.
     */
    public static class ReadWriteConfig {
        /**
         * Whether read-write separation is enabled.
         *
         * @see #isEnabled()
         * @see #setEnabled(boolean)
         */
        private boolean enabled = false;

        /**
         * Name of the master data source used for write operations.
         *
         * @see #getMasterDataSourceName()
         * @see #setMasterDataSourceName(String)
         * @see #hasMasterDataSource()
         */
        private String masterDataSourceName = "master";

        /**
         * Names of slave data sources used for read operations.
         *
         * @see #getSlaveDataSourceNames()
         * @see #setSlaveDataSourceNames(String[])
         * @see #hasSlaveDataSources()
         * @see #getSlaveDataSourceNamesList()
         * @see #getSlaveDataSourceCount()
         */
        private String[] slaveDataSourceNames = new String[0];

        /**
         * Load balancing strategy for distributing read operations among slave data sources.
         *
         * @see #getSlaveLoadBalancingStrategy()
         * @see #setSlaveLoadBalancingStrategy(LoadBalancingStrategy)
         */
        private LoadBalancingStrategy slaveLoadBalancingStrategy = LoadBalancingStrategy.RANDOM;

        /**
         * Gets whether read-write separation is enabled.
         *
         * @return true if read-write separation is enabled, false otherwise
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether read-write separation is enabled.
         *
         * @param enabled true to enable read-write separation, false to disable
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Gets the name of the master data source.
         *
         * @return the master data source name
         */
        public String getMasterDataSourceName() {
            return masterDataSourceName;
        }

        /**
         * Sets the name of the master data source.
         *
         * @param masterDataSourceName the master data source name
         */
        public void setMasterDataSourceName(String masterDataSourceName) {
            this.masterDataSourceName = masterDataSourceName;
        }

        /**
         * Gets the names of slave data sources.
         *
         * @return array of slave data source names
         */
        public String[] getSlaveDataSourceNames() {
            return slaveDataSourceNames;
        }

        /**
         * Sets the names of slave data sources.
         *
         * @param slaveDataSourceNames array of slave data source names
         */
        public void setSlaveDataSourceNames(String[] slaveDataSourceNames) {
            this.slaveDataSourceNames = slaveDataSourceNames != null ? slaveDataSourceNames : new String[0];
        }

        /**
         * Gets the load balancing strategy for slave data sources.
         *
         * @return the load balancing strategy
         */
        public LoadBalancingStrategy getSlaveLoadBalancingStrategy() {
            return slaveLoadBalancingStrategy;
        }

        /**
         * Sets the load balancing strategy for slave data sources.
         *
         * @param slaveLoadBalancingStrategy the load balancing strategy
         */
        public void setSlaveLoadBalancingStrategy(LoadBalancingStrategy slaveLoadBalancingStrategy) {
            this.slaveLoadBalancingStrategy = slaveLoadBalancingStrategy;
        }

        /**
         * Checks if there are any slave data sources configured.
         *
         * @return true if there are slave data sources, false otherwise
         */
        public boolean hasSlaveDataSources() {
            return slaveDataSourceNames != null && slaveDataSourceNames.length > 0;
        }

        /**
         * Checks if a master data source is configured.
         *
         * @return true if a master data source is configured, false otherwise
         */
        public boolean hasMasterDataSource() {
            return masterDataSourceName != null && !masterDataSourceName.trim().isEmpty();
        }

        /**
         * Gets the names of slave data sources as a list.
         *
         * @return list of slave data source names
         */
        public List<String> getSlaveDataSourceNamesList() {
            return slaveDataSourceNames != null ?
                   Arrays.asList(slaveDataSourceNames) :
                   Collections.emptyList();
        }

        /**
         * Gets the count of slave data sources.
         *
         * @return the number of slave data sources
         */
        public int getSlaveDataSourceCount() {
            return slaveDataSourceNames != null ? slaveDataSourceNames.length : 0;
        }

        /**
         * Validates this read-write configuration.
         *
         * @throws IllegalArgumentException if validation fails
         */
        public void validate() {
            if (enabled) {
                if (masterDataSourceName == null || masterDataSourceName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Master data source name must be configured when read-write separation is enabled");
                }
                if (slaveLoadBalancingStrategy == null) {
                    throw new IllegalArgumentException("Load balancing strategy must be configured when read-write separation is enabled");
                }
                if (slaveDataSourceNames != null &&
                    Arrays.stream(slaveDataSourceNames).anyMatch(name -> name == null || name.trim().isEmpty())) {
                    throw new IllegalArgumentException("Slave data source names cannot be null or empty");
                }
            }
        }
    }

    /**
     * Connection pool configuration class.
     * Provides settings for managing database connection pools including sizing,
     * validation, and idle connection handling.
     * This class configures how database connections are pooled, validated, and managed.
     */
    public static class PoolConfig {
        /**
         * Minimum number of idle connections to maintain in the pool.
         *
         * @see #getMinIdle()
         * @see #setMinIdle(int)
         */
        private int minIdle = 5;

        /**
         * Maximum number of active connections that can be allocated from the pool at the same time.
         *
         * @see #getMaxActive()
         * @see #setMaxActive(int)
         */
        private int maxActive = 20;

        /**
         * Maximum time in milliseconds that a caller will wait for a connection from the pool.
         *
         * @see #getMaxWait()
         * @see #setMaxWait(long)
         * @see #getMaxWait(TimeUnit)
         */
        private long maxWait = 30000;

        /**
         * Time between eviction runs in milliseconds.
         *
         * @see #getTimeBetweenEvictionRuns()
         * @see #setTimeBetweenEvictionRuns(long)
         * @see #getTimeBetweenEvictionRuns(TimeUnit)
         */
        private long timeBetweenEvictionRuns = 60000;

        /**
         * Minimum amount of time a connection may sit idle in the pool before it is eligible for eviction.
         *
         * @see #getMinEvictableIdleTime()
         * @see #setMinEvictableIdleTime(long)
         * @see #getMinEvictableIdleTime(TimeUnit)
         */
        private long minEvictableIdleTime = 300000;

        /**
         * Whether to validate connections while they are idle in the pool.
         *
         * @see #isTestWhileIdle()
         * @see #setTestWhileIdle(boolean)
         */
        private boolean testWhileIdle = true;

        /**
         * Whether to validate connections before borrowing them from the pool.
         *
         * @see #isTestOnBorrow()
         * @see #setTestOnBorrow(boolean)
         */
        private boolean testOnBorrow = false;

        /**
         * Whether to validate connections before returning them to the pool.
         *
         * @see #isTestOnReturn()
         * @see #setTestOnReturn(boolean)
         */
        private boolean testOnReturn = false;

        /**
         * SQL query used to validate connections.
         *
         * @see #getValidationQuery()
         * @see #setValidationQuery(String)
         */
        private String validationQuery = "SELECT 1";

        /**
         * Gets the minimum number of idle connections.
         *
         * @return the minimum idle connections
         */
        public int getMinIdle() {
            return minIdle;
        }

        /**
         * Sets the minimum number of idle connections.
         *
         * @param minIdle the minimum idle connections
         */
        public void setMinIdle(int minIdle) {
            this.minIdle = minIdle;
        }

        /**
         * Gets the maximum number of active connections.
         *
         * @return the maximum active connections
         */
        public int getMaxActive() {
            return maxActive;
        }

        /**
         * Sets the maximum number of active connections.
         *
         * @param maxActive the maximum active connections
         */
        public void setMaxActive(int maxActive) {
            this.maxActive = maxActive;
        }

        /**
         * Gets the maximum wait time in milliseconds.
         *
         * @return the maximum wait time in milliseconds
         */
        public long getMaxWait() {
            return maxWait;
        }

        /**
         * Sets the maximum wait time in milliseconds.
         *
         * @param maxWait the maximum wait time in milliseconds
         */
        public void setMaxWait(long maxWait) {
            this.maxWait = maxWait;
        }

        /**
         * Gets the maximum wait time in the specified time unit.
         *
         * @param timeUnit the time unit to convert to
         * @return the maximum wait time in the specified time unit
         */
        public long getMaxWait(TimeUnit timeUnit) {
            Assert.notNull(timeUnit, "TimeUnit must not be null");
            return timeUnit.convert(maxWait, TimeUnit.MILLISECONDS);
        }

        /**
         * Gets the time between eviction runs in milliseconds.
         *
         * @return the time between eviction runs in milliseconds
         */
        public long getTimeBetweenEvictionRuns() {
            return timeBetweenEvictionRuns;
        }

        /**
         * Gets the time between eviction runs in the specified time unit.
         *
         * @param timeUnit the time unit to convert to
         * @return the time between eviction runs in the specified time unit
         */
        public long getTimeBetweenEvictionRuns(TimeUnit timeUnit) {
            Assert.notNull(timeUnit, "TimeUnit must not be null");
            return timeUnit.convert(timeBetweenEvictionRuns, TimeUnit.MILLISECONDS);
        }

        /**
         * Sets the time between eviction runs in milliseconds.
         *
         * @param timeBetweenEvictionRuns the time between eviction runs in milliseconds
         */
        public void setTimeBetweenEvictionRuns(long timeBetweenEvictionRuns) {
            this.timeBetweenEvictionRuns = timeBetweenEvictionRuns;
        }

        /**
         * Gets the minimum evictable idle time in milliseconds.
         *
         * @return the minimum evictable idle time in milliseconds
         */
        public long getMinEvictableIdleTime() {
            return minEvictableIdleTime;
        }

        /**
         * Gets the minimum evictable idle time in the specified time unit.
         *
         * @param timeUnit the time unit to convert to
         * @return the minimum evictable idle time in the specified time unit
         */
        public long getMinEvictableIdleTime(TimeUnit timeUnit) {
            Assert.notNull(timeUnit, "TimeUnit must not be null");
            return timeUnit.convert(minEvictableIdleTime, TimeUnit.MILLISECONDS);
        }

        /**
         * Sets the minimum evictable idle time in milliseconds.
         *
         * @param minEvictableIdleTime the minimum evictable idle time in milliseconds
         */
        public void setMinEvictableIdleTime(long minEvictableIdleTime) {
            this.minEvictableIdleTime = minEvictableIdleTime;
        }

        /**
         * Gets whether connections are validated while idle.
         *
         * @return true if idle connections are validated, false otherwise
         */
        public boolean isTestWhileIdle() {
            return testWhileIdle;
        }

        /**
         * Sets whether connections are validated while idle.
         *
         * @param testWhileIdle true to validate idle connections, false otherwise
         */
        public void setTestWhileIdle(boolean testWhileIdle) {
            this.testWhileIdle = testWhileIdle;
        }

        /**
         * Gets whether connections are validated on borrow.
         *
         * @return true if connections are validated on borrow, false otherwise
         */
        public boolean isTestOnBorrow() {
            return testOnBorrow;
        }

        /**
         * Sets whether connections are validated on borrow.
         *
         * @param testOnBorrow true to validate connections on borrow, false otherwise
         */
        public void setTestOnBorrow(boolean testOnBorrow) {
            this.testOnBorrow = testOnBorrow;
        }

        /**
         * Gets whether connections are validated on return.
         *
         * @return true if connections are validated on return, false otherwise
         */
        public boolean isTestOnReturn() {
            return testOnReturn;
        }

        /**
         * Sets whether connections are validated on return.
         *
         * @param testOnReturn true to validate connections on return, false otherwise
         */
        public void setTestOnReturn(boolean testOnReturn) {
            this.testOnReturn = testOnReturn;
        }

        /**
         * Gets the SQL validation query.
         *
         * @return the validation query
         */
        public String getValidationQuery() {
            return validationQuery;
        }

        /**
         * Sets the SQL validation query.
         *
         * @param validationQuery the validation query
         */
        public void setValidationQuery(String validationQuery) {
            this.validationQuery = validationQuery;
        }

        /**
         * Validates this pool configuration.
         *
         * @throws IllegalArgumentException if validation fails
         */
        public void validate() {
            if (minIdle < 0) {
                throw new IllegalArgumentException("Minimum idle connections must be non-negative");
            }
            if (maxActive <= 0) {
                throw new IllegalArgumentException("Maximum active connections must be greater than 0");
            }
            if (minIdle > maxActive) {
                throw new IllegalArgumentException("Minimum idle connections cannot exceed maximum active connections");
            }
            if (maxWait < 0) {
                throw new IllegalArgumentException("Maximum wait time must be non-negative");
            }
            if (timeBetweenEvictionRuns < 0) {
                throw new IllegalArgumentException("Time between eviction runs must be non-negative");
            }
            if (minEvictableIdleTime < 0) {
                throw new IllegalArgumentException("Minimum evictable idle time must be non-negative");
            }
            if (validationQuery == null || validationQuery.trim().isEmpty()) {
                throw new IllegalArgumentException("Validation query must not be empty");
            }
        }
    }

    /**
     * Data source type enumeration.
     * Defines whether a data source is intended for write operations (MASTER) or read operations (SLAVE).
     * This enumeration categorizes data sources by their intended usage pattern.
     */
    public enum DataSourceType {
        /** Master data source for write operations. */
        MASTER,
        /** Slave data source for read operations. */
        SLAVE
    }

    /**
     * Data source routing strategy enumeration.
     * Defines how data source selection is determined for operations.
     * This enumeration specifies different approaches for choosing which data source to use
     * for a particular operation or request.
     */
    public enum RoutingStrategy {
        /** Uses annotations for data source routing. */
        ANNOTATION,
        /** Uses aspect-oriented programming for data source routing. */
        ASPECT,
        /** Uses interceptors for data source routing. */
        INTERCEPTOR
    }

    /**
     * Load balancing strategy enumeration.
     * Defines how read operations are distributed across multiple slave data sources.
     * This enumeration provides different algorithms for distributing read traffic among
     * available slave data sources to optimize performance and availability.
     */
    public enum LoadBalancingStrategy {
        /** Round-robin selection of slave data sources. */
        ROUND_ROBIN,
        /** Random selection of slave data sources. */
        RANDOM,
        /** Weighted selection of slave data sources based on configured weights. */
        WEIGHTED
    }
}
