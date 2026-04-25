package com.bone.studio.generator.domain.data;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;

@Table("data_source")
public class DataSource extends AggregateRoot<String> {

    private String id;
    private String name;
    private String type;
    private String host;
    private String port;
    private String database;
    private String username;
    private String password;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private DataSource() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DataSource create(String id, String name, String type, String host, 
                                  String port, String database, String username, String password) {
        if (name == null || name.isEmpty()) {
            throw new DomainException("数据源名称不能为空");
        }
        if (type == null || type.isEmpty()) {
            throw new DomainException("数据库类型不能为空");
        }
        if (host == null || host.isEmpty()) {
            throw new DomainException("主机地址不能为空");
        }
        if (port == null || port.isEmpty()) {
            throw new DomainException("端口不能为空");
        }
        if (database == null || database.isEmpty()) {
            throw new DomainException("数据库名称不能为空");
        }
        if (username == null || username.isEmpty()) {
            throw new DomainException("用户名不能为空");
        }

        DataSource dataSource = new DataSource();
        dataSource.id = id;
        dataSource.name = name;
        dataSource.type = type;
        dataSource.host = host;
        dataSource.port = port;
        dataSource.database = database;
        dataSource.username = username;
        dataSource.password = password;
        dataSource.status = "INACTIVE";
        dataSource.createdAt = LocalDateTime.now();
        dataSource.updatedAt = LocalDateTime.now();

        return dataSource;
    }

    public void update(String name, String type, String host, String port, 
                     String database, String username, String password) {
        if (name == null || name.isEmpty()) {
            throw new DomainException("数据源名称不能为空");
        }
        if (type == null || type.isEmpty()) {
            throw new DomainException("数据库类型不能为空");
        }
        if (host == null || host.isEmpty()) {
            throw new DomainException("主机地址不能为空");
        }
        if (port == null || port.isEmpty()) {
            throw new DomainException("端口不能为空");
        }
        if (database == null || database.isEmpty()) {
            throw new DomainException("数据库名称不能为空");
        }
        if (username == null || username.isEmpty()) {
            throw new DomainException("用户名不能为空");
        }

        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.updatedAt = LocalDateTime.now();
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public static class Builder {
        private String id;
        private String name;
        private String type;
        private String host;
        private String port;
        private String database;
        private String username;
        private String password;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(String port) {
            this.port = port;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public DataSource build() {
            DataSource dataSource = new DataSource();
            dataSource.id = id;
            dataSource.name = name;
            dataSource.type = type;
            dataSource.host = host;
            dataSource.port = port;
            dataSource.database = database;
            dataSource.username = username;
            dataSource.password = password;
            dataSource.status = "INACTIVE";
            dataSource.createdAt = LocalDateTime.now();
            dataSource.updatedAt = LocalDateTime.now();
            return dataSource;
        }
    }
}