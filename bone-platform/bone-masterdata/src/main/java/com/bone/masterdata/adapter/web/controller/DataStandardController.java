package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.DataStandardWebConverter;
import com.bone.masterdata.adapter.web.dto.request.CreateDataStandardReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateDataStandardReq;
import com.bone.masterdata.adapter.web.dto.response.DataStandardResp;
import com.bone.masterdata.application.StandardApplicationService;
import com.bone.masterdata.application.command.cmd.DeleteDataStandardCommand;
import com.bone.masterdata.application.query.qry.DataStandardPageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 主数据标准控制器 */
@Tag(name = "数据标准", description = "主数据标准管理接口")
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/data-standards")
@RequiredArgsConstructor
public class DataStandardController {

  private final StandardApplicationService standardService;
  private final DataStandardWebConverter dataStandardWebConverter;

  @Operation(summary = "创建数据标准")
  @PostMapping
  public ApiResponse<Long> create(@Valid @RequestBody CreateDataStandardReq req) {
    return ApiResponse.success(standardService.create(dataStandardWebConverter.toCommand(req)));
  }

  @Operation(summary = "更新数据标准")
  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateDataStandardReq req) {
    standardService.update(dataStandardWebConverter.toCommand(id, req));
    return ApiResponse.success();
  }

  @Operation(summary = "删除数据标准")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    standardService.delete(new DeleteDataStandardCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "按实体查询数据标准")
  @GetMapping("/entity/{entityCode}")
  public ApiResponse<List<DataStandardResp>> listByEntity(@PathVariable String entityCode) {
    return ApiResponse.success(
        standardService.listByEntity(entityCode).stream()
            .map(dataStandardWebConverter::toResp)
            .collect(Collectors.toList()));
  }

  @Operation(summary = "分页查询数据标准")
  @GetMapping("/page")
  public ApiResponse<PageResult<DataStandardResp>> page(DataStandardPageQuery qry) {
    return ApiResponse.success(standardService.page(qry).map(dataStandardWebConverter::toResp));
  }
}
