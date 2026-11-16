package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.ChangeRequestApplicationService;
import com.bone.masterdata.application.command.ApplyChangeRequestCommand;
import com.bone.masterdata.application.command.CreateChangeRequestCommand;
import com.bone.masterdata.application.command.DecideSegmentCommand;
import com.bone.masterdata.application.command.SubmitChangeRequestCommand;
import com.bone.masterdata.application.query.dto.ChangeRequestStateDTO;
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
 * 分段并行变更请求治理 API。
 *
 * <p>与编排服务同受 {@code bone.masterdata.change-request.enabled=true} 控制：生产端口未就绪时端点不注册。
 * 响应只含状态、段路由与裁决账号，不回业务字段值 / PII。租户一律取 JWT 登录态，不接受自报租户； 操作人必须与登录账号一致（编排层 {@code requireActor}
 * 硬校验），不接受请求体代签。
 *
 * <p><b>权限分离</b>：写路径 {@code masterdata:governance:write}，读路径 {@code masterdata:governance:read}；
 * SoD 的实质校验在领域内核与编排层，注解只是第一道闸门，去掉注解也不能绕过状态机。
 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/change-requests")
@ConditionalOnProperty(
    prefix = "bone.masterdata.change-request",
    name = "enabled",
    havingValue = "true")
public class ChangeRequestController {

  private final ChangeRequestApplicationService service;

  public ChangeRequestController(ChangeRequestApplicationService service) {
    this.service = service;
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping
  public ApiResponse<ChangeRequestStateDTO> create(
      @Valid @RequestBody CreateChangeRequestCommand cmd) {
    return ApiResponse.success(service.create(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/{id}/submit")
  public ApiResponse<ChangeRequestStateDTO> submit(
      @PathVariable String id, @Valid @RequestBody SubmitChangeRequestCommand cmd) {
    cmd.setChangeRequestId(id);
    return ApiResponse.success(service.submit(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/{id}/segments/{segment}/decide")
  public ApiResponse<ChangeRequestStateDTO> decide(
      @PathVariable String id,
      @PathVariable String segment,
      @Valid @RequestBody DecideSegmentCommand cmd) {
    cmd.setChangeRequestId(id);
    cmd.setSegment(segment);
    return ApiResponse.success(service.decide(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/{id}/apply")
  public ApiResponse<ChangeRequestStateDTO> apply(
      @PathVariable String id, @Valid @RequestBody ApplyChangeRequestCommand cmd) {
    cmd.setChangeRequestId(id);
    return ApiResponse.success(service.apply(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:governance:read')")
  @GetMapping("/{id}")
  public ApiResponse<ChangeRequestStateDTO> current(@PathVariable String id) {
    Optional<ChangeRequestStateDTO> state = service.currentForCaller(id);
    return ApiResponse.success(state.orElse(null));
  }
}
