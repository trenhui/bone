package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.dto.request.RecordLineageReq;
import com.bone.masterdata.application.LineageApplicationService;
import com.bone.masterdata.application.command.cmd.RecordLineageCommand;
import com.bone.masterdata.application.query.dto.LineageRecordDTO;
import com.bone.masterdata.application.query.qry.LineageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 数据血缘控制器 */
@Tag(name = "数据血缘", description = "数据血缘采集与查询接口")
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/lineage")
@RequiredArgsConstructor
public class LineageController {

  private final LineageApplicationService lineageService;

  @Operation(summary = "记录数据血缘边")
  @PostMapping
  public ApiResponse<Void> record(@Valid @RequestBody RecordLineageReq req) {
    RecordLineageCommand cmd = new RecordLineageCommand();
    cmd.setSourceEntity(req.getSourceEntity());
    cmd.setSourceField(req.getSourceField());
    cmd.setTransformType(req.getTransformType());
    cmd.setTargetEntity(req.getTargetEntity());
    cmd.setTargetField(req.getTargetField());
    cmd.setSchemaName(req.getSchemaName());
    lineageService.record(cmd);
    return ApiResponse.success();
  }

  @Operation(summary = "查询血缘边（按 target 查上游 / source 查下游）")
  @GetMapping
  public ApiResponse<List<LineageRecordDTO>> list(LineageQuery qry) {
    return ApiResponse.success(lineageService.list(qry));
  }
}
