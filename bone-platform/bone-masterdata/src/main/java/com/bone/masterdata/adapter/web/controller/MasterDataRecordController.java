package com.bone.masterdata.adapter.web.controller;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.MasterDataRecordWebConverter;
import com.bone.masterdata.adapter.web.dto.req.CreateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.req.UpdateMasterDataRecordReq;
import com.bone.masterdata.application.command.cmd.CreateMasterDataRecordCommand;
import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCommand;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataRecordCommand;
import com.bone.masterdata.application.command.handler.CreateMasterDataRecordHandler;
import com.bone.masterdata.application.command.handler.ImportMasterDataRecordsHandler;
import com.bone.masterdata.application.command.handler.PublishMasterDataRecordHandler;
import com.bone.masterdata.application.command.handler.UpdateMasterDataRecordHandler;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.handler.ExportMasterDataRecordsQueryHandler;
import com.bone.masterdata.application.query.handler.MasterDataRecordDetailQueryHandler;
import com.bone.masterdata.application.query.handler.MasterDataRecordListQueryHandler;
import com.bone.masterdata.application.query.qry.MasterDataRecordByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQuery;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/records")
@RequiredArgsConstructor
public class MasterDataRecordController {
  private final CreateMasterDataRecordHandler createMasterDataRecordHandler;
  private final UpdateMasterDataRecordHandler updateMasterDataRecordHandler;
  private final ImportMasterDataRecordsHandler importMasterDataRecordsHandler;
  private final MasterDataRecordListQueryHandler masterDataRecordListQueryHandler;
  private final MasterDataRecordDetailQueryHandler masterDataRecordDetailQueryHandler;
  private final PublishMasterDataRecordHandler publishMasterDataRecordHandler;
  private final ExportMasterDataRecordsQueryHandler exportMasterDataRecordsQueryHandler;
  private final MasterDataRecordWebConverter converter;
  private final MasterDataRecordRepository masterDataRecordRepository;

  @PostMapping
  public ApiResponse<Long> create(@RequestBody CreateMasterDataRecordReq req) {
    CreateMasterDataRecordCommand cmd = converter.toCommand(req);
    Long id = createMasterDataRecordHandler.handle(cmd);
    return ApiResponse.success(id);
  }

  @GetMapping
  public ApiResponse<PageResult<MasterDataRecordDTO>> list(MasterDataRecordListQuery qry) {
    // 分页参数默认值：pageNum 从1开始
    if (qry.getPageNum() <= 0) {
      qry.setPageNum(1);
    }
    if (qry.getPageSize() <= 0) {
      qry.setPageSize(10);
    }
    PageResult<MasterDataRecordDTO> result = masterDataRecordListQueryHandler.handle(qry);
    return ApiResponse.success(result);
  }

  @GetMapping("/{id}")
  public ApiResponse<MasterDataRecordDTO> detail(@PathVariable Long id) {
    MasterDataRecordByIdQuery qry = MasterDataRecordByIdQuery.builder().id(id).build();
    MasterDataRecordDTO dto = masterDataRecordDetailQueryHandler.handle(qry);
    return ApiResponse.success(dto);
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @RequestBody UpdateMasterDataRecordReq req) {
    UpdateMasterDataRecordCommand cmd = converter.toCommand(id, req);
    updateMasterDataRecordHandler.handle(cmd);
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    masterDataRecordRepository.deleteById(id);
    return ApiResponse.success();
  }

  @PostMapping("/import")
  public ApiResponse<List<Long>> importRecords(
      @RequestParam Long masterDataEntityId, @RequestParam MultipartFile file) {
    try {
      ImportMasterDataRecordsCommand cmd = new ImportMasterDataRecordsCommand();
      cmd.setMasterDataEntityId(masterDataEntityId);
      cmd.setOriginalFilename(file.getOriginalFilename());
      cmd.setDataStream(file.getInputStream());
      List<Long> ids = importMasterDataRecordsHandler.handle(cmd);
      return ApiResponse.success(ids);
    } catch (IOException e) {
      throw BizException.of(400, "读取上传文件失败: " + e.getMessage(), e);
    }
  }

  @PostMapping("/{id}/publish")
  public ApiResponse<Void> publish(@PathVariable Long id) {
    publishMasterDataRecordHandler.handle(id);
    return ApiResponse.success();
  }

  @GetMapping("/export")
  public ApiResponse<String> export(@RequestParam("masterDataEntityId") Long masterDataEntityId) {
    return ApiResponse.success(exportMasterDataRecordsQueryHandler.handle(masterDataEntityId));
  }
}
