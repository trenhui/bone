package com.bone.tpa.api.request;

import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import lombok.Data;

@Data
public class BackToStatusRequest {


    private String operatorId ;

    private String operatorName ;

    private String operatorGroupId;

    private String operatorGroupName;

    private String reason ;

    private String reasonType;
    /**
     * 见枚举 EventType.type = 2
     * 不能为空，要匹配的到
     */
    private String statusEvent;

    private ClaimDetailSyncVO claimInfo; //saas会更新赔案数据

}
