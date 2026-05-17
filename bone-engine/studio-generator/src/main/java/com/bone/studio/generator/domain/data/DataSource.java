package com.bone.studio.generator.domain.data;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;

/**
 * 代码生成数据源（对齐 bone-init {@code gen_data_source}）。
 */
@Table("gen_data_source")
public class DataSource extends AggregateRoot<Long> {

    private Long id;
    private Long tenantId;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String dbName;
    private String username;
    private String passwordEncrypted;
    private String params;
    @Column(name = "is_enabled")
    private boolean enabled = true;
    private LocalDateTime lastTestAt;
    private String lastTestResult;
    private String lastTestMessage;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;
    private int version;

    private DataSource() {
    }

    public static DataSource create(
            Long id,
            Long tenantId,
            String name,
            String dbType,
            String host,
            Integer port,
            String dbName,
            String username,
            String passwordEncrypted) {
        validate(name, dbType, host, port, dbName, username);
        DataSource ds = new DataSource();
        ds.id = id;
        ds.tenantId = tenantId == null ? 0L : tenantId;
        ds.name = name;
        ds.dbType = dbType.toLowerCase();
        ds.host = host;
        ds.port = port;
        ds.dbName = dbName;
        ds.username = username;
        ds.passwordEncrypted = passwordEncrypted;
        ds.enabled = true;
        LocalDateTime now = LocalDateTime.now();
        ds.createdAt = now;
        ds.updatedAt = now;
        ds.deleted = false;
        ds.version = 0;
        return ds;
    }

    public void update(
            String name,
            String dbType,
            String host,
            Integer port,
            String dbName,
            String username,
            String passwordEncrypted) {
        validate(name, dbType, host, port, dbName, username);
        this.name = name;
        this.dbType = dbType.toLowerCase();
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.username = username;
        if (passwordEncrypted != null && !passwordEncrypted.isBlank()) {
            this.passwordEncrypted = passwordEncrypted;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void recordTestResult(boolean success, String message) {
        this.lastTestAt = LocalDateTime.now();
        this.lastTestResult = success ? "SUCCESS" : "FAILED";
        this.lastTestMessage = message;
        this.updatedAt = LocalDateTime.now();
        if (success) {
            this.enabled = true;
        }
    }

    private static void validate(
            String name, String dbType, String host, Integer port, String dbName, String username) {
        if (name == null || name.isBlank()) {
            throw new DomainException("数据源名称不能为空");
        }
        if (dbType == null || dbType.isBlank()) {
            throw new DomainException("数据库类型不能为空");
        }
        if (host == null || host.isBlank()) {
            throw new DomainException("主机地址不能为空");
        }
        if (port == null || port <= 0) {
            throw new DomainException("端口无效");
        }
        if (dbName == null || dbName.isBlank()) {
            throw new DomainException("数据库名称不能为空");
        }
        if (username == null || username.isBlank()) {
            throw new DomainException("用户名不能为空");
        }
    }

    /** 兼容 JDBC 网关：库名 */
    public String getDatabase() {
        return dbName;
    }

    /** 兼容 JDBC 网关：类型 */
    public String getType() {
        return dbType;
    }

    /** 兼容 JDBC 网关：明文密码字段名历史调用 */
    public String getPassword() {
        return passwordEncrypted;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getDbType() {
        return dbType;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getDbName() {
        return dbName;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordEncrypted() {
        return passwordEncrypted;
    }

    public String getParams() {
        return params;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getLastTestAt() {
        return lastTestAt;
    }

    public String getLastTestResult() {
        return lastTestResult;
    }

    public String getLastTestMessage() {
        return lastTestMessage;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public int getVersion() {
        return version;
    }
}
