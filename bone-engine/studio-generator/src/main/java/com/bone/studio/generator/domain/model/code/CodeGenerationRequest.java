package com.bone.studio.generator.domain.model.code;

import com.bone.studio.generator.domain.model.catalog.MetadataSourceType;
import java.util.List;
import java.util.Map;

public class CodeGenerationRequest {
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
  private MetadataSourceType metadataSource;
  private Long tenantId;
  private List<String> entityCodes;

  private CodeGenerationRequest() {}

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

  public MetadataSourceType getMetadataSource() {
    return metadataSource != null ? metadataSource : MetadataSourceType.PHYSICAL_DB;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public List<String> getEntityCodes() {
    return entityCodes;
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
    private MetadataSourceType metadataSource;
    private Long tenantId;
    private List<String> entityCodes;

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

    public Builder metadataSource(MetadataSourceType metadataSource) {
      this.metadataSource = metadataSource;
      return this;
    }

    public Builder tenantId(Long tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder entityCodes(List<String> entityCodes) {
      this.entityCodes = entityCodes;
      return this;
    }

    public CodeGenerationRequest build() {
      CodeGenerationRequest request = new CodeGenerationRequest();
      request.templateId = templateId;
      request.name = name;
      request.description = description;
      request.language = language;
      request.framework = framework;
      request.parameters = parameters;
      request.tags = tags;
      request.outputFormat = outputFormat;
      request.outputPath = outputPath;
      request.includeTests = includeTests;
      request.includeDocumentation = includeDocumentation;
      request.dataSourceId = dataSourceId;
      request.tableNames = tableNames;
      request.basePackage = basePackage;
      request.moduleName = moduleName;
      request.metadataSource = metadataSource;
      request.tenantId = tenantId;
      request.entityCodes = entityCodes;
      return request;
    }
  }
}
