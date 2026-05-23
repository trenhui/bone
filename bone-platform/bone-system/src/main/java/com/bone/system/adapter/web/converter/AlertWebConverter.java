package com.bone.system.adapter.web.converter;

import com.bone.system.adapter.web.dto.req.AlertEventPageReq;
import com.bone.system.adapter.web.dto.req.AlertRulePageReq;
import com.bone.system.adapter.web.dto.req.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.req.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.resp.AlertEventResp;
import com.bone.system.adapter.web.dto.resp.AlertRuleResp;
import com.bone.system.application.command.cmd.CreateAlertRuleCommand;
import com.bone.system.application.command.cmd.UpdateAlertRuleCommand;
import com.bone.system.application.query.dto.AlertEventDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.query.qry.AlertEventPageQuery;
import com.bone.system.application.query.qry.AlertRulePageQuery;
import org.mapstruct.Mapper;

/**
 * 告警Web转换器
 */
@Mapper
public interface AlertWebConverter {

    CreateAlertRuleCommand toCommand(CreateAlertRuleReq req);

    UpdateAlertRuleCommand toCommand(UpdateAlertRuleReq req);

    AlertRulePageQuery toQuery(AlertRulePageReq req);

    AlertEventPageQuery toQuery(AlertEventPageReq req);

    AlertRuleResp toResp(AlertRuleDTO dto);

    AlertEventResp toResp(AlertEventDTO dto);
}
