package com.bone.tpa.soa.backnodeevent.impl;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.api.enums.BackClaimEventType;
import com.bone.tpa.api.enums.HangUpStatus;
import com.bone.tpa.api.request.BackToStatusRequest;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.claim.sync.ClaimSyncFromTpaService;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.soa.backnodeevent.BackNodeEvnetAction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class BackQualityNodeAction implements BackNodeEvnetAction {
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private ClaimSyncFromTpaService syncFromTpaService;

    @Override
    public BackClaimEventType getEvent() {
        return BackClaimEventType.tpa退回质检;
    }

    @Override
    public Map<String, Object> fire(BackToStatusRequest request) {
        log.info("backToquality request:{}", JSONObject.toJSONString(request));

        Claim claimCheck = claimRepository.findById(request.getClaimInfo().getClaimNumber());
        if (StringUtils.isBlank(request.getOperatorGroupId())) {
            throw new IllegalArgumentException("退回质检时，operatorGroupId不能为空");
        }

        if (StringUtils.isBlank(request.getOperatorId())) {
            throw new IllegalArgumentException("退回质检时，operatorId不能为空");
        }
        if (StringUtils.isBlank(request.getOperatorName())) {
            throw new IllegalArgumentException("退回质检时，operatorName不能为空");
        }

        //退回质检
        ClaimStatusEnum nowStatus = ClaimStatusEnum.getByCode(claimCheck.getStatus(), "");
        if (nowStatus == ClaimStatusEnum.WAIBAO_INPUTING
                || nowStatus == ClaimStatusEnum.PUKANG_INPUTING
                || nowStatus == ClaimStatusEnum.PRE_ADUITING
        ) {
            throw new IllegalArgumentException("当前状态不允许退回质检");
        }
        //同步数据
        syncFromTpaService.saveSyncVo(request.getClaimInfo(), null);


        //退回质检
        Claim updto = new Claim();
        updto.setId(claimCheck.getId());
        updto.setBizIdentityCode(claimCheck.getBizIdentityCode());
        updto.setOperatorOrgName(request.getOperatorGroupName());
        updto.setOperatorUserId(request.getOperatorId());
        updto.setOperatorUserName(request.getOperatorName());
        updto.setOperatorOrgId(request.getOperatorGroupId());
        updto.setStatus(ClaimStatusEnum.INSPECTIONING.getCode());
        updto.setStatusSub(ClaimStatusEnum.INSPECTIONING.getSubStatus());
        updto.setStage(ClaimStatusEnum.INSPECTIONING.getStage().getCode());
        updto.setHangUpStatus(HangUpStatus.NO_HANG_UP.getCode());
        updto.setHangUpType("");
        claimService.updateClaim(updto, false, "BackQualityNodeAction.fire");
        return new HashMap<>();
    }
}
