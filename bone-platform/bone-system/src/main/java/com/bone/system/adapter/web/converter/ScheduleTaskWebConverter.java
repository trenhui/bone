package com.bone.system.adapter.web.converter;

import com.bone.system.adapter.web.dto.request.CreateScheduleTaskReq;
import com.bone.system.adapter.web.dto.request.UpdateScheduleTaskReq;
import com.bone.system.adapter.web.dto.response.ScheduleTaskResp;
import com.bone.system.application.command.cmd.CreateScheduleTaskCommand;
import com.bone.system.application.command.cmd.UpdateScheduleTaskCommand;
import com.bone.system.application.query.dto.ScheduleTaskDTO;
import org.springframework.stereotype.Component;

@Component
public class ScheduleTaskWebConverter {

  public CreateScheduleTaskCommand toCommand(CreateScheduleTaskReq req) {
    CreateScheduleTaskCommand cmd = new CreateScheduleTaskCommand();
    cmd.setName(req.getName());
    cmd.setCron(req.getCron());
    cmd.setHandler(req.getHandler());
    cmd.setStatus(req.getStatus());
    return cmd;
  }

  public UpdateScheduleTaskCommand toCommand(Long id, UpdateScheduleTaskReq req) {
    UpdateScheduleTaskCommand cmd = new UpdateScheduleTaskCommand();
    cmd.setId(id);
    cmd.setName(req.getName());
    cmd.setCron(req.getCron());
    cmd.setHandler(req.getHandler());
    return cmd;
  }

  public ScheduleTaskResp toResp(ScheduleTaskDTO dto) {
    return ScheduleTaskResp.builder()
        .id(dto.getId())
        .name(dto.getName())
        .cron(dto.getCron())
        .handler(dto.getHandler())
        .status(dto.getStatus())
        .lastRunAt(dto.getLastRunAt())
        .nextRunAt(dto.getNextRunAt())
        .createdAt(dto.getCreatedAt())
        .updatedAt(dto.getUpdatedAt())
        .build();
  }
}
