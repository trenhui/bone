package com.bone.masterdata.adapter.web.controller;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.MasterDataRecordWebConverter;
import com.bone.masterdata.adapter.web.dto.request.CreateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateMasterDataRecordReq;
import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCommand;
import com.bone.masterdata.application.command.handler.ArchiveMasterDataRecordHandler;
import com.bone.masterdata.application.command.handler.CreateMasterDataRecordHandler;
import com.bone.masterdata.application.command.handler.DeleteMasterDataRecordHandler;
import com.bone.masterdata.application.command.handler.ImportMasterDataRecordsHandler;
import com.bone.masterdata.application.command.handler.PublishMasterDataRecordHandler;
import com.bone.masterdata.application.command.handler.UpdateMasterDataRecordHandler;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.handler.ExportMasterDataRecordsQueryHandler;
import com.bone.masterdata.application.query.handler.MasterDataRecordDetailQueryHandler;
import com.bone.masterdata.application.query.handler.MasterDataRecordListQueryHandler;
import com.bone.masterdata.application.query.qry.MasterDataRecordByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQuery;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/records")
@RequiredArgsConstructor
public class MasterDataRecordTopController {
  private final CreateMasterDataRecordHandler createMasterDataRecordHandler;
  private final UpdateMasterDataRecordHandler updateMasterDataRecordHandler;
  private final ImportMasterDataRecordsHandler importMasterDataRecordsHandler;
  private final MasterDataRecordListQueryHandler masterDataRecordListQueryHandler;
  private final MasterDataRecordDetailQueryHandler masterDataRecordDetailQueryHandler;
  private final PublishMasterDataRecordHandler publishMasterDataRecordHandler;
  private final ExportMasterDataRecordsQueryHandler exportMasterDataRecordsQueryHandler;
  private final DeleteMasterDataRecordHandler deleteMasterDataRecordHandler;
  private final ArchiveMasterDataRecordHandler archiveMasterDataRecordHandler;
  private final MasterDataRecordWebConverter converter;

  @GetMapping
  public ApiResponse<PageResult<MasterDataRecordDTO>> list(MasterDataRecordListQuery qry) {
    if (qry.getPageNum() <= 0) {
      qry.setPageNum(1);
    }
    if (qry.getPageSize() <= 0) {
      qry.setPageSize(10);
    }
    return ApiResponse.success(masterDataRecordListQueryHandler.handle(qry));
  }

  @GetMapping("/{id}")
  public ApiResponse<MasterDataRecordDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(
        masterDataRecordDetailQueryHandler.handle(
            MasterDataRecordByIdQuery.builder().id(id).build()));
  }

  @PostMapping("/entity/{masterDataEntityId}")
  public ApiResponse<Long> create(
      @PathVariable Long masterDataEntityId, @RequestBody CreateMasterDataRecordReq req) {
    req.setMasterDataEntityId(masterDataEntityId);
    return ApiResponse.success(createMasterDataRecordHandler.handle(converter.toCommand(req)));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @RequestBody UpdateMasterDataRecordReq req) {
    updateMasterDataRecordHandler.handle(converter.toCommand(id, req));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteMasterDataRecordHandler.handle(id);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/publish")
  public ApiResponse<Void> publish(@PathVariable Long id) {
    publishMasterDataRecordHandler.handle(id);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/archive")
  public ApiResponse<Void> archive(@PathVariable Long id) {
    archiveMasterDataRecordHandler.handle(id);
    return ApiResponse.success();
  }

  @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<List<Long>> importRecords(
      @RequestParam(value = "masterDataEntityId", required = false) Long masterDataEntityId,
      @RequestParam MultipartFile file) {
    try {
      ImportMasterDataRecordsCommand cmd = new ImportMasterDataRecordsCommand();
      cmd.setMasterDataEntityId(masterDataEntityId);
      cmd.setOriginalFilename(file.getOriginalFilename());
      cmd.setDataStream(file.getInputStream());
      return ApiResponse.success(importMasterDataRecordsHandler.handle(cmd));
    } catch (IOException e) {
      throw BizException.of(400, "读取上传文件失败: " + e.getMessage(), e);
    }
  }

  @GetMapping("/export")
  public ApiResponse<String> export(
      @RequestParam(value = "masterDataEntityId", required = false) Long masterDataEntityId) {
    return ApiResponse.success(
        "导出成功", exportMasterDataRecordsQueryHandler.handle(masterDataEntityId));
  }
}
