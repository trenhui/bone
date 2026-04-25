package com.bone.studio.generator.application.command.cmd;

import java.util.List;
import java.util.Map;

public class GenerateCodeCommand {
    private String templateId;
    private String name;
    private String description;
    private String language;
    private String framework;
    private Map<String, Object> parameters;
    private List<String> tags;
    private String outputFormat;
    private String outputPath;
    private boolean includeTests;
    private boolean includeDocumentation;
    private String dataSourceId;
    private List<String> tableNames;
    private String basePackage;
    private String moduleName;

    private GenerateCodeCommand() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLanguage() {
        return language;
    }

    public String getFramework() {
        return framework;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public boolean isIncludeTests() {
        return includeTests;
    }

    public boolean isIncludeDocumentation() {
        return includeDocumentation;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public List<String> getTableNames() {
        return tableNames;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public String getModuleName() {
        return moduleName;
    }

    public static class Builder {
        private String templateId;
        private String name;
        private String description;
        private String language;
        private String framework;
        private Map<String, Object> parameters;
        private List<String> tags;
        private String outputFormat;
        private String outputPath;
        private boolean includeTests;
        private boolean includeDocumentation;
        private String dataSourceId;
        private List<String> tableNames;
        private String basePackage;
        private String moduleName;

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder framework(String framework) {
            this.framework = framework;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder outputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        public Builder outputPath(String outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public Builder includeTests(boolean includeTests) {
            this.includeTests = includeTests;
            return this;
        }

        public Builder includeDocumentation(boolean includeDocumentation) {
            this.includeDocumentation = includeDocumentation;
            return this;
        }

        public Builder dataSourceId(String dataSourceId) {
            this.dataSourceId = dataSourceId;
            return this;
        }

        public Builder tableNames(List<String> tableNames) {
            this.tableNames = tableNames;
            return this;
        }

        public Builder basePackage(String basePackage) {
            this.basePackage = basePackage;
            return this;
        }

        public Builder moduleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        public GenerateCodeCommand build() {
            GenerateCodeCommand command = new GenerateCodeCommand();
            command.templateId = templateId;
            command.name = name;
            command.description = description;
            command.language = language;
            command.framework = framework;
            command.parameters = parameters;
            command.tags = tags;
            command.outputFormat = outputFormat;
            command.outputPath = outputPath;
            command.includeTests = includeTests;
            command.includeDocumentation = includeDocumentation;
            command.dataSourceId = dataSourceId;
            command.tableNames = tableNames;
            command.basePackage = basePackage;
            command.moduleName = moduleName;
            return command;
        }
    }
}