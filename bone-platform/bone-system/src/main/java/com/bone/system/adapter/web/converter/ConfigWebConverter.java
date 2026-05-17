package com.bone.system.adapter.web.converter;

import com.bone.system.adapter.web.dto.req.ConfigPageReq;
import com.bone.system.adapter.web.dto.req.CreateConfigReq;
import com.bone.system.adapter.web.dto.req.UpdateConfigReq;
import com.bone.system.adapter.web.dto.resp.ConfigResp;
import com.bone.system.application.command.cmd.CreateConfigCmd;
import com.bone.system.application.command.cmd.UpdateConfigCmd;
import com.bone.system.application.query.dto.ConfigDTO;
import com.bone.system.application.query.qry.ConfigPageQry;
import org.mapstruct.Mapper;

/**
 * 配置Web转换器
 */
@Mapper
public interface ConfigWebConverter {

    CreateConfigCmd toCmd(CreateConfigReq req);

    UpdateConfigCmd toCmd(UpdateConfigReq req);

    ConfigPageQry toQry(ConfigPageReq req);

    ConfigResp toResp(ConfigDTO dto);
}
