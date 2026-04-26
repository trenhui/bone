package com.bone.tpa.adjustment.adapter;

import com.bone.core.result.Result;
import com.bone.tpa.adjustment.application.AdjustmentApplicationService;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.intelligent.adjustment.dto.request.LiabilityCreateBatchRequest;
import com.bone.tpa.intelligent.adjustment.dto.request.PolicyBindRequest;
import com.bone.tpa.intelligent.adjustment.model.AdjustmentInfo;
import com.bone.tpa.intelligent.adjustment.model.LiabilityToBind;
import com.bone.tpa.intelligent.adjustment.service.PolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tpa/adjust")
@SimpleLog
public class AdjustmentController {

    @Autowired
    private AdjustmentApplicationService adjustmentApplicationService;

    @Autowired
    private PolicyService policyService;

    /**
     * 绑定保单到该赔案
     *
     * @return 理算结果对象
     */
    @PostMapping("/bind")
    public Result<Boolean> bindPolicy(@RequestBody PolicyBindRequest request) {
        policyService.savePolicyInfo(request.getPolicyInfoModel(), request.getClaimId());
        return Result.ok();
    }


    /**
     * 查询可以绑定的责任
     *
     * @return 理算结果对象
     */
    @GetMapping("/queryliability")
    public Result<List<LiabilityToBind>> queryLiabilityToBind(@RequestParam("claimId") Long claimId, @RequestParam("visitTypeCn") String visitTypeCn) {
        return Result.ok(adjustmentApplicationService.queryLiabilityToBind(claimId, visitTypeCn));
    }



    /**
     * 执行单赔案的理算
     *
     * @param claimId 赔案主键
     * @return 理算结果对象
     */
    @GetMapping("/adjust")
    public Result<AdjustmentInfo> adjustClaim(@RequestParam("claimId") Long claimId) {
        return Result.ok(adjustmentApplicationService.adjustClaim(claimId));
    }

    /**
     * 清除指定赔案的理算结果
     *
     * <p>执行操作包括：<br>
     * 1. 释放冻结额度<br>
     * 2. 删除理算临时数据<br>
     *
     * @param claimId 理赔业务唯一标识号
     */
    @GetMapping("/clear")
    public Result<Boolean> clearClaimAdjustment(@RequestParam("claimId") Long claimId) {
        adjustmentApplicationService.clear(claimId);
        return Result.ok();
    }

    /**
     * 重新理算流程
     * @param claimId 数据库记录主键ID
     */
    @GetMapping("/readjust")
    public Result<AdjustmentInfo> readjustClaim(@RequestParam("claimId") Long claimId) {
        return Result.ok(adjustmentApplicationService.readjustClaim(claimId));
    }

}
