package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.studio.generator.application.RepairLegacyEntityNameApplicationService;
import com.bone.studio.generator.application.command.cmd.RepairLegacyEntityNameCommand;
import com.bone.studio.generator.application.dto.RepairEntityNameResult;
import com.bone.studio.generator.common.GeneratorApiPaths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维管理端点（一次性修复等）。
 *
 * <p>所有写操作均为显式、幂等、可预览；不存在隐性数据变更。
 */
@RestController
@RequestMapping(GeneratorApiPaths.ADMIN)
@RequiredArgsConstructor
@Slf4j
public class StudioGeneratorAdminController {

  private final RepairLegacyEntityNameApplicationService repairService;

  /**
   * 将存量未转驼峰的 {@code custom_entity_name} 收敛为 PascalCase。
   *
   * <p>默认 execute=false 仅预览；带 {@code {"execute":true}} 才真正写入。幂等，可重复执行。 仅修正 customEntityName ==
   * originalTableName 的行，用户已自定义的行不受影响。
   */
  @PreAuthorize("hasAuthority('generator:admin:write')")
  @PostMapping("/repair-entity-names")
  public ApiResponse<RepairEntityNameResult> repairEntityNames(
      @RequestBody(required = false) RepairLegacyEntityNameCommand command) {
    if (command == null) {
      command = new RepairLegacyEntityNameCommand();
    }
    RepairEntityNameResult result = repairService.handle(command);
    String message = result.isExecuted() ? "修复完成" : "预览完成（未执行，传 execute=true 以执行）";
    return ApiResponse.success(message, result);
  }
}
