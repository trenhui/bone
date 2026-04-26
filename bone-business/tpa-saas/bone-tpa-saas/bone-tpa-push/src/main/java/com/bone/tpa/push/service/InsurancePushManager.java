package com.bone.tpa.push.service;

import com.bone.tpa.push.feign.request.ClaimCancelRequest;

public interface InsurancePushManager {
    String claimCancel(ClaimCancelRequest request);
}
