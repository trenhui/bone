package com.bone.system.adapter.web.converter;

import com.bone.system.adapter.web.dto.req.ConfigPageReq;
import com.bone.system.adapter.web.dto.req.CreateConfigReq;
import com.bone.system.adapter.web.dto.req.UpdateConfigReq;
import com.bone.system.adapter.web.dto.resp.ConfigResp;
import com.bone.system.application.command.cmd.CreateConfigCommand;
import com.bone.system.application.command.cmd.UpdateConfigCommand;
import com.bone.system.application.query.dto.ConfigDTO;
import com.bone.system.application.query.qry.ConfigPageQuery;
import org.mapstruct.Mapper;

/**
 * 配置Web转换器
 */
@Mapper
public interface ConfigWebConverter {

    CreateConfigCommand toCommand(CreateConfigReq req);

    UpdateConfigCommand toCommand(UpdateConfigReq req);

    ConfigPageQuery toQuery(ConfigPageReq req);

    ConfigResp toResp(ConfigDTO dto);
}
