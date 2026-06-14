package com.bone.studio.generator.domain.model;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
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
}
