package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.studio.generator.application.GenerateCodeApplicationService;
import com.bone.studio.generator.application.command.cmd.GenerateCodeCommand;
import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.domain.model.code.CodeGenerationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 同步代码生成任务（原 {@code POST /api/v1/generator/generate}）。 */
@RestController
@RequestMapping(GeneratorApiPaths.GENERATION_TASKS)
@RequiredArgsConstructor
public class GenerationTaskController {

  private final GenerateCodeApplicationService generateCodeHandler;

  @PostMapping
  public ApiResponse<CodeGenerationResponse> create(@RequestBody GenerateCodeCommand command) {
    return ApiResponse.success(generateCodeHandler.handle(command));
  }
}
