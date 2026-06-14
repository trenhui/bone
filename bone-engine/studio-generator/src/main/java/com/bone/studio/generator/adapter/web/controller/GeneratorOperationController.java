package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.studio.generator.application.dto.GeneratorOperationView;
import com.bone.studio.generator.application.service.GenerationTaskOperationService;
import com.bone.studio.generator.common.GeneratorApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(GeneratorApiPaths.OPERATIONS)
@RequiredArgsConstructor
public class GeneratorOperationController {

  private final GenerationTaskOperationService operationService;

  @GetMapping("/{operationId}")
  public ApiResponse<GeneratorOperationView> getOperation(@PathVariable String operationId) {
    GeneratorOperationView view = operationService.toOperationView(operationId);
    if (view == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "操作不存在");
    }
    return ApiResponse.success(view);
  }
}
