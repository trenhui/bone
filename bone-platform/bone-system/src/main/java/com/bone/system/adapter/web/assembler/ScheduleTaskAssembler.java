package com.bone.system.adapter.web.assembler;

import com.bone.system.adapter.web.dto.request.CreateScheduleTaskReq;
import com.bone.system.adapter.web.dto.request.ScheduleTaskPageReq;
import com.bone.system.adapter.web.dto.request.UpdateScheduleTaskReq;
import com.bone.system.adapter.web.dto.response.ScheduleTaskResp;
import com.bone.system.application.command.CreateScheduleTaskCommand;
import com.bone.system.application.command.UpdateScheduleTaskCommand;
import com.bone.system.application.query.dto.ScheduleTaskDto;
import com.bone.system.application.query.qry.ScheduleTaskPageQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 定时任务的 HTTP ↔ application 转换边界。 */
@Mapper
public interface ScheduleTaskAssembler {

  CreateScheduleTaskCommand toCommand(CreateScheduleTaskReq req);

  @Mapping(target = "id", source = "id")
  UpdateScheduleTaskCommand toCommand(Long id, UpdateScheduleTaskReq req);

  ScheduleTaskPageQuery toQuery(ScheduleTaskPageReq req);

  ScheduleTaskResp toResp(ScheduleTaskDto dto);
}
