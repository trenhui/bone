package com.bone.lowcode.infra.application.convert;


import com.bone.lowcode.infra.application.dto.tableRule.CreateTableRowEditRuleDTO;
import com.bone.lowcode.infra.application.dto.tableRule.UpdateRowRuleDTO;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataRowEditDO;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Date;

public class TableRuleConvert {
    public static CfgTableDataRowEditDO createTableRowEditRuleDTOToDO(CreateTableRowEditRuleDTO param) {
        CfgTableDataRowEditDO ruleDO = new CfgTableDataRowEditDO();
        BeanUtils.copyProperties(param, ruleDO);
        ruleDO.setTableId(param.getTableId());
        ruleDO.setSourceFieldIds(param.getSourceFieldIdList());
        ruleDO.setFunctionName(param.getFunctionName());
        ruleDO.setTargetFieldId(param.getTargetFieldId());
        ruleDO.setDeleted(DeletedEnum.UNDELETED.getCode());
        ruleDO.setCreateTime(new Date());
        return ruleDO;
    }

    public static CfgTableDataRowEditDO updateRowRuleDTOToDO(UpdateRowRuleDTO dto) {
        CfgTableDataRowEditDO ruleDO = new CfgTableDataRowEditDO();
        ruleDO.setId(dto.getId());
        if (!CollectionUtils.isEmpty(dto.getSourceFieldIdList())) {
            ruleDO.setSourceFieldIds(dto.getSourceFieldIdList());
        }
        if (StringUtils.hasText(dto.getFunctionName())) {
            ruleDO.setFunctionName(dto.getFunctionName());
        }
        if (dto.getTargetFieldId() != null) {
            ruleDO.setTargetFieldId(dto.getTargetFieldId());
        }
        ruleDO.setErrorPrompt(dto.getErrorPrompt());
        ruleDO.setStatus(dto.getStatus());
        ruleDO.setVerifyType(dto.getVerifyType());
        return ruleDO;
    }
}
