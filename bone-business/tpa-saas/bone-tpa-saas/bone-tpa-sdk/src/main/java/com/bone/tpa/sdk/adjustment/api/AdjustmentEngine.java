package com.bone.tpa.sdk.adjustment.api;

import com.bone.tpa.sdk.adjustment.request.ClaimAdjustmentRequest;
import com.bone.tpa.sdk.adjustment.response.ClaimAdjustmentResponse;
import com.bone.tpa.sdk.claim.model.Claim;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * 理算接口
 *
 */
public interface AdjustmentEngine {

    /**
     * 执行理赔处理流程，包括校验、理算、额度冻结、结果持久化等步骤
     *
     * @param claimId 赔案主键
     * @return 理算结果对象
     */
    ClaimAdjustmentResponse adjustClaim(@RequestParam("claimId") Long claimId);

    /**
     * 执行理赔处理流程，包括校验、理算、额度冻结、结果持久化等步骤
     *
     * @return 理算结果对象
     */
    ClaimAdjustmentResponse adjustClaim(Claim claim);

//    /**
//     * 执行理赔处理流程，包括校验、理算、额度冻结、结果持久化等步骤
//     *
//     * @param claimAdjustmentRequest 赔案请求
//     * @return 理算结果对象
//     */
//    ClaimAdjustmentResponse adjustClaim(ClaimAdjustmentRequest claimAdjustmentRequest);

    /**
     * 清除指定赔案的理算结果
     *
     * <p>执行操作包括：<br>
     * 1. 释放冻结额度<br>
     * 2. 删除理算临时数据<br>
     *
     * @param claimId 理赔业务唯一标识号
     */
    void clearClaimAdjustment(@RequestParam("claimId") Long claimId);

    /**
     * 重新理算流程
     * @param claimId 数据库记录主键ID
     */
    ClaimAdjustmentResponse readjustClaim(@RequestParam("claimId") Long claimId);

//    /**
//     * 重新理算
//     * @param request 包含最新参数的请求对象
//     */
//    ClaimAdjustmentResponse readjustClaim(ClaimAdjustmentRequest request);
}
