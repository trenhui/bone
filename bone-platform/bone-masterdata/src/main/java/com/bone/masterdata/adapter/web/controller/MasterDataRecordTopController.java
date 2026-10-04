package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.adapter.web.converter.MasterDataRecordWebConverter;
import com.bone.masterdata.adapter.web.dto.request.CreateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.request.UpdateMasterDataRecordReq;
import com.bone.masterdata.adapter.web.dto.response.MasterDataRecordVersionResp;
import com.bone.masterdata.application.RecordApplicationService;
import com.bone.masterdata.application.command.ImportMasterDataRecordsCommand;
import com.bone.masterdata.application.query.dto.ImportResultDTO;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
    if (qry.getPage() <= 0) {
      qry.setPage(1);
    }
    if (qry.getSize() <= 0) {
      qry.setSize(10);
    }
    return ApiResponse.success(recordService.list(qry));
  }

  @GetMapping("/{id}")
  public ApiResponse<MasterDataRecordDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(
        recordService.detail(MasterDataRecordByIdQuery.builder().id(id).build()));
  }

  /**
   * 按业务编码点查当前生效记录（下游按业务键消费的主路径）。
   *
   * <p>只返回「已发布 + 当前版本 + 生效窗口含此刻」的记录；未命中（不存在/未发布/已过期） data 为 null，调用方无需区分失败原因。避免消费方为查一条记录整实体全量拉取。
   */
  @GetMapping("/by-code")
  public ApiResponse<MasterDataRecordDTO> byCode(
      @RequestParam("masterDataEntityId") Long masterDataEntityId,
      @RequestParam("code") String code) {
    return ApiResponse.success(
        recordService.findByBusinessKey(masterDataEntityId, code).orElse(null));
  }

  @PreAuthorize("hasAuthority('masterdata:records:write')")
  @PostMapping("/entity/{masterDataEntityId}")
  public ApiResponse<Long> create(
      @PathVariable Long masterDataEntityId, @Valid @RequestBody CreateMasterDataRecordReq req) {
    req.setMasterDataEntityId(masterDataEntityId);
    return ApiResponse.success(recordService.create(converter.toCommand(req)));
  }

  @PreAuthorize("hasAuthority('masterdata:records:write')")
  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateMasterDataRecordReq req) {
    recordService.update(converter.toCommand(id, req));
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:records:write')")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    recordService.delete(id);
    return ApiResponse.success();
  }

  /**
   * 发布记录（DRAFT → PUBLISHED，生效当前版本）。
   *
   * <p>复用 {@code masterdata:records:write}（权限目录已把「发布」登记在该码的职责内）： 审批与发布的职责分离已由 {@code
   * masterdata:records:approve} + 领域/应用层门禁保证 —— 启用了审批流的实体，{@code RecordApplicationService#publish}
   * 会拒绝非 APPROVED 记录。 再拆一个 publish 码不产生额外隔离：能审批的人同时持有 write 时本就自审自发布。
   */
  @PreAuthorize("hasAuthority('masterdata:records:write')")
  @PostMapping("/{id}/publish")
  public ApiResponse<Void> publish(@PathVariable Long id) {
    recordService.publish(id);
    return ApiResponse.success();
  }

  /** 提交审批（UC-T7）。 */
  @PreAuthorize("hasAuthority('masterdata:records:write')")
  @PostMapping("/{id}/submit")
  public ApiResponse<Void> submitForApproval(@PathVariable Long id) {
    recordService.submitForApproval(id);
    return ApiResponse.success();
  }

  /** 审批通过（UC-T7，SoD：审批人≠提交人）。body 可为 {"comment": "..."}。 */
  @PreAuthorize("hasAuthority('masterdata:records:approve')")
  @PostMapping("/{id}/approve")
  public ApiResponse<Void> approve(
      @PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
    recordService.approve(id, body == null ? null : body.get("comment"));
    return ApiResponse.success();
  }

  /** 审批驳回（UC-T7，退回草稿）。body 可为 {"comment": "..."}。 */
  @PreAuthorize("hasAuthority('masterdata:records:approve')")
  @PostMapping("/{id}/reject")
  public ApiResponse<Void> reject(
      @PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
    recordService.reject(id, body == null ? null : body.get("comment"));
    return ApiResponse.success();
  }

  /** 版本历史（UC-T7 追溯）。返回 DTO 而非领域实体，避免领域模型泄漏到 API 响应体。 */
  @GetMapping("/{id}/versions")
  public ApiResponse<List<MasterDataRecordVersionResp>> versions(@PathVariable Long id) {
    return ApiResponse.success(converter.toVersionRespList(recordService.versions(id)));
  }

  // archive 是记录自身的状态流转（非审批动作），与 approve/reject 分属不同语义，故取 records:write。
  @PreAuthorize("hasAuthority('masterdata:records:write')")
  @PostMapping("/{id}/archive")
  public ApiResponse<Void> archive(@PathVariable Long id) {
    recordService.archive(id);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:records:write')")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<ImportResultDTO> importRecords(
      @RequestParam(value = "masterDataEntityId", required = false) Long masterDataEntityId,
      @RequestParam(value = "duplicateStrategy", required = false) String duplicateStrategy,
      @RequestParam MultipartFile file) {
    try {
      ImportMasterDataRecordsCommand cmd = new ImportMasterDataRecordsCommand();
      cmd.setMasterDataEntityId(masterDataEntityId);
      cmd.setDuplicateStrategy(duplicateStrategy);
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
