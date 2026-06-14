package com.bone.studio.generator.domain.code;

import java.util.List;

public class CodeGenerationResponse {
  private String generationId;
  private String status;
  private String message;
  private List<GeneratedFile> generatedFiles;
  private long executionTime;
  private String outputPath;

  private CodeGenerationResponse() {}

  public static Builder builder() {
    return new Builder();
  }

  public String getGenerationId() {
    return generationId;
  }

  public String getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public List<GeneratedFile> getGeneratedFiles() {
    return generatedFiles;
  }

  public long getExecutionTime() {
    return executionTime;
  }

  public String getOutputPath() {
    return outputPath;
  }

  public static class Builder {
    private String generationId;
    private String status;
    private String message;
    private List<GeneratedFile> generatedFiles;
    private long executionTime;
    private String outputPath;

    public Builder generationId(String generationId) {
      this.generationId = generationId;
      return this;
    }

    public Builder status(String status) {
      this.status = status;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder generatedFiles(List<GeneratedFile> generatedFiles) {
      this.generatedFiles = generatedFiles;
      return this;
    }

    public Builder executionTime(long executionTime) {
      this.executionTime = executionTime;
      return this;
    }

    public Builder outputPath(String outputPath) {
      this.outputPath = outputPath;
      return this;
    }

    public CodeGenerationResponse build() {
      CodeGenerationResponse response = new CodeGenerationResponse();
      response.generationId = generationId;
      response.status = status;
      response.message = message;
      response.generatedFiles = generatedFiles;
      response.executionTime = executionTime;
      response.outputPath = outputPath;
      return response;
    }
  }
}
