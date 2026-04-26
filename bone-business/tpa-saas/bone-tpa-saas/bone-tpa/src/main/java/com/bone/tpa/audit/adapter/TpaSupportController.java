package com.bone.tpa.audit.adapter;

import com.bone.core.result.Result;
import com.bone.tpa.audit.application.ConfigAdapterAbstractHandler;
import com.bone.tpa.audit.application.request.HistoryClaimQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.request.HospitalNameCheckRequest;
import com.bone.tpa.audit.infrastructure.feign.request.QueryReviewInfoRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaHistoryClaimQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaPersonInfoQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.response.*;
import com.bone.tpa.push.feign.request.TpaPersonalImageQueryRequest;
import com.bone.tpa.push.feign.response.PersonalImageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:09
 */
@RestController
@RequestMapping("/v1/tpa/support")
public class TpaSupportController {

    @Autowired
    private ConfigAdapterAbstractHandler configAdapterHandler;

    @Autowired
    @Qualifier("pushConfigAdapterHandler")
    private com.bone.tpa.push.service.ConfigAdapterAbstractHandler pushConfigAdapterHandler;

    @GetMapping("/getOutWriterRecords")
    public Result<List<ThirdWriteInfoResponse>> getOutWriterRecord(@RequestParam("claimNumber") Long claimNumber) {
        List<ThirdWriteInfoResponse> thirdWriteInfoResponses = configAdapterHandler.handleThirdWriteInfo(claimNumber);
        return Result.ok(thirdWriteInfoResponses);
    }

    @PostMapping("/queryHistoryClaims")
    public Result<List<TpaHistoryClaimQueryResponse>> queryHistoryClaims(@RequestBody @Validated HistoryClaimQueryRequest request) {
        List<TpaHistoryClaimQueryResponse> responses = configAdapterHandler.handleHistoryClaimQuery(request);
        return Result.ok(responses);
    }

    @PostMapping("/queryReviewInfo")
    public Result<QueryReviewInfoResponse> queryReviewInfo(@RequestBody @Validated QueryReviewInfoRequest request) {
        QueryReviewInfoResponse reviewInfoResponse = configAdapterHandler.handleQueryReviewInfo(request);
        return Result.ok(reviewInfoResponse);
    }

    @PostMapping("/queryPersonInfo")
    public Result<TpaPersonInfoQueryResponse> queryPersonInfo(@RequestBody @Validated TpaPersonInfoQueryRequest request) {
        TpaPersonInfoQueryResponse personInfoQueryResponse = configAdapterHandler.queryPersonInfo(request);
        return Result.ok(personInfoQueryResponse);
    }

    @PostMapping("/queryPersonalClaimImage")
    public Result<List<PersonalImageResponse>> queryPersonalClaimImage(@RequestBody @Validated TpaPersonalImageQueryRequest request) {
        List<PersonalImageResponse> responses = pushConfigAdapterHandler.handlePersonalClaimImages(request);
        return Result.ok(responses);
    }

    @GetMapping("/pushClaim")
    public Result<String> pushClaim(@RequestParam("claimNo") Long claimNo) {
        configAdapterHandler.pushClaim(claimNo);
        return Result.ok();
    }
}
