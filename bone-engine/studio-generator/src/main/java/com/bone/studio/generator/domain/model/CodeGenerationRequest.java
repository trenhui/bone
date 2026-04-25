package com.bone.studio.generator.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

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