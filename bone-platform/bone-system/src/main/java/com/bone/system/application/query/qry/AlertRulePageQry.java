package com.bone.system.application.query.qry;

import lombok.Data;

@Data
public class AlertRulePageQry {
    private String keyword;
    private String alertLevel;
    private Boolean enabled;
    private int pageNum = 1;
    private int pageSize = 10;
}
