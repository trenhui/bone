package com.bone.tpa.submission.adapter;

import com.bone.core.result.Result;
import com.bone.core.util.JsonUtil;
import com.bone.tpa.sdk.claim.model.SignRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * 在线案件签收模块
 *
 */
@RestController
@RequestMapping("/api/v1/claim")
@Slf4j
public class CaseSignController {

    /**
     * 签收案件 (acknowledgeClaim)
     * @param signRecordRequest 签收请求体
     */
    @PostMapping("/acknowledge")
    public Result<SignRecord> acknowledgeClaim(@RequestBody SignRecord signRecordRequest) {
        log.info("Submitting claim acknowledge for batchNo: {}", signRecordRequest.getBatchNo());
        log.info("signRecordRequest: {}", JsonUtil.toJson(signRecordRequest));
        //ClaimResponse response = claimApplicationService.registerClaim(claimRequest);
        // return ResponseEntity.ok(Result.ok(response));
        signRecordRequest.setRemark("CPL 签收成功");

        return Result.ok(signRecordRequest);
    }

}
