package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.metadata.catalog.application.DictBindingToggle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典绑定灰度开关端点（界面设计器字典打通 v1.1 §6.5）：查看当前租户判定、即时开/关、撤销覆盖。
 *
 * <p><b>为什么需要管理端点而不只是配置</b>：绑定解析故障时必须能<b>秒级拉闸</b>—— 改配置要等配置中心推送 + 实例刷新，黑屏期间的表单错误已经发生。运行时覆盖是
 * 「临时拉闸」通道，持久灰度名单仍在配置里（{@code enabled-tenants}）。
 *
 * <p><b>权限</b>：读状态不挂 {@code @PreAuthorize}（同 {@code MetaFieldOptionsController} 读端点姿态，
 * 便于任意租户自查自己是否被开放）；开/关是治理写动作，挂 {@code metadata:model:write}（字段建模同码，未在 IAM 目录登记新码，避免所有调用方 403）。
 */
@RestController
@RequestMapping("/api/v1/metadata/dict-binding/toggle")
@RequiredArgsConstructor
@Tag(name = "字典绑定灰度", description = "按租户查看/开关字典绑定能力")
public class DictBindingToggleController {

  private final DictBindingToggle dictBindingToggle;

  @GetMapping
  @Operation(
      summary = "查看当前租户灰度判定",
      description = "返回 enabled（是否开放）与 source（runtime / tenant-config / global-default）")
  public ApiResponse<DictBindingToggle.ToggleState> state() {
    return ApiResponse.success(dictBindingToggle.currentState());
  }

  /**
   * 运行时覆盖当前租户（进程内生效，重启失效）。
   *
   * <p><b>诚实边界</b>：这不是持久化配置——集群下只影响本实例；需要稳定灰度请改 {@code
   * bone.metadata.dict-binding.enabled-tenants}。此端点定位是「临时拉闸/单租户验证」。
   */
  @PutMapping
  @Operation(summary = "运行时开关当前租户", description = "进程内覆盖（重启失效），用于临时拉闸与单租户验证；持久灰度请改配置")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ApiResponse<DictBindingToggle.ToggleState> override(
      @RequestParam("enabled") boolean enabled) {
    return ApiResponse.success(dictBindingToggle.overrideCurrentTenant(enabled));
  }

  @DeleteMapping
  @Operation(summary = "撤销运行时覆盖", description = "清除当前租户的进程内覆盖，回落到配置判定")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ApiResponse<DictBindingToggle.ToggleState> clearOverride() {
    return ApiResponse.success(dictBindingToggle.clearCurrentTenantOverride());
  }
}
