package com.bone.studio.generator.application.command.cmd;

public class CreateDataSourceCommand {
    private String name;
    private String type;
    private String host;
    private String port;
    private String database;
    private String username;
    private String password;

    private CreateDataSourceCommand() {
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String type;
        private String host;
        private String port;
        private String database;
        private String username;
        private String password;

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

        public CreateDataSourceCommand build() {
            CreateDataSourceCommand command = new CreateDataSourceCommand();
            command.name = this.name;
            command.type = this.type;
            command.host = this.host;
            command.port = this.port;
            command.database = this.database;
            command.username = this.username;
            command.password = this.password;
            return command;
        }
    }
}
