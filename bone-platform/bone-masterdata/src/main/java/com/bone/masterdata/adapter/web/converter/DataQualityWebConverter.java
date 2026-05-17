package com.bone.masterdata.adapter.web.converter;

import com.bone.masterdata.adapter.web.dto.req.CreateDataQualityRuleReq;
import com.bone.masterdata.adapter.web.dto.req.UpdateDataQualityRuleReq;
import com.bone.masterdata.adapter.web.dto.resp.DataQualityRuleDetailResp;
import com.bone.masterdata.application.command.cmd.CreateDataQualityRuleCmd;
import com.bone.masterdata.application.command.cmd.UpdateDataQualityRuleCmd;
import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import org.springframework.stereotype.Component;

@Component
public class DataQualityWebConverter {

    public CreateDataQualityRuleCmd toCmd(CreateDataQualityRuleReq req) {
        return CreateDataQualityRuleCmd.builder()
                .masterDataEntityId(req.getMasterDataEntityId())
                .name(req.getName())
                .type(req.getRuleType())
                .expression(req.getRuleConfig())
                .severity(parseSeverity(req.getSeverity()))
                .description(req.getDescription())
                .build();
    }

    public UpdateDataQualityRuleCmd toCmd(Long id, UpdateDataQualityRuleReq req) {
        return UpdateDataQualityRuleCmd.builder()
                .id(id)
                .name(req.getName())
                .ruleType(req.getRuleType())
                .ruleConfig(req.getRuleConfig())
                .severity(req.getSeverity())
                .description(req.getDescription())
                .build();
    }

    public DataQualityRuleDetailResp toResp(DataQualityRuleDTO dto) {
        return DataQualityRuleDetailResp.builder()
                .id(dto.getId())
                .masterDataEntityId(dto.getMasterDataEntityId())
                .name(dto.getName())
                .ruleType(dto.getType())
                .ruleConfig(dto.getExpression())
                .severity(dto.getSeverity() != null ? dto.getSeverity().name() : null)
                .description(dto.getDescription())
                .build();
    }

    private static RuleSeverity parseSeverity(String severity) {
        return severity != null && !severity.isBlank() ? RuleSeverity.valueOf(severity) : null;
    }
}
