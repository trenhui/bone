package com.bone.tpa.soa.application.impl;

import com.bone.core.util.PkStringUtil;
import com.bone.tpa.api.enums.AssignClaimEventType;
import com.bone.tpa.api.enums.HangUpStatus;
import com.bone.tpa.api.request.AssignClaimAndSyncRequest;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.soa.application.AssignClaimService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

@Service
public class AssignClaimServiceImpl implements AssignClaimService {
    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private CommonLogService commonLogService;

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public Map<String, Object> assignClaim(AssignClaimAndSyncRequest request) {
        Assert.notNull(request.getClaimNumber(),"claimNumber");
        Assert.notNull(request.getEventType(),"eventType is null");
        Claim claim =  claimRepository.findById(request.getClaimNumber());
        if( claim == null){
            throw new IllegalArgumentException("赔案不存在");
        }

        commonLogService.addLogASync(request.getClaimNumber().toString(),
                CommonLogType.FROM_TPA_LOG, "assignClaim,request:{}", request);
        Map<String,Object> rs = new HashMap<>();

        Claim updateDto = new Claim();
        updateDto.setId(request.getClaimNumber());
        updateDto.setBizIdentityCode(claim.getBizIdentityCode());
        updateDto.setTenantId(claim.getTenantId());
        updateDto.setOperatorOrgId(PkStringUtil.nullIfEmpty(request.getOperatorGroupId()));
        updateDto.setOperatorOrgName(PkStringUtil.nullIfEmpty(request.getOperatorGroupName()));
        updateDto.setOperatorUserId(PkStringUtil.nullIfEmpty(request.getOperatorId()));
        updateDto.setOperatorUserName(PkStringUtil.nullIfEmpty(request.getOperatorName()));
        AssignClaimEventType eventType = AssignClaimEventType.getByCode(request.getEventType());
        if( eventType == AssignClaimEventType.tpa分配初审人员和组){
            //状态变成初审中
            updateDto.setStage(ClaimStatusEnum.PRE_ADUITING.getStage().getCode());
            updateDto.setStatus(ClaimStatusEnum.PRE_ADUITING.getCode());
            updateDto.setStatusSub(ClaimStatusEnum.PRE_ADUITING.getSubStatus());
//            updateDto.setPreExamOperatorName(PkStringUtil.nullIfEmpty(request.getOperatorName()));
        }else if(eventType == AssignClaimEventType.tpa分配普康的录入人员和组){
            updateDto.setStage(ClaimStatusEnum.PUKANG_INPUTING.getStage().getCode());
            updateDto.setStatus(ClaimStatusEnum.PUKANG_INPUTING.getCode());
            updateDto.setStatusSub(ClaimStatusEnum.PUKANG_INPUTING.getSubStatus());
//            updateDto.setSubmittingOperatorName(PkStringUtil.nullIfEmpty(request.getOperatorName()));
        }else if( eventType == AssignClaimEventType.tpa分配质检人员和组){
            updateDto.setStage(ClaimStatusEnum.INSPECTIONING.getStage().getCode());
            updateDto.setStatus(ClaimStatusEnum.INSPECTIONING.getCode());
            updateDto.setStatusSub(ClaimStatusEnum.INSPECTIONING.getSubStatus());
//            updateDto.setInspectionOperatorName(PkStringUtil.nullIfEmpty(request.getOperatorName()));
        }else if( eventType == AssignClaimEventType.tpa手工分配审核人员){
            updateDto.setStage(ClaimStatusEnum.Auditing.getStage().getCode());
            updateDto.setStatus(ClaimStatusEnum.Auditing.getCode());
            updateDto.setStatusSub(ClaimStatusEnum.Auditing.getSubStatus());
//            updateDto.setAuditingOperatorName(PkStringUtil.nullIfEmpty(request.getOperatorName()));
        }else if( eventType == AssignClaimEventType.tpa手工分配复核人员){
            updateDto.setStage(ClaimStatusEnum.AuditingReviewIng.getStage().getCode());
            updateDto.setStatus(ClaimStatusEnum.AuditingReviewIng.getCode());
            updateDto.setStatusSub(ClaimStatusEnum.AuditingReviewIng.getSubStatus());
//            updateDto.setReviewingOperatorName(PkStringUtil.nullIfEmpty(request.getOperatorName()));
        }else{
            throw new IllegalArgumentException("不支持的分配事件类型");
        }
        //清除挂起状态
        updateDto.setHangUpType("");
        updateDto.setHangUpStatus(HangUpStatus.NO_HANG_UP.getCode());
        claimService.updateClaim(updateDto, false, "AssignClaimServiceImpl.assignClaim");
        return rs;
    }
}
