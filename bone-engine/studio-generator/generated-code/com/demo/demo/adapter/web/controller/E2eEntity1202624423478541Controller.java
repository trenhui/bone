package com.demo.demo.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.demo.demo.adapter.web.dto.response.E2eEntity1202624423478541Response;
import com.demo.demo.application.E2eEntity1202624423478541ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * E2E实体 (物理表: meta_e2e_1202624423590500)控制器。
 *
 * <p>由代码生成器基于表 e2e_entity_1202624423478541 生成。
 */
@Tag(name = "E2E实体 (物理表: meta_e2e_1202624423590500)", description = "E2E实体 (物理表: meta_e2e_1202624423590500)管理接口")
@RestController
@RequestMapping("/api/v1/demo/e2eentity1202624423478541")
@RequiredArgsConstructor
public class E2eEntity1202624423478541Controller {

  private final E2eEntity1202624423478541ApplicationService applicationService;

  @Operation(summary = "查询 E2E实体 (物理表: meta_e2e_1202624423590500) 详情")
  @GetMapping("/{id}")
  public ApiResponse<E2eEntity1202624423478541Response> getById(@PathVariable Long id) {
    return ApiResponse.success(applicationService.get(id));
  }

  @Operation(summary = "分页查询 E2E实体 (物理表: meta_e2e_1202624423590500)")
  @GetMapping("/page")
  public ApiResponse<PageResult<E2eEntity1202624423478541Response>> page(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(applicationService.page(page, size));
  }
}
