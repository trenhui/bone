package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.application.dto.GeneratorOperationView;
import com.bone.studio.generator.application.service.CodeGenerationAsyncService;
import com.bone.studio.generator.application.service.GenerationTaskOperationService;
import com.bone.studio.generator.application.usecase.CreateCodeGenerationUseCase;
import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.config.GeneratorProperties;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(GeneratorApiPaths.CODE_GENERATION)
@RequiredArgsConstructor
public class CodeGenerationController {

    private final CreateCodeGenerationUseCase createCodeGenerationUseCase;
    private final CodeGenerationAsyncService codeGenerationAsyncService;
    private final GenerationTaskOperationService operationService;
    private final GeneratorProperties generatorProperties;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> createCodeGeneration(
            @RequestBody CreateCodeGenerationCommand command,
            @RequestParam(required = false) Boolean sync) {
        if (resolveSync(sync)) {
            String taskId = createCodeGenerationUseCase.execute(command);
            return ResponseEntity.ok(ApiResponse.success(taskId));
        }
        String taskId = codeGenerationAsyncService.submit(command);
        Map<String, Object> accepted = new LinkedHashMap<>();
        accepted.put("operationId", taskId);
        accepted.put("taskId", taskId);
        String location = GeneratorApiPaths.V1_PREFIX + "/operations/" + taskId;
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create(location))
                .body(ApiResponse.success(accepted));
    }

    @GetMapping("/tasks/{taskId}/download")
    public void downloadCode(@PathVariable String taskId) {
        // [Target] MinIO 产物下载
    }

    @GetMapping("/tasks/{taskId}/status")
    public ApiResponse<String> getTaskStatus(@PathVariable String taskId) {
        GeneratorOperationView view = operationService.toOperationView(taskId);
        if (view == null) {
            return ApiResponse.success("UNKNOWN");
        }
        if (view.getResult() != null && view.getResult().get("status") != null) {
            return ApiResponse.success(view.getResult().get("status").toString());
        }
        return ApiResponse.success(view.isDone() ? "FAILED" : "PROCESSING");
    }

    private boolean resolveSync(Boolean syncParam) {
        if (!generatorProperties.getLro().isCodeGenerationEnabled()) {
            return true;
        }
        if (syncParam != null) {
            return syncParam;
        }
        return generatorProperties.getLro().isCodeGenerationSyncByDefault();
    }
}
