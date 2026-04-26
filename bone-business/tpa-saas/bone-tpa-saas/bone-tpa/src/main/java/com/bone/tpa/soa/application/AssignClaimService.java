package com.bone.tpa.soa.application;

import com.bone.tpa.api.request.AssignClaimAndSyncRequest;

import java.util.Map;

public interface AssignClaimService {
    /**
     * 分配组和人员
     * @param request
     * @return
     */
    Map<String,Object> assignClaim(AssignClaimAndSyncRequest request);
}
