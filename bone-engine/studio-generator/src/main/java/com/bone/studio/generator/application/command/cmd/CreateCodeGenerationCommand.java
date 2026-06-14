package com.bone.studio.generator.application.command.cmd;

import java.util.List;

public class CreateCodeGenerationCommand {
  private String projectName;
  private String basePackage;
  private String moduleName;
  private Long dataSourceId;
  private List<String> tableNames;
  private List<Long> templateIds;
  private String genConfig;

  private CreateCodeGenerationCommand() {}

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

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String projectName;
    private String basePackage;
    private String moduleName;
    private Long dataSourceId;
    private List<String> tableNames;
    private List<Long> templateIds;
    private String genConfig;

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

    public CreateCodeGenerationCommand build() {
      CreateCodeGenerationCommand command = new CreateCodeGenerationCommand();
      command.projectName = this.projectName;
      command.basePackage = this.basePackage;
      command.moduleName = this.moduleName;
      command.dataSourceId = this.dataSourceId;
      command.tableNames = this.tableNames;
      command.templateIds = this.templateIds;
      command.genConfig = this.genConfig;
      return command;
    }
  }
}
