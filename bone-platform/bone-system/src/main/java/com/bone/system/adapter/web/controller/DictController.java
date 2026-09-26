package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.assembler.DictAssembler;
import com.bone.system.adapter.web.dto.request.CreateDictReq;
import com.bone.system.adapter.web.dto.request.DictPageReq;
import com.bone.system.adapter.web.dto.request.UpdateDictReq;
import com.bone.system.adapter.web.dto.response.DictResp;
import com.bone.system.application.DictApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 系统字典控制器。 */
@Tag(name = "系统字典", description = "系统字典管理接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/dicts")
@RequiredArgsConstructor
public class DictController {

  private final DictApplicationService dictApplicationService;
  private final DictAssembler dictAssembler;

  @Operation(summary = "创建字典项")
  @PostMapping
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Long> create(@Valid @RequestBody CreateDictReq req) {
    return ApiResponse.success(dictApplicationService.create(dictAssembler.toCommand(req)));
  }

  @Operation(summary = "更新字典项")
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateDictReq req) {
    dictApplicationService.update(dictAssembler.toCommand(id, req));
    return ApiResponse.success();
  }

  @Operation(summary = "删除字典项")
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    dictApplicationService.delete(id);
    return ApiResponse.success();
  }

  @Operation(summary = "按类型查询字典列表")
  @GetMapping("/type/{type}")
  public ApiResponse<List<DictResp>> listByType(@PathVariable String type) {
    return ApiResponse.success(
        dictApplicationService.listByType(type).stream().map(dictAssembler::toResp).toList());
  }

  @Operation(summary = "按类型与编码查询字典")
  @GetMapping("/{type}/{code}")
  public ApiResponse<DictResp> getByTypeAndCode(
      @PathVariable String type, @PathVariable String code) {
    return ApiResponse.success(
        dictApplicationService
            .getByTypeAndCode(type, code)
            .map(dictAssembler::toResp)
            .orElse(null));
  }

  @Operation(summary = "分页查询字典")
  @GetMapping("/page")
  public ApiResponse<PageResult<DictResp>> page(DictPageReq req) {
    return ApiResponse.success(
        dictApplicationService.page(dictAssembler.toQuery(req)).map(dictAssembler::toResp));
  }
}
