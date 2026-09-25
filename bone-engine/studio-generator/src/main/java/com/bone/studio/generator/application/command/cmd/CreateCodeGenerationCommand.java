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

  /** PHYSICAL_DB（默认）或 CATALOG_SNAPSHOT；统一入口复用 CodeGeneratorService 的 catalog 引擎 */
  private String metadataSource;

  /** catalog 模式：按实体编码过滤；空=全部已发布 */
  private List<String> entityCodes;

  private Long tenantId;

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

  public String getMetadataSource() {
    return metadataSource;
  }

  public List<String> getEntityCodes() {
    return entityCodes;
  }

  public Long getTenantId() {
    return tenantId;
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
    private String metadataSource;
    private List<String> entityCodes;
    private Long tenantId;

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

    public Builder metadataSource(String metadataSource) {
      this.metadataSource = metadataSource;
      return this;
    }

    public Builder entityCodes(List<String> entityCodes) {
      this.entityCodes = entityCodes;
      return this;
    }

    public Builder tenantId(Long tenantId) {
      this.tenantId = tenantId;
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
      command.metadataSource = this.metadataSource;
      command.entityCodes = this.entityCodes;
      command.tenantId = this.tenantId;
      return command;
    }
  }
}
