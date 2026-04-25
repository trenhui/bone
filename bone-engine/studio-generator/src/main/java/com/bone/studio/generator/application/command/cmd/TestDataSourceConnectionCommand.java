package com.bone.studio.generator.application.command.cmd;

public class TestDataSourceConnectionCommand {
    private String id;

    private TestDataSourceConnectionCommand() {
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

        public TestDataSourceConnectionCommand build() {
            TestDataSourceConnectionCommand command = new TestDataSourceConnectionCommand();
            command.id = this.id;
            return command;
        }
    }
}
