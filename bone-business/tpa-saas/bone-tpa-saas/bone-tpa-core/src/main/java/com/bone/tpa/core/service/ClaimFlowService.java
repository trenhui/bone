package com.bone.tpa.core.service;

import com.bone.tpa.sdk.claim.enums.ClaimFlowStatus;
import com.bone.tpa.sdk.vo.ClaimFlowConfigVO;

public interface ClaimFlowService {
    /**
     * 获取草稿态的流程配置
     * @param bizIdentityCode
     * @return
     */
    ClaimFlowConfigVO getClaimFlowConfig(String bizIdentityCode, ClaimFlowStatus status);



    ClaimFlowConfigVO getClaimFlowById(Long flowConfigId,String bizIdentityCode);



}
