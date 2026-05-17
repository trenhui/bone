package com.bone.system.adapter.web.converter;

import com.bone.system.adapter.web.dto.req.CreateLogReq;
import com.bone.system.adapter.web.dto.req.LogPageReq;
import com.bone.system.adapter.web.dto.resp.LogResp;
import com.bone.system.application.command.cmd.CreateLogCmd;
import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.query.qry.LogPageQry;
import org.mapstruct.Mapper;

/**
 * 日志Web转换器
 */
@Mapper
public interface LogWebConverter {

    CreateLogCmd toCmd(CreateLogReq req);

    LogPageQry toQry(LogPageReq req);

    LogResp toResp(LogDTO dto);
}
