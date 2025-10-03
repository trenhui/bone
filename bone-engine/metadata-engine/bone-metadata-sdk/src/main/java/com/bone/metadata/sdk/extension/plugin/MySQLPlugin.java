package com.bone.metadata.sdk.extension.plugin;

import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.sql.dialect.SqlDialect;
import com.bone.metadata.sdk.support.security.service.SecurityService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.TypeUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
public class MySQLPlugin implements Plugin {

    // 运行时资源
    private HikariDataSource dataSource;
    private SqlDialect sqlDialect;

    // 上下文引用
    private PluginContext context;

    // 状态标记
    private boolean active = false;

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        Properties config = context.getConfig();

        try {
            // 1. 配置验证
            validateConfig(config);

            // 2. 安全校验
            SecurityService securityService = context.getService(SecurityService.class);
            if (securityService != null) {
                securityService.validateConnection(config.getProperty("jdbc.url"));
            } else {
                log.warn("SecurityService not available, skipping connection validation");
            }

            // 3. 初始化数据源
            initializeDataSource(config);

            // 4. 注册服务
            registerSqlDialect();

            log.info("MySQL Plugin initialized successfully");

        } catch (Exception e) {
            log.error("MySQL Plugin initialization failed", e);
            throw new PluginInitializationException("Failed to initialize MySQL Plugin", e);
        }
    }

    private void validateConfig(Properties config) {
        String[] requiredKeys = {"jdbc.url", "username"};
        for (String key : requiredKeys) {
            if (!config.containsKey(key)) {
                throw new PluginConfigurationException("Missing required property: " + key);
            }
        }

        // 设置默认值
        config.putIfAbsent("connectionTimeout", "30000");
        config.putIfAbsent("idleTimeout", "600000");
    }

    private void initializeDataSource(Properties config) throws SQLException {
        HikariConfig dsConfig = new HikariConfig();
        dsConfig.setJdbcUrl(config.getProperty("jdbc.url"));
        dsConfig.setUsername(config.getProperty("username"));
        dsConfig.setPassword(config.getProperty("password", ""));
        dsConfig.setPoolName("MySQL-Plugin-Pool");

        // 设置超时相关配置
        dsConfig.setConnectionTimeout(Long.parseLong(config.getProperty("connectionTimeout", "30000")));
        dsConfig.setIdleTimeout(Long.parseLong(config.getProperty("idleTimeout", "600000")));

        dataSource = new HikariDataSource(dsConfig);

        // 测试连接
        try (Connection testConn = dataSource.getConnection();
             Statement stmt = testConn.createStatement()) {
            stmt.execute("SELECT 1");
            log.debug("Successfully connected to MySQL database");
        }
    }

    private void registerSqlDialect() {
        sqlDialect = new MySQLDialect(dataSource);
        context.registerService(SqlDialect.class, sqlDialect);
        log.info("Registered MySQL SQL dialect service");
    }

    @Override
    public void start() {
        if (!active) {
            // 连接池预热
            try (Connection connection = dataSource.getConnection();
                 Statement stmt = connection.createStatement()) {
                stmt.execute("SELECT 1");
                log.debug("MySQL connection pool warmed up");
            } catch (SQLException e) {
                log.error("MySQL Plugin warm-up failed", e);
            }
            active = true;
            log.info("MySQL Plugin started");
        }
    }

    @Override
    public void stop() {
        if (active) {
            active = false;
            log.info("MySQL Plugin stopped");
        }
    }

    @Override
    public void destroy() {
        stop();

        // 关闭数据库连接池
        if (dataSource != null) {
            dataSource.close();
            log.info("MySQL connection pool closed");
            dataSource = null;
        }
        sqlDialect = null;
        context = null;
    }

    @Override
    public String getId() {
        return "com.bone.metadata.plugin.mysql";
    }

    @Override
    public String getName() {
        return "MySQL Data Plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Provides MySQL database integration and SQL dialect support";
    }

    @Override
    public String getPluginType() {
        return "database";
    }

    @Override
    public Properties getConfig() {
        return context != null ? context.getConfig() : new Properties();
    }

    @Override
    public void setConfig(Properties config) {
        if (context != null) {
            context.updateConfig(config);
        }
    }

    // MySQL 方言实现
    public static class MySQLDialect implements SqlDialect {
        private final DataSource dataSource;

        public MySQLDialect(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public String getDatabaseProductName() {
            return "MySQL";
        }

        @Override
        public String paginateQuery(String baseSql, long offset, long limit) {
            return baseSql + " LIMIT " + limit + " OFFSET " + offset;
        }

        @Override
        public String generateCreateTableStatement(TableMetadata table) {
            StringBuilder sql = new StringBuilder("CREATE TABLE ");
            sql.append(quoteIdentifier(table.getName())).append(" (\n");

            List<String> columns = new ArrayList<>();
            List<String> primaryKeys = new ArrayList<>();

            table.getColumns().forEach(column -> {
                StringBuilder colDef = new StringBuilder("  ");
                colDef.append(quoteIdentifier(column.getName())).append(" ")
                        .append(getColumnType(column.getType()));

                if (!column.isNullable()) {
                    colDef.append(" NOT NULL");
                }

                if (column.isPrimaryKey()) {
                    primaryKeys.add(quoteIdentifier(column.getName()));
                }

                columns.add(colDef.toString());
            });

            sql.append(String.join(",\n", columns));

            if (!primaryKeys.isEmpty()) {
                sql.append(",\n  PRIMARY KEY (");
                sql.append(String.join(", ", primaryKeys));
                sql.append(")");
            }

            sql.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            return sql.toString();
        }

        @Override
        public String getColumnType(Class<?> javaType) {
            if (TypeUtils.isAssignable(javaType, String.class)) {
                return "VARCHAR(255)";
            } else if (TypeUtils.isAssignable(javaType, Long.class) ||
                    TypeUtils.isAssignable(javaType, long.class)) {
                return "BIGINT";
            } else if (TypeUtils.isAssignable(javaType, Integer.class) ||
                    TypeUtils.isAssignable(javaType, int.class)) {
                return "INT";
            } else if (TypeUtils.isAssignable(javaType, Boolean.class) ||
                    TypeUtils.isAssignable(javaType, boolean.class)) {
                return "TINYINT(1)";
            } else if (TypeUtils.isAssignable(javaType, java.util.Date.class)) {
                return "DATETIME";
            } else if (TypeUtils.isAssignable(javaType, Double.class) ||
                    TypeUtils.isAssignable(javaType, double.class)) {
                return "DOUBLE";
            } else {
                return "TEXT";
            }
        }

        @Override
        public String quoteIdentifier(String identifier) {
            return "`" + identifier.replace("`", "``") + "`";
        }
    }
}