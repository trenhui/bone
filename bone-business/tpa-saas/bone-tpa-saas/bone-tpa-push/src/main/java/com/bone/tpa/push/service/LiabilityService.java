package com.bone.tpa.push.service;

import com.bone.tpa.push.bean.BatchDataBean;
import com.bone.tpa.sdk.adjustment.model.Coverage;
import com.bone.tpa.sdk.adjustment.model.Plan;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/10/22 13:58
 */
public interface LiabilityService {
    Coverage getCoverage(Long planId, String planVersion);
    Plan getPlan(Long planId, String planVersion);
    BatchDataBean getLiabilityMap(List<ClaimInvoice> claimInvoices, String version);
}
