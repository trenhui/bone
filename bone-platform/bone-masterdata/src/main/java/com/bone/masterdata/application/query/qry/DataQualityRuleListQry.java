package com.bone.masterdata.application.query.qry;

import lombok.Data;

@Data
public class DataQualityRuleListQry {
    private Long masterDataEntityId;
    private String type;
    private String severity;
}