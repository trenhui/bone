package com.bone.system.adapter.web.converter;

import com.bone.system.adapter.web.dto.request.CreateLogReq;
import com.bone.system.adapter.web.dto.request.LogPageReq;
import com.bone.system.adapter.web.dto.response.LogResp;
import com.bone.system.application.command.cmd.CreateLogCommand;
import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.query.qry.LogPageQuery;
import org.mapstruct.Mapper;

/** 日志Web转换器 */
@Mapper
public interface LogWebConverter {

  CreateLogCommand toCommand(CreateLogReq req);

  LogPageQuery toQuery(LogPageReq req);

  LogResp toResp(LogDTO dto);
}
