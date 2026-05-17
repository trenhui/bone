package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.application.usecase.CreateCodeGenerationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(GeneratorApiPaths.CODE_GENERATION)
@RequiredArgsConstructor
public class CodeGenerationController {

    private final CreateCodeGenerationUseCase createCodeGenerationUseCase;

    @PostMapping
    public ApiResponse<String> createCodeGeneration(@RequestBody CreateCodeGenerationCommand command) {
        return ApiResponse.success(createCodeGenerationUseCase.execute(command));
    }

    @GetMapping("/tasks/{taskId}/download")
    public void downloadCode(@PathVariable String taskId) {
    }

    @GetMapping("/tasks/{taskId}/status")
    public ApiResponse<String> getTaskStatus(@PathVariable String taskId) {
        return ApiResponse.success("SUCCESS");
    }
}
