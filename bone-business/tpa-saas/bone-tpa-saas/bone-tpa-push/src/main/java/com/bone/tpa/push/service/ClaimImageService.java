package com.bone.tpa.push.service;

import com.bone.tpa.sdk.claim.model.ClaimImage;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/10/22 15:01
 */
public interface ClaimImageService {
    List<ClaimImage> getClaimImageList(Long claimNo);
}
