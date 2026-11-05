package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.ClaimDictApplicationService;
import com.bone.claim.application.ClaimDicts;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 理赔受控码字典接口（单一真源出口）。
 *
 * <p>前端经 codegen 拉取本端点生成字典模块，消灭手维护双源。只读、无副作用；任意已认证用户可读 （字典是展示元数据，不含业务数据）。
 */
@Tag(name = "智能理赔-受控码字典", description = "受控码 → 中文标签的单一真源，供前端 codegen")
@RestController
@RequestMapping("/api/v1/claim-config/dicts")
@RequiredArgsConstructor
public class ClaimConfigController {

  private final ClaimDictApplicationService claimDictApplicationService;

  @Operation(
      summary = "受控码字典全集",
      description = "返回案件状态 / AI 参与 / 权利 / 消费结果 / 零赔付归因 / 拒赔原因 / 反欺诈闸 / 权限码 的中文标签映射，单一真源")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping
  public ApiResponse<ClaimDicts> dicts() {
    return ApiResponse.success(claimDictApplicationService.dicts());
  }
}
