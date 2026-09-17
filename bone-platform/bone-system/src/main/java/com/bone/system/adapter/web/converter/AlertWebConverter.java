package com.bone.system.adapter.web.converter;

import com.bone.system.adapter.web.dto.request.AlertRecordPageReq;
import com.bone.system.adapter.web.dto.request.AlertRulePageReq;
import com.bone.system.adapter.web.dto.request.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.request.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.response.AlertRecordResp;
import com.bone.system.adapter.web.dto.response.AlertRuleResp;
import com.bone.system.application.command.cmd.CreateAlertRuleCommand;
import com.bone.system.application.command.cmd.UpdateAlertRuleCommand;
import com.bone.system.application.query.dto.AlertRecordDTO;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.application.query.qry.AlertRecordPageQuery;
import com.bone.system.application.query.qry.AlertRulePageQuery;
import org.mapstruct.Mapper;

/** 告警Web转换器 */
@Mapper
public interface AlertWebConverter {

  CreateAlertRuleCommand toCommand(CreateAlertRuleReq req);

  UpdateAlertRuleCommand toCommand(UpdateAlertRuleReq req);

  AlertRulePageQuery toQuery(AlertRulePageReq req);

  AlertRecordPageQuery toQuery(AlertRecordPageReq req);

  AlertRuleResp toResp(AlertRuleDTO dto);

  AlertRecordResp toResp(AlertRecordDTO dto);
}
