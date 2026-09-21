package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.assembler.ScheduleTaskAssembler;
import com.bone.system.adapter.web.dto.request.CreateScheduleTaskReq;
import com.bone.system.adapter.web.dto.request.ScheduleTaskPageReq;
import com.bone.system.adapter.web.dto.request.UpdateScheduleTaskReq;
import com.bone.system.adapter.web.dto.response.ScheduleTaskResp;
import com.bone.system.application.ScheduleTaskApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 系统定时任务控制器。 */
@Tag(name = "系统定时任务", description = "系统定时任务管理接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/schedule-tasks")
@RequiredArgsConstructor
public class ScheduleTaskController {

  private final ScheduleTaskApplicationService scheduleTaskApplicationService;
  private final ScheduleTaskAssembler scheduleTaskAssembler;

  @Operation(summary = "创建定时任务")
  @PostMapping
  public ApiResponse<Long> create(@Valid @RequestBody CreateScheduleTaskReq req) {
    return ApiResponse.success(
        scheduleTaskApplicationService.create(scheduleTaskAssembler.toCommand(req)));
  }

  @Operation(summary = "更新定时任务")
  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateScheduleTaskReq req) {
    scheduleTaskApplicationService.update(scheduleTaskAssembler.toCommand(id, req));
    return ApiResponse.success();
  }

  @Operation(summary = "删除定时任务")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    scheduleTaskApplicationService.delete(id);
    return ApiResponse.success();
  }

  @Operation(summary = "启用/停用定时任务")
  @PutMapping("/{id}/toggle")
  public ApiResponse<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
    scheduleTaskApplicationService.toggle(id, enabled);
    return ApiResponse.success();
  }

  @Operation(summary = "查询定时任务详情")
  @GetMapping("/{id}")
  public ApiResponse<ScheduleTaskResp> getById(@PathVariable Long id) {
    return ApiResponse.success(
        scheduleTaskApplicationService.getById(id).map(scheduleTaskAssembler::toResp).orElse(null));
  }

  @Operation(summary = "分页查询定时任务")
  @GetMapping("/page")
  public ApiResponse<PageResult<ScheduleTaskResp>> page(ScheduleTaskPageReq req) {
    return ApiResponse.success(
        scheduleTaskApplicationService
            .page(scheduleTaskAssembler.toQuery(req))
            .map(scheduleTaskAssembler::toResp));
  }
}
