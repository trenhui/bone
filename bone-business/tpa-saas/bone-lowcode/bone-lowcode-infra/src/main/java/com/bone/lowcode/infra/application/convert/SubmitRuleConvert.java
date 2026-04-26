package com.bone.lowcode.infra.application.convert;


import com.bone.lowcode.infra.application.dto.submitRule.CreateSubmitRuleDTO;
import com.bone.lowcode.infra.application.dto.submitRule.UpdateSubmitRuleDTO;
import com.bone.lowcode.infra.application.vo.page.pageJson.SubmitRule;
import com.bone.lowcode.infra.application.vo.submitRule.GetSubmitRuleByPageVO;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgSubmitRuleDO;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class SubmitRuleConvert {
    public static CfgSubmitRuleDO createSubmitRuleDTOToDO(CreateSubmitRuleDTO dto) {
        CfgSubmitRuleDO submitRuleDO = new CfgSubmitRuleDO();
        BeanUtils.copyProperties(dto, submitRuleDO);
        List<Long> fieldIdList = dto.getFieldIdList();
        submitRuleDO.setFieldList(fieldIdList.stream().map(String::valueOf).collect(Collectors.joining(",")));
        submitRuleDO.setStatus(StatusEnum.YES.getCode());
        submitRuleDO.setDeleted(DeletedEnum.UNDELETED.getCode());
        submitRuleDO.setCreateBy(null);
        submitRuleDO.setCreateTime(new Date());
        return submitRuleDO;
    }

    public static GetSubmitRuleByPageVO DOToGetSubmitRuleByPageVO(CfgSubmitRuleDO submitRuleDO) {
        GetSubmitRuleByPageVO vo = new GetSubmitRuleByPageVO();
        BeanUtils.copyProperties(submitRuleDO, vo);
        vo.setId(submitRuleDO.getId().toString());
        vo.setPageId(submitRuleDO.getPageId().toString());
        return vo;
    }

    public static CfgSubmitRuleDO updateSubmitRuleDTOToDO(UpdateSubmitRuleDTO dto) {
        CfgSubmitRuleDO submitRuleDO = new CfgSubmitRuleDO();
        BeanUtils.copyProperties(dto, submitRuleDO);
        if (!CollectionUtils.isEmpty(dto.getFieldIdList())) {
            submitRuleDO.setFieldIds(dto.getFieldIdList());
        }
        return submitRuleDO;
    }

    public static SubmitRule DOToSubmitRule(CfgSubmitRuleDO submitRuleDO) {
        SubmitRule submitRule = new SubmitRule();
        submitRule.setId(submitRuleDO.getId().toString());
        submitRule.setFunctionType(submitRuleDO.getFunctionType());
        submitRule.setFunctionName(submitRuleDO.getFunctionName());
        submitRule.setOperator(submitRuleDO.getOperator());
        submitRule.setValueType(submitRuleDO.getValueType());
        submitRule.setVerifyType(submitRuleDO.getVerifyType());
        submitRule.setErrorPrompt(submitRuleDO.getErrorPrompt());
        return submitRule;
    }
}
