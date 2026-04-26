package com.bone.tpa.audit.application.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.tpa.audit.application.ConfigAdapterAbstractHandler;
import com.bone.tpa.audit.application.ConfigSupportService;
import com.bone.tpa.audit.application.request.HistoryClaimQueryRequest;
import com.bone.tpa.audit.domain.service.ClaimAuditService;
import com.bone.tpa.audit.infrastructure.feign.request.HospitalNameCheckRequest;
import com.bone.tpa.audit.infrastructure.feign.request.QueryReviewInfoRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaHistoryClaimQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.request.TpaPersonInfoQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.response.*;
import com.bone.tpa.push.service.PushClaimService;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.service.ClaimInfoService;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:44
 */
@Service
public class ConfigAdapterHandler extends ConfigAdapterAbstractHandler {

    @Autowired
    private ConfigSupportService configSupportService;

    @Autowired
    private ClaimAuditService claimAuditService;

    @Autowired
    private ClaimInfoService claimInfoService;

    @Autowired
    private PushClaimService pushClaimService;

    @Override
    public List<ThirdWriteInfoResponse> handleThirdWriteInfo(Long claimId) {
        Object outWriterRecord = configSupportService.getOutWriterRecord(claimId);
        if (outWriterRecord == null) {
            return null;
        }
        List<ThirdWriteInfoResponse> thirdWriteInfoResponses = (List<ThirdWriteInfoResponse>) outWriterRecord;
        return thirdWriteInfoResponses;
    }

    @Override
    public List<TpaHistoryClaimQueryResponse> handleHistoryClaimQuery(HistoryClaimQueryRequest request) {
        Claim claim = claimInfoService.getClaim(request.getClaimNo());
        TpaHistoryClaimQueryRequest tpaHistoryClaimQueryRequest = new TpaHistoryClaimQueryRequest();
        tpaHistoryClaimQueryRequest.setInsuranceName(claim.getInsuranceName());
        tpaHistoryClaimQueryRequest.setOutUserName(request.getOutUserName());
        tpaHistoryClaimQueryRequest.setOutIdentityNo(request.getOutIdentityNo());
        List<TpaHistoryClaimQueryResponse> allHistoryClaims = Lists.newArrayList();
        // saas 历史案件查询
        List<TpaHistoryClaimQueryResponse> tpaHistoryClaimQueryResponses = claimAuditService.queryHistoryClaims(tpaHistoryClaimQueryRequest);
        if (CollectionUtil.isNotEmpty(tpaHistoryClaimQueryResponses)) {
            allHistoryClaims.addAll(tpaHistoryClaimQueryResponses);
        }
        // 新tpa 历史案件
        List<TpaHistoryClaimQueryResponse> tpaHistoryClaims = configSupportService.queryTpaHistoryClaims(tpaHistoryClaimQueryRequest);
        if (CollectionUtil.isNotEmpty(tpaHistoryClaims)) {
            allHistoryClaims.addAll(tpaHistoryClaims);
        }
        List<TpaHistoryClaimQueryResponse> distinctList = Lists.newArrayList();
        if (CollectionUtil.isNotEmpty(allHistoryClaims)) {
            distinctList = new ArrayList<>(allHistoryClaims.stream()
                            .collect(Collectors.toMap(
                                    TpaHistoryClaimQueryResponse::getClaimNo,
                                    Function.identity(),
                                    (k1, k2) -> k1,
                                    LinkedHashMap::new
                            ))
                            .values());
        }
        return distinctList;
    }

    @Override
    public QueryReviewInfoResponse handleQueryReviewInfo(QueryReviewInfoRequest request) {
        QueryReviewInfoResponse queryReviewInfoResponse = configSupportService.queryReviewInfo(request);
        return queryReviewInfoResponse;
    }

    @Override
    @Async
    public void pushClaim(Long claimNo) {
        pushClaimService.push(claimNo, null);
    }

    @Override
    public TpaPersonInfoQueryResponse queryPersonInfo(TpaPersonInfoQueryRequest request) {
        TpaPersonInfoQueryResponse tpaPersonInfoQueryResponse = configSupportService.queryPersonInfo(request);
        return tpaPersonInfoQueryResponse;
    }
}
