package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.converter.DictWebConverter;
import com.bone.system.adapter.web.dto.request.CreateDictReq;
import com.bone.system.adapter.web.dto.request.UpdateDictReq;
import com.bone.system.adapter.web.dto.response.DictResp;
import com.bone.system.application.command.cmd.DeleteDictCommand;
import com.bone.system.application.command.handler.DictCommandHandler;
import com.bone.system.application.query.dto.DictDTO;
import com.bone.system.application.query.handler.DictQueryHandler;
import com.bone.system.application.query.qry.DictByTypeQuery;
import com.bone.system.application.query.qry.DictPageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 系统字典控制器 */
@Tag(name = "系统字典", description = "系统字典管理接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/dicts")
@RequiredArgsConstructor
public class DictController {

  private final DictCommandHandler dictCommandHandler;
  private final DictQueryHandler dictQueryHandler;
  private final DictWebConverter dictWebConverter;

  @Operation(summary = "创建字典项")
  @PostMapping
  public ApiResponse<Long> create(@Valid @RequestBody CreateDictReq req) {
    return ApiResponse.success(dictCommandHandler.create(dictWebConverter.toCommand(req)));
  }

  @Operation(summary = "更新字典项")
  @PutMapping("/{id}")
  public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateDictReq req) {
    dictCommandHandler.update(dictWebConverter.toCommand(id, req));
    return ApiResponse.success();
  }

  @Operation(summary = "删除字典项")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    dictCommandHandler.delete(new DeleteDictCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "按类型查询字典列表")
  @GetMapping("/type/{type}")
  public ApiResponse<List<DictResp>> listByType(@PathVariable String type) {
    DictByTypeQuery qry = new DictByTypeQuery();
    qry.setType(type);
    return ApiResponse.success(
        dictQueryHandler.listByType(qry).stream()
            .map(dictWebConverter::toResp)
            .collect(Collectors.toList()));
  }

  @Operation(summary = "按类型与编码查询字典")
  @GetMapping("/{type}/{code}")
  public ApiResponse<DictResp> getByTypeAndCode(
      @PathVariable String type, @PathVariable String code) {
    DictDTO dto = dictQueryHandler.getByTypeAndCode(type, code);
    return ApiResponse.success(dto != null ? dictWebConverter.toResp(dto) : null);
  }

  @Operation(summary = "分页查询字典")
  @GetMapping("/page")
  public ApiResponse<PageResult<DictResp>> page(DictPageQuery qry) {
    return ApiResponse.success(dictQueryHandler.page(qry).map(dictWebConverter::toResp));
  }
}
