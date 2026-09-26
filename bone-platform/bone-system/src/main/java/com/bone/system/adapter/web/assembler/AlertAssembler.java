package com.bone.system.adapter.web.assembler;

import com.bone.system.adapter.web.dto.request.AlertRecordPageReq;
import com.bone.system.adapter.web.dto.request.AlertRulePageReq;
import com.bone.system.adapter.web.dto.request.CreateAlertRuleReq;
import com.bone.system.adapter.web.dto.request.UpdateAlertRuleReq;
import com.bone.system.adapter.web.dto.response.AlertRecordResp;
import com.bone.system.adapter.web.dto.response.AlertRuleResp;
import com.bone.system.application.command.CreateAlertRuleCommand;
import com.bone.system.application.command.UpdateAlertRuleCommand;
import com.bone.system.application.query.dto.AlertRecordDto;
import com.bone.system.application.query.dto.AlertRuleDto;
import com.bone.system.application.query.qry.AlertRecordPageQuery;
import com.bone.system.application.query.qry.AlertRulePageQuery;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;

/** 告警的 HTTP ↔ application 转换边界。 */
@Mapper
public interface AlertAssembler {

  CreateAlertRuleCommand toCommand(CreateAlertRuleReq req);

  UpdateAlertRuleCommand toCommand(UpdateAlertRuleReq req);

  AlertRulePageQuery toQuery(AlertRulePageReq req);

  AlertRecordPageQuery toRecordQuery(AlertRecordPageReq req);

  AlertRuleResp toResp(AlertRuleDto dto);

  AlertRecordResp toResp(AlertRecordDto dto);

  /** LocalDateTime → Instant（UTC 归一），对外契约统一带偏移的 ISO-8601（...Z）（i18n 方案 §6.4）。 */
  default Instant toInstant(LocalDateTime ldt) {
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }
}
