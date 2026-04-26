package com.bone.lowcode.infra.domain.model;

import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import lombok.Data;

import java.util.List;

//基础页面版本类
@Data
public class BasicPageInfo {

    private CfgPageDO pageDO;

    private CfgFormDO formDO;

    private List<CfgBlockDO> blockDOList;

    private List<CfgFieldsetDO> fieldsetDOList;

    private List<CfgTableDO> tableDOList;

    private List<CfgModelDO> modelDOList;

    private List<CfgFieldDO> fieldDOList;

    private List<CfgEventTriggerDO> eventTriggerList;

    private List<CfgSubmitRuleDO> submitRuleList;

    private List<CfgFieldLinkageRuleDO> fieldLinkageRuleList;

    private List<FieldLinkedDisplayRule> fieldLinkedDisplayRuleList;

    private List<CfgFieldTableRule> fieldTableRuleList;

    private List<CfgTableDataGroupAggregateDO> tableDataGroupAggregateDOList;

    private List<CfgTableDataRowVerifyDO> tableDataRowVerifyDOList;

    private List<CfgTableDataRowEditDO> tableDataRowEditDOList;

    private List<CfgTableDataRelationDO> tableDataRelationDOList;

    private List<CfgTableDataCrossEditDO> crossTableDataEditDOList;

    private List<CfgTableDataCrossVerifyDO> crossTableDataVerifyDOList;
}
