package com.bone.studio.generator.domain.data;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.studio.generator.domain.code.GeneratedFile;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Table("gen_generation_task")
public class GenerationTask extends AggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long tenantId;
  private String taskId;
  private String projectName;
  private String basePackage;
  private String moduleName;
  private Long dataSourceId;
  private List<String> tableNames;
  private List<Long> templateIds;
  private String genConfig;
  private List<GeneratedFile> generatedFiles;
  private String zipUrl;
  private String status;
  private String errorMessage;
  private LocalDateTime startedAt;
  private LocalDateTime completedAt;
  private Long createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private boolean deleted;
  private int version;

  private GenerationTask() {}

  public static GenerationTask create(
      Long id,
      Long tenantId,
      String taskId,
      String projectName,
      String basePackage,
      String moduleName,
      Long dataSourceId,
      List<String> tableNames,
      List<Long> templateIds,
      String genConfig) {
    GenerationTask task = new GenerationTask();
    task.id = id;
    task.tenantId = tenantId;
    task.taskId = taskId;
    task.projectName = projectName;
    task.basePackage = basePackage;
    task.moduleName = moduleName;
    task.dataSourceId = dataSourceId;
    task.tableNames = tableNames;
    task.templateIds = templateIds;
    task.genConfig = genConfig;
    task.status = "PENDING";
    LocalDateTime now = LocalDateTime.now();
    task.createdAt = now;
    task.updatedAt = now;
    task.deleted = false;
    task.version = 0;
    return task;
  }

  public void markProcessing() {
    this.status = "PROCESSING";
    this.startedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  public void markCompleted(List<GeneratedFile> files, String zipUrl) {
    this.status = "SUCCESS";
    this.generatedFiles = files;
    this.zipUrl = zipUrl;
    this.completedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  public void markFailed(String errorMessage) {
    this.status = "FAILED";
    this.errorMessage = errorMessage;
    this.completedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  public static class Builder {
    private Long id;
    private Long tenantId;
    private String taskId;
    private String projectName;
    private String basePackage;
    private String moduleName;
    private Long dataSourceId;
    private List<String> tableNames;
    private List<Long> templateIds;
    private String genConfig;
    private List<GeneratedFile> generatedFiles;
    private String zipUrl;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long createdBy;
    private LocalDateTime createdAt;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(Long tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder taskId(String taskId) {
      this.taskId = taskId;
      return this;
    }

    public Builder projectName(String projectName) {
      this.projectName = projectName;
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

    public Builder dataSourceId(Long dataSourceId) {
      this.dataSourceId = dataSourceId;
      return this;
    }

    public Builder tableNames(List<String> tableNames) {
      this.tableNames = tableNames;
      return this;
    }

    public Builder templateIds(List<Long> templateIds) {
      this.templateIds = templateIds;
      return this;
    }

    public Builder genConfig(String genConfig) {
      this.genConfig = genConfig;
      return this;
    }

    public Builder generatedFiles(List<GeneratedFile> generatedFiles) {
      this.generatedFiles = generatedFiles;
      return this;
    }

    public Builder zipUrl(String zipUrl) {
      this.zipUrl = zipUrl;
      return this;
    }

    public Builder status(String status) {
      this.status = status;
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

    public Builder createdBy(Long createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder createdAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public GenerationTask build() {
      GenerationTask task = new GenerationTask();
      task.id = id;
      task.tenantId = tenantId;
      task.taskId = taskId;
      task.projectName = projectName;
      task.basePackage = basePackage;
      task.moduleName = moduleName;
      task.dataSourceId = dataSourceId;
      task.tableNames = tableNames;
      task.templateIds = templateIds;
      task.genConfig = genConfig;
      task.generatedFiles = generatedFiles;
      task.zipUrl = zipUrl;
      task.status = status;
      task.errorMessage = errorMessage;
      task.startedAt = startedAt;
      task.completedAt = completedAt;
      task.createdBy = createdBy;
      task.createdAt = createdAt;
      return task;
    }
  }

  public Long getId() {
    return id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getProjectName() {
    return projectName;
  }

  public String getBasePackage() {
    return basePackage;
  }

  public String getModuleName() {
    return moduleName;
  }

  public Long getDataSourceId() {
    return dataSourceId;
  }

  public List<String> getTableNames() {
    return tableNames;
  }

  public List<Long> getTemplateIds() {
    return templateIds;
  }

  public String getGenConfig() {
    return genConfig;
  }

  public List<GeneratedFile> getGeneratedFiles() {
    return generatedFiles;
  }

  public String getZipUrl() {
    return zipUrl;
  }

  public String getStatus() {
    return status;
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

  public Long getCreatedBy() {
    return createdBy;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public static Builder builder() {
    return new Builder();
  }
}
