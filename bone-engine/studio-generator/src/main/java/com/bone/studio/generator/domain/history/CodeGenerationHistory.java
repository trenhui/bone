package com.bone.studio.generator.domain.history;

import com.bone.core.domain.AggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import java.util.List;

@Table("gen_code_generation_history")
public class CodeGenerationHistory extends AggregateRoot<Long> {

    private Long id;
    private String taskId;
    private String templateId;
    private String templateName;
    private String generationName;
    private String dataSourceId;
    private List<String> tableNames;
    private String basePackage;
    private String moduleName;
    private String status;
    private Integer fileCount;
    private Long executionTime;
    private String outputPath;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    private CodeGenerationHistory() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CodeGenerationHistory create(String taskId, String templateId, String templateName, 
                                             String generationName, String dataSourceId, 
                                             List<String> tableNames, String basePackage, 
                                             String moduleName) {
        CodeGenerationHistory history = new CodeGenerationHistory();
        history.taskId = taskId;
        history.templateId = templateId;
        history.templateName = templateName;
        history.generationName = generationName;
        history.dataSourceId = dataSourceId;
        history.tableNames = tableNames;
        history.basePackage = basePackage;
        history.moduleName = moduleName;
        history.status = "PENDING";
        history.startedAt = LocalDateTime.now();
        history.createdAt = LocalDateTime.now();
        return history;
    }

    public void complete(int fileCount, long executionTime, String outputPath) {
        this.status = "SUCCESS";
        this.fileCount = fileCount;
        this.executionTime = executionTime;
        this.outputPath = outputPath;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getGenerationName() {
        return generationName;
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

    public String getStatus() {
        return status;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public Long getExecutionTime() {
        return executionTime;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public static class Builder {
        private Long id;
        private String taskId;
        private String templateId;
        private String templateName;
        private String generationName;
        private String dataSourceId;
        private List<String> tableNames;
        private String basePackage;
        private String moduleName;
        private String status;
        private Integer fileCount;
        private Long executionTime;
        private String outputPath;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder templateName(String templateName) {
            this.templateName = templateName;
            return this;
        }

        public Builder generationName(String generationName) {
            this.generationName = generationName;
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

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder fileCount(Integer fileCount) {
            this.fileCount = fileCount;
            return this;
        }

        public Builder executionTime(Long executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public Builder outputPath(String outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder startedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public CodeGenerationHistory build() {
            CodeGenerationHistory history = new CodeGenerationHistory();
            history.id = id;
            history.taskId = taskId;
            history.templateId = templateId;
            history.templateName = templateName;
            history.generationName = generationName;
            history.dataSourceId = dataSourceId;
            history.tableNames = tableNames;
            history.basePackage = basePackage;
            history.moduleName = moduleName;
            history.status = status;
            history.fileCount = fileCount;
            history.executionTime = executionTime;
            history.outputPath = outputPath;
            history.errorMessage = errorMessage;
            history.startedAt = startedAt;
            history.completedAt = completedAt;
            history.createdAt = createdAt;
            return history;
        }
    }
}