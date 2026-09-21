package com.bone.studio.generator.application.service;

import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.studio.generator.application.dto.GeneratorOperationView;
import com.bone.studio.generator.domain.data.GenerationTask;
import com.bone.studio.generator.domain.repository.GenerationTaskRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GenerationTaskOperationService {

  private static final String TYPE_CODE_GENERATION = "code-generation";

  private final GenerationTaskRepository generationTaskRepository;

  public GenerationTaskOperationService(GenerationTaskRepository generationTaskRepository) {
    this.generationTaskRepository = generationTaskRepository;
  }

  public GenerationTask findByTaskId(String taskId) {
    try {
      return generationTaskRepository.findByTaskId(taskId);
    } catch (MultipleResultsException ex) {
      throw new IllegalStateException("duplicate generation task: " + taskId, ex);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  public GeneratorOperationView toOperationView(String operationId) {
    GenerationTask task = findByTaskId(operationId);
    if (task == null) {
      return null;
    }
    GeneratorOperationView view = new GeneratorOperationView();
    view.setOperationId(operationId);
    view.setType(TYPE_CODE_GENERATION);
    String status = task.getStatus() != null ? task.getStatus() : "PENDING";
    boolean terminal = "SUCCESS".equals(status) || "FAILED".equals(status);
    view.setDone(terminal);
    view.setProgress(progressForStatus(status));
    if ("SUCCESS".equals(status)) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("taskId", task.getTaskId());
      result.put("status", status);
      result.put("zipUrl", task.getZipUrl());
      if (task.getGeneratedFiles() != null) {
        result.put("fileCount", task.getGeneratedFiles().size());
      }
      view.setResult(result);
    } else if ("FAILED".equals(status)) {
      Map<String, Object> error = new LinkedHashMap<>();
      error.put("errorCode", "GEN_GENERATION_FAILED");
      error.put("detail", task.getErrorMessage() != null ? task.getErrorMessage() : "代码生成失败");
      view.setError(error);
    }
    return view;
  }

  private static int progressForStatus(String status) {
    return switch (status) {
      case "PENDING" -> 5;
      case "PROCESSING" -> 50;
      case "SUCCESS" -> 100;
      case "FAILED" -> 100;
      default -> 0;
    };
  }
}
