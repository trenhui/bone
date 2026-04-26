package com.bone.tpa.api.request;

import com.bone.tpa.api.vo.ClaimConfig;
import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import lombok.Data;

@Data
public class SyncClaimWithEventRequest {

    /**
     * 赔案号
     * not null
     */
    private Long claimNumber;
    /**
     * 见枚举 EventType.type = 1
     * 不能为空，要匹配的到
     * tpa也能自己有自己的eventCode
     */
    private String eventType; //事件code


    /**
     * tpa 的操作人员id
     * 根据eventcode，判断是否要更新
     */
    private String operatorId ; //操作人id
    /**
     * tpa 的操作人员name
     * 根据eventcode，判断是否要更新
     */
    private String operatorName ; //操作人姓名
    /**
     * tpa 的操作人员所属组id
     * 根据eventcode，判断是否要更新
     */
    private String operatorGroupId; //操作组id
    /**
     * tpa 的操作人员所属组名称
     * 根据eventcode，判断是否要更新
     */
    private String operatorGroupName; //操作组名称
    /**
     * 备注信息
     */
    private String remark ; //备注

    /**
     * 赔案的一些流程配置，和赔案暑假无关
     * 只在签收下法时候会传
     *
     */
    private ClaimConfig claimConfig;
    /**
     * 赔案信息
     */
    private ClaimDetailSyncVO claimInfo;//更新信息
}
