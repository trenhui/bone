package com.bone.system.adapter.web.converter;

import com.bone.system.adapter.web.dto.req.AlertEventPageReq;
import com.bone.system.adapter.web.dto.req.AlertRulePageReq;
import com.bone.system.adapter.web.dto.req.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.req.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.resp.AlertEventResp;
import com.bone.system.adapter.web.dto.resp.AlertRuleResp;
import com.bone.system.application.command.cmd.CreateAlertRuleCmd;
import com.bone.system.application.command.cmd.UpdateAlertRuleCmd;
import com.bone.system.application.query.dto.AlertEventDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.query.qry.AlertEventPageQry;
import com.bone.system.application.query.qry.AlertRulePageQry;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 告警Web转换器
 */
@Mapper
public interface AlertWebConverter {
    AlertWebConverter INSTANCE = Mappers.getMapper(AlertWebConverter.class);

    CreateAlertRuleCmd toCmd(CreateAlertRuleReq req);

    UpdateAlertRuleCmd toCmd(UpdateAlertRuleReq req);

    AlertRulePageQry toQry(AlertRulePageReq req);

    AlertEventPageQry toQry(AlertEventPageReq req);

    AlertRuleResp toResp(AlertRuleDTO dto);

    AlertEventResp toResp(AlertEventDTO dto);
}
