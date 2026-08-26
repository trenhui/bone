package com.bone.metadata.engine.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Operation metadata model class Used to define various operations and methods for entities */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class OperationMetadata {
  // Basic information
  private String id;
  private String name;
  private String apiName;
  private String label;
  @Builder.Default private Map<String, String> labels = new HashMap<>(); // Multi-language labels
  private String description;
  private String domain;

  // Operation definition
  private String entityName;
  private String methodName;
  private String implementationClass;
  private String returnType;
  private String scriptLanguage;
  private String scriptContent;

  // Parameter information
  @Builder.Default private List<OperationParameterMetadata> parameters = new ArrayList<>();

  // Permission information
  @Builder.Default private boolean requiresAuth = true;
  private String requiredPermission;
  private String sensitivityLevel;

  // Cache configuration
  @Builder.Default private boolean cacheable = false;
  @Builder.Default private int cacheTtl = 300; // Default 5 minutes

  // Rate limiting configuration
  @Builder.Default private boolean rateLimited = false;
  private int maxRequestsPerMinute;

  // Monitoring configuration
  @Builder.Default private boolean trackPerformance = false;
  @Builder.Default private boolean trackAudit = true;

  // Business status
  @Builder.Default private boolean active = true;
  @Builder.Default private boolean system = false;

  // Lifecycle information
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String createdBy;
  private String updatedBy;

  // Metadata version information
  private String version;
  private String previousVersionId;

  /** Gets the API name */
  public String getApiName() {
    return this.apiName;
  }

  /** Adds an operation parameter */
  public void addParameter(OperationParameterMetadata parameter) {
    if (parameters == null) {
      parameters = new ArrayList<>();
    }
    parameters.add(parameter);
  }

  /** Gets parameter metadata */
  public OperationParameterMetadata getParameter(String parameterName) {
    if (parameters == null) {
      return null;
    }
    return parameters.stream()
        .filter(p -> p.getName().equals(parameterName))
        .findFirst()
        .orElse(null);
  }

  /** Operation parameter metadata inner class */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OperationParameterMetadata {
    private String id;
    private String name;
    private String label;
    private String type;
    @Builder.Default private boolean required = false;
    private String defaultValue;
    private String description;
    @Builder.Default private boolean encrypted = false;
    private String validationPattern;

    /** Gets the parameter name */
    public String getName() {
      return this.name;
    }
  }
}
