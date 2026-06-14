package com.bone.studio.generator.application.service;

import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.application.command.handler.CreateCodeGenerationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodeGenerationTaskService {

  private final CreateCodeGenerationHandler createCodeGenerationHandler;

  @Async
  public void executeTask(CreateCodeGenerationCommand command, CodeGenerationCallback callback) {
    try {
      String taskId = createCodeGenerationHandler.handle(command);
      callback.onSuccess(taskId);
    } catch (Exception e) {
      callback.onFailure(e);
    }
  }

  public interface CodeGenerationCallback {
    void onSuccess(String taskId);

    void onFailure(Exception e);
  }
}
