package com.bone.tpa.test.claim;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.util.JsonUtil;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.config.JacksonConfig;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.EventOtherRequest;
import com.bone.tpa.facade.request.TpaAddLogRequest;
import com.bone.tpa.facade.request.TpaSubmitClaimResult;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.test.BaseTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class FlowToTpaTest  extends BaseTest {
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private ClaimToTpaChangeService toTpaChangeService;
    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;
    @Autowired
    private ClaimTrackLogService trackLogService;
    @Autowired
    private CommonLogService commonLogService;

    @Autowired
    private ClaimService claimService;
    @Test
    public void test() {
        Long claimId = 254903131001L;

        Claim claim = claimRepository.findById(claimId);
        ClaimStatusEnum toClaimStatus = ClaimStatusEnum.Auditing;

        String assignType = "2";

        //  走初审人工分配
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(claimId);

       // request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));

        request.setEventTime(System.currentTimeMillis());

        request.setEventType(Integer.valueOf(EventType.saas通知tpa同步数据状态和日志.getCode()));
        EventOtherRequest eventOtherRequest = new EventOtherRequest();
        request.setEventOtherRequest(eventOtherRequest);
        eventOtherRequest.setAssignStage(ClaimStageEnum.AUDITING.getCode());
        eventOtherRequest.setClaimStatus(Integer.valueOf(toClaimStatus.getCode()));
        eventOtherRequest.setAssignTag(true);
        eventOtherRequest.setAssignStrategy(assignType);


        if(toClaimStatus == ClaimStatusEnum.WaitAudit){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation("进入审核等待分配");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }

        if(toClaimStatus == ClaimStatusEnum.Auditing){
            TpaAddLogRequest tpaAddLogRequest = new TpaAddLogRequest();
            tpaAddLogRequest.setClaimNumber( claim.getId());
            tpaAddLogRequest.setOperation("进入审核阶段");
            tpaAddLogRequest.setRemark("");
            tpaAddLogRequest.setCreateBy("System");
            tpaAddLogRequest.setCreateTime( (new Date()).getTime());
            eventOtherRequest.getOperationLogList().add(tpaAddLogRequest);
        }

        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        //log.info("saas通知tpa初审完成不自动分配:{}",remoteRs);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "saas通知tpa进入审核阶段,cremoteRs:{}", remoteRs);

        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("saas通知tpa进入审核阶段，通知tpa失败:" + remoteRs.getMessage());
        }

    }


}
