package com.bone.tpa.claim.flow;

import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;

public interface FlowFireService {
    /**
     * 向下推送流程
     * @param claim
     */
    void fire(Claim claim);

    /**
     * 是否能跳过这个节点
     * @param claim
     * @return
     */
    Boolean canJoin(Claim claim);


    /**
     * 落地流程后的动作（一般记录一个异步任务)
     * @param claim
     */
    void doLandEvent(Claim claim);


}
