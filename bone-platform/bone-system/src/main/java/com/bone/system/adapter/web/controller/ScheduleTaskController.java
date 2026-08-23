package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.converter.ScheduleTaskWebConverter;
import com.bone.system.adapter.web.dto.req.CreateScheduleTaskReq;
import com.bone.system.adapter.web.dto.req.UpdateScheduleTaskReq;
import com.bone.system.adapter.web.dto.resp.ScheduleTaskResp;
import com.bone.system.application.command.cmd.DeleteScheduleTaskCommand;
import com.bone.system.application.command.cmd.ToggleScheduleTaskCommand;
import com.bone.system.application.command.handler.ScheduleTaskCommandHandler;
import com.bone.system.application.query.dto.ScheduleTaskDTO;
import com.bone.system.application.query.handler.ScheduleTaskQueryHandler;
import com.bone.system.application.query.qry.ScheduleTaskPageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 系统定时任务控制器 */
@Tag(name = "系统定时任务", description = "系统定时任务管理接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/schedule-tasks")
@RequiredArgsConstructor
public class ScheduleTaskController {

  private final ScheduleTaskCommandHandler scheduleTaskCommandHandler;
  private final ScheduleTaskQueryHandler scheduleTaskQueryHandler;
  private final ScheduleTaskWebConverter scheduleTaskWebConverter;

  @Operation(summary = "创建定时任务")
  @PostMapping
  public ApiResponse<Long> create(@Valid @RequestBody CreateScheduleTaskReq req) {
    return ApiResponse.success(
        scheduleTaskCommandHandler.create(scheduleTaskWebConverter.toCommand(req)));
  }

  @Operation(summary = "更新定时任务")
  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateScheduleTaskReq req) {
    scheduleTaskCommandHandler.update(scheduleTaskWebConverter.toCommand(id, req));
    return ApiResponse.success();
  }

  @Operation(summary = "删除定时任务")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    scheduleTaskCommandHandler.delete(new DeleteScheduleTaskCommand(id));
    return ApiResponse.success();
  }

  @Operation(summary = "启用/停用定时任务")
  @PutMapping("/{id}/toggle")
  public ApiResponse<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
    ToggleScheduleTaskCommand cmd = new ToggleScheduleTaskCommand();
    cmd.setId(id);
    cmd.setEnabled(enabled);
    scheduleTaskCommandHandler.toggle(cmd);
    return ApiResponse.success();
  }

  @Operation(summary = "查询定时任务详情")
  @GetMapping("/{id}")
  public ApiResponse<ScheduleTaskResp> getById(@PathVariable Long id) {
    ScheduleTaskDTO dto = scheduleTaskQueryHandler.getById(id);
    return ApiResponse.success(dto != null ? scheduleTaskWebConverter.toResp(dto) : null);
  }

  @Operation(summary = "分页查询定时任务")
  @GetMapping("/page")
  public ApiResponse<PageResult<ScheduleTaskResp>> page(ScheduleTaskPageQuery qry) {
    return ApiResponse.success(
        scheduleTaskQueryHandler.page(qry).map(scheduleTaskWebConverter::toResp));
  }
}
