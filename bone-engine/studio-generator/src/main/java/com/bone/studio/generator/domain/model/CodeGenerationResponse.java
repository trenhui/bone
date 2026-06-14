package com.bone.studio.generator.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CodeGenerationResponse {
  private String generationId;
  private String status;
  private String name;
  private String description;
  private String language;
  private String framework;
  private List<GeneratedFile> generatedFiles;
  private String downloadUrl;
  private LocalDateTime generatedAt;
  private String errorMessage;

  @Data
  @Builder
  public static class GeneratedFile {
    private String fileName;
    private String fileType;
    private String content;
    private String path;
    private long size;
  }
}
