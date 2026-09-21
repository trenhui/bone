package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.MasterDataRecordWebConverter;
import com.bone.masterdata.adapter.web.dto.request.CreateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateMasterDataRecordReq;
import com.bone.masterdata.application.RecordApplicationService;
import com.bone.masterdata.application.command.cmd.CreateMasterDataRecordCommand;
import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCommand;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataRecordCommand;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQuery;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/entities")
@RequiredArgsConstructor
public class MasterDataRecordController {

  private final RecordApplicationService recordService;
  private final MasterDataRecordWebConverter converter;

  @PostMapping("/{entityId}/records")
  public ApiResponse<Long> create(
      @PathVariable Long entityId, @RequestBody CreateMasterDataRecordReq req) {
    req.setMasterDataEntityId(entityId);
    CreateMasterDataRecordCommand cmd = converter.toCommand(req);
    Long id = recordService.create(cmd);
    return ApiResponse.success(id);
  }

  @GetMapping("/{entityId}/records")
  public ApiResponse<PageResult<MasterDataRecordDTO>> list(
      @PathVariable Long entityId, MasterDataRecordListQuery qry) {
    qry.setMasterDataEntityId(entityId);
    if (qry.getPageNum() <= 0) {
      qry.setPageNum(1);
    }
    if (qry.getPageSize() <= 0) {
      qry.setPageSize(10);
    }
    PageResult<MasterDataRecordDTO> result = recordService.list(qry);
    return ApiResponse.success(result);
  }

  @GetMapping("/records/{id}")
  public ApiResponse<MasterDataRecordDTO> detail(@PathVariable Long id) {
    MasterDataRecordByIdQuery qry = MasterDataRecordByIdQuery.builder().id(id).build();
    MasterDataRecordDTO dto = recordService.detail(qry);
    return ApiResponse.success(dto);
  }

  @PutMapping("/records/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @RequestBody UpdateMasterDataRecordReq req) {
    UpdateMasterDataRecordCommand cmd = converter.toCommand(id, req);
    recordService.update(cmd);
    return ApiResponse.success();
  }

  @DeleteMapping("/records/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    recordService.delete(id);
    return ApiResponse.success();
  }

  @PostMapping("/{entityId}/records/import")
  public ApiResponse<List<Long>> importRecords(
      @PathVariable Long entityId, @RequestParam MultipartFile file) {
    try {
      ImportMasterDataRecordsCommand cmd = new ImportMasterDataRecordsCommand();
      cmd.setMasterDataEntityId(entityId);
      cmd.setOriginalFilename(file.getOriginalFilename());
      cmd.setDataStream(file.getInputStream());
      List<Long> ids = recordService.importRecords(cmd);
      return ApiResponse.success(ids);
    } catch (IOException e) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.FILE_READ_FAILED, "读取上传文件失败: " + e.getMessage(), e);
    }
  }

  @PostMapping("/records/{id}/publish")
  public ApiResponse<Void> publish(@PathVariable Long id) {
    recordService.publish(id);
    return ApiResponse.success();
  }

  @GetMapping("/{entityId}/records/export")
  public ApiResponse<String> export(@PathVariable Long entityId) {
    return ApiResponse.success("导出成功", recordService.export(entityId));
  }
}
