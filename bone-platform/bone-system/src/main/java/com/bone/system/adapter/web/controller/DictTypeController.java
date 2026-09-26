package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.assembler.DictAssembler;
import com.bone.system.adapter.web.dto.request.CreateDictTypeReq;
import com.bone.system.adapter.web.dto.request.DictTypePageReq;
import com.bone.system.adapter.web.dto.request.UpdateDictTypeReq;
import com.bone.system.adapter.web.dto.response.DictEnumDiffResp;
import com.bone.system.adapter.web.dto.response.DictExportResp;
import com.bone.system.adapter.web.dto.response.DictTypeResp;
import com.bone.system.application.DictApplicationService;
import com.bone.system.application.query.dto.DictEnumDiffDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典类型接口（定义层）。
 *
 * <p>路径用 {@code /dict/types} 而不是 v1 的 {@code /dicts}：v1 把「类型」与「项」混在一个资源里，
 * 导致「停用整个值域」这类操作无处安放；两级模型下两者生命周期不同，理应是两个资源。
 */
@Tag(name = "字典类型", description = "字典值域定义（ENUM/LIST/CASCADE）")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/dict/types")
@RequiredArgsConstructor
public class DictTypeController {

  private final DictApplicationService dictApplicationService;
  private final DictAssembler dictAssembler;

  @Operation(summary = "新建字典类型")
  @PostMapping
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Long> create(@Valid @RequestBody CreateDictTypeReq req) {
    return ApiResponse.success(dictApplicationService.createType(dictAssembler.toCommand(req)));
  }

  @Operation(summary = "更新字典类型（内置类型仅允许改名称/排序/启停/说明）")
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateDictTypeReq req) {
    dictApplicationService.updateType(dictAssembler.toCommand(id, req));
    return ApiResponse.success();
  }

  @Operation(summary = "删除字典类型（内置或仍有项时拒绝）")
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    dictApplicationService.deleteType(id);
    return ApiResponse.success();
  }

  @Operation(summary = "按编码读取字典类型")
  @GetMapping("/{code}")
  public ApiResponse<DictTypeResp> getByCode(@PathVariable String code) {
    return ApiResponse.success(
        dictApplicationService.getTypeByCode(code).map(dictAssembler::toResp).orElse(null));
  }

  @Operation(summary = "字典类型分页")
  @GetMapping("/page")
  public ApiResponse<PageResult<DictTypeResp>> page(DictTypePageReq req) {
    return ApiResponse.success(
        dictApplicationService.pageTypes(dictAssembler.toQuery(req)).map(dictAssembler::toResp));
  }

  @Operation(summary = "该值域已有的层级视图编码（含 DEFAULT）")
  @GetMapping("/{code}/hierarchies")
  public ApiResponse<List<String>> hierarchies(@PathVariable String code) {
    return ApiResponse.success(dictApplicationService.listHierarchies(code));
  }

  @Operation(summary = "枚举漂移检查（不写库）")
  @GetMapping("/{code}/enum-diff")
  public ApiResponse<DictEnumDiffResp> enumDiff(@PathVariable String code) {
    DictEnumDiffDto diff = dictApplicationService.enumDiff(code);
    return ApiResponse.success(
        DictEnumDiffResp.builder()
            .typeCode(diff.getTypeCode())
            .enumClass(diff.getEnumClass())
            .missingInDict(diff.getMissingInDict())
            .missingInEnum(diff.getMissingInEnum())
            .valueDrift(diff.getValueDrift())
            .consistent(diff.isConsistent())
            .build());
  }

  @Operation(summary = "按绑定枚举同步字典项（幂等，不覆盖人工文案）")
  @PostMapping("/{code}/enum-sync")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Integer> enumSync(@PathVariable String code) {
    return ApiResponse.success(dictApplicationService.syncEnum(code));
  }

  @Operation(summary = "导出行值域快照")
  @GetMapping("/{code}/export")
  public ApiResponse<DictExportResp> export(@PathVariable String code) {
    return ApiResponse.success(
        dictApplicationService.exportType(code).map(dictAssembler::toResp).orElse(null));
  }

  @Operation(summary = "导入值域快照（按编码 upsert）")
  @PostMapping("/{code}/import")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Integer> importSnapshot(
      @PathVariable String code, @RequestBody DictExportResp payload) {
    return ApiResponse.success(
        dictApplicationService.importType(code, dictAssembler.toDto(payload)));
  }
}
