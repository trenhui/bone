package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.GoldenRecordApplicationService;
import com.bone.masterdata.application.command.ResolveAndMergeCommand;
import com.bone.masterdata.application.command.RollbackMergeCommand;
import com.bone.masterdata.application.query.dto.GoldenRecordMergeResultDTO;
import com.bone.masterdata.application.query.dto.GoldenRecordStateDTO;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Golden Record 平台治理 API（身份集中、视图联邦）。
 *
 * <p>与编排服务同受 {@code bone.masterdata.golden-record.enabled=true} 控制：生产端口未就绪时端点不注册，
 * 不对外暴露一个「能调用但落不了库」的假能力。API 只返回裁决、版本与摘要，不回字段值。
 *
 * <p><b>租户隔离</b>：租户一律取 JWT 登录态，不接受 {@code tenantId} 请求参数；命令里自带的 tenantId 也会 在编排层与登录态比对，不一致即
 * fail-closed。
 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/golden-records")
@ConditionalOnProperty(
    prefix = "bone.masterdata.golden-record",
    name = "enabled",
    havingValue = "true")
public class GoldenRecordController {

  private final GoldenRecordApplicationService orchestration;

  public GoldenRecordController(GoldenRecordApplicationService orchestration) {
    this.orchestration = orchestration;
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/merge")
  public ApiResponse<GoldenRecordMergeResultDTO> merge(
      @Valid @RequestBody ResolveAndMergeCommand cmd) {
    return ApiResponse.success(orchestration.resolveAndMerge(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/rollback")
  public ApiResponse<GoldenRecordMergeResultDTO> rollback(
      @Valid @RequestBody RollbackMergeCommand cmd) {
    return ApiResponse.success(orchestration.rollback(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:governance:read')")
  @GetMapping("/{partyId}")
  public ApiResponse<GoldenRecordStateDTO> current(@PathVariable String partyId) {
    Optional<GoldenRecordStateDTO> state = orchestration.currentForCaller(partyId);
    return ApiResponse.success(state.orElse(null));
  }
}
