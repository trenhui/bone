package com.bone.system.adapter.web.dto.req;

import lombok.Data;

/**
 * 告警规则分页查询请求
 */
@Data
public class AlertRulePageReq {
    private String keyword;
    private String alertLevel;
    private Boolean enabled;
    private int pageNum = 1;
    private int pageSize = 10;
}
