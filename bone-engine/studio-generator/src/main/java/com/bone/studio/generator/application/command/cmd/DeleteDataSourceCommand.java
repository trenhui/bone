package com.bone.studio.generator.application.command.cmd;

public class DeleteDataSourceCommand {
    private String id;

    private DeleteDataSourceCommand() {
    }

    public String getId() {
        return id;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public DeleteDataSourceCommand build() {
            DeleteDataSourceCommand command = new DeleteDataSourceCommand();
            command.id = this.id;
            return command;
        }
    }
}
