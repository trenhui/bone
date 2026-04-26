package com.bone.tpa.api.request;

import lombok.Data;

@Data
public class AssignClaimAndSyncRequest {

    private Long claimNumber;
    /**
     * 见枚举 EventType.code
     * 不能为空，要匹配的到
     */
   // private AssignClaimEventType eventType ; //事件

    private String eventType;
    private String operatorId ="";

    private String operatorName  ="";

    private String operatorGroupId ="";

    private String operatorGroupName ="";

    private String remark;

}
