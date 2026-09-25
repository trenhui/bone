package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.MasterDataRecordWebConverter;
import com.bone.masterdata.adapter.web.dto.request.CreateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateMasterDataRecordReq;
import com.bone.masterdata.application.RecordApplicationService;
import com.bone.masterdata.application.command.ImportMasterDataRecordsCommand;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQuery;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/records")
@RequiredArgsConstructor
public class MasterDataRecordTopController {

  private final RecordApplicationService recordService;
  private final MasterDataRecordWebConverter converter;

  @GetMapping
  public ApiResponse<PageResult<MasterDataRecordDTO>> list(MasterDataRecordListQuery qry) {
    if (qry.getPageNum() <= 0) {
      qry.setPageNum(1);
    }
    if (qry.getPageSize() <= 0) {
      qry.setPageSize(10);
    }
    return ApiResponse.success(recordService.list(qry));
  }

  @GetMapping("/{id}")
  public ApiResponse<MasterDataRecordDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(
        recordService.detail(MasterDataRecordByIdQuery.builder().id(id).build()));
  }

  @PostMapping("/entity/{masterDataEntityId}")
  public ApiResponse<Long> create(
      @PathVariable Long masterDataEntityId, @Valid @RequestBody CreateMasterDataRecordReq req) {
    req.setMasterDataEntityId(masterDataEntityId);
    return ApiResponse.success(recordService.create(converter.toCommand(req)));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateMasterDataRecordReq req) {
    recordService.update(converter.toCommand(id, req));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    recordService.delete(id);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/publish")
  public ApiResponse<Void> publish(@PathVariable Long id) {
    recordService.publish(id);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/archive")
  public ApiResponse<Void> archive(@PathVariable Long id) {
    recordService.archive(id);
    return ApiResponse.success();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<List<Long>> importRecords(
      @RequestParam(value = "masterDataEntityId", required = false) Long masterDataEntityId,
      @RequestParam MultipartFile file) {
    try {
      ImportMasterDataRecordsCommand cmd = new ImportMasterDataRecordsCommand();
      cmd.setMasterDataEntityId(masterDataEntityId);
      cmd.setOriginalFilename(file.getOriginalFilename());
      cmd.setDataStream(file.getInputStream());
      return ApiResponse.success(recordService.importRecords(cmd));
    } catch (IOException e) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.FILE_READ_FAILED, "读取上传文件失败: " + e.getMessage(), e);
    }
  }

  @GetMapping("/export")
  public ApiResponse<String> export(
      @RequestParam(value = "masterDataEntityId", required = false) Long masterDataEntityId) {
    return ApiResponse.success("导出成功", recordService.export(masterDataEntityId));
  }
}
