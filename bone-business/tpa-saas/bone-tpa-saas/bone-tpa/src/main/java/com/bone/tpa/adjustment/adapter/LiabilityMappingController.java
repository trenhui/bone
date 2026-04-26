package com.bone.tpa.adjustment.adapter;

import com.bone.core.result.Result;
import com.bone.tpa.adjustment.application.LiabilityMappingApplicationService;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.sdk.adjustment.request.LiabilityMappingReq;
import com.bone.tpa.sdk.adjustment.response.LiabilityMappingRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tpa/liabilityMapping")
@SimpleLog
public class LiabilityMappingController {

    @Autowired
    private LiabilityMappingApplicationService applicationService;


    /**
     * 给已发布计划下的责任生成责任映射数据
     */
    @GetMapping("/initLiabilityMapping")
    public Result<Boolean> initLiabilityMapping(@RequestParam("planId") Long planId) {
        applicationService.updateLiabilityMapping(planId);
        return Result.ok(true);
    }

    /**
     * 查询保单下的责任映射数据
     */
    @GetMapping("/getLiabilityMapping")
    public Result getLiabilityMapping(@RequestParam("policyNo") String policyNo) {
        List<LiabilityMappingRes> res = applicationService.getLiabilityMapping(policyNo);
        return Result.ok(res);
    }

    /**
     * 更新单条责任映射数据
     */
    @PostMapping("/updateLiabilityMapping")
    public Result updateLiabilityMapping(@RequestBody LiabilityMappingReq param) {
        Boolean flag = applicationService.updateLiabilityMapping(param);
        if (flag) return Result.ok("更新完成!");
        else return Result.error("更新失败!");
    }

    /**
     * 批量更新责任映射数据
     */
    @PostMapping("/batchUpdateLiabilityMapping")
    public Result batchUpdateLiabilityMapping(@RequestBody List<LiabilityMappingReq> param) {
        Boolean flag = applicationService.batchUpdateLiabilityMapping(param);
        if (flag) return Result.ok("更新完成!");
        else return Result.error("更新失败!");
    }
}
