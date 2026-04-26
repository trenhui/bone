package com.bone.tpa.claim.domain.service;


import com.alibaba.fastjson.JSONObject;
import com.bone.metadata.sdk.MetadataFetchEngine;
import com.bone.metadata.sdk.SdkPropertyConfig;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.criteria.SortItem;
import com.bone.metadata.sdk.query.criteria.SqlSortTypeEnums;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.ClaimTrackLogRepository;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.enums.TpaHangupReason;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimTrackLog;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * ss_claim_track_log Service 接口
 *
 * @author 0
 */
@Slf4j
@Service
public class ClaimTrackLogService {
    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimTrackLogRepository claimTrackLogRepository;

    public void addActionRecord(Claim claim,String operator,
                                Map<String,Object> extraStore,OperationTypeEnum type,String ... remark){
        operationRecord(claim, null, null, type,extraStore, null,operator, remark);

    }
    /**
     * 不同操作主体存储操作记录的入口
     */
    public void claimRecord(Claim claim,String operator, String beforeValue, String afterValue, OperationTypeEnum type,
                            BizModelEnum object, String... remark) {
        operationRecord(claim, beforeValue, afterValue, type,null, object,operator, remark);
    }



    @Async
    public void deleteRecord(Long claimId, Long id, BizModelEnum object) {
        Criteria<Claim> criteria = new Criteria<>();
        criteria.eq(Claim::getId, claimId);

        Claim claim = claimRepository.findOneByCriteria(criteria, true);
        operationRecord(claim, null, null, OperationTypeEnum.DELETE,null, object, String.valueOf(id));
    }


    /**
     * 创建操作记录
     */
    private void operationRecord(Claim claim, String beforeValue, String afterValue,
                                 OperationTypeEnum type,Map<String,Object> extraStore, BizModelEnum object,String operator, String... remark) {
        //创建发票相关的操作记录
        ClaimTrackLog claimTrackLog = new ClaimTrackLog();

        claimTrackLog.setTenantId(claim.getTenantId());
        claimTrackLog.setRelatedClaimId(claim.getId());
        claimTrackLog.setRelatedClaimNo(claim.getClaimNo());
        claimTrackLog.setStage(claim.getStage());
        claimTrackLog.setType(type.getCode());
        claimTrackLog.setBeforeValue(beforeValue);
        claimTrackLog.setAfterValue(afterValue);
        if(extraStore != null){
            claimTrackLog.setExtraStore(JSONObject.toJSONString(extraStore));
        }
        if(object!= null){
            claimTrackLog.setMessage(type.getValue()+object.getValue());
        }
        claimTrackLog.setOperator(operator);
        if(claimTrackLog.getOperator() == null){
            claimTrackLog.setOperator("system");
        }
        if (remark != null && remark.length > 0) {
            claimTrackLog.setRemark(Arrays.toString(remark));
        }

        //操作记录持久化
        claimTrackLogRepository.insert(claimTrackLog);
    }


    /**
     * 异常记录
     * 用于挂起或退回
     */
    public Long exceptionRecord(Claim claim, OperationTypeEnum type, String detailType, String reason) {
        //创建发票相关的操作记录
        ClaimTrackLog claimTrackLog = new ClaimTrackLog();

        claimTrackLog.setTenantId(claim.getTenantId());
        claimTrackLog.setRelatedClaimId(claim.getId());
        claimTrackLog.setRelatedClaimNo(claim.getClaimNo());
        claimTrackLog.setStage(claim.getStage());
        claimTrackLog.setType(type.getCode());
        claimTrackLog.setMessage(detailType);
        claimTrackLog.setRemark(reason);
        claimTrackLog.setOperator(BizContextUtils.getUser());

        //操作记录持久化
        return   claimTrackLogRepository.insert(claimTrackLog);
    }


    /**
     * 查询操作记录
     */
    public List<ClaimTrackLog> queryClaimRecord(Long claimId, OperationTypeEnum... queryOption) {
        Criteria<ClaimTrackLog> criteria = new Criteria<>();

        criteria.eq(ClaimTrackLog::getRelatedClaimId, claimId);

        if (queryOption != null && queryOption.length != 0){
            criteria.in(ClaimTrackLog::getType, Arrays.stream(queryOption).map(OperationTypeEnum::getCode).toList());
        }

        return claimTrackLogRepository.findByCriteria(criteria);
    }


    /**
     * 查询一条最晚的操作记录
     */
    public ClaimTrackLog queryLatestClaimRecord(Long claimId, OperationTypeEnum... queryOption) {
        Criteria<ClaimTrackLog> criteria = new Criteria<>();

        criteria.eq(ClaimTrackLog::getRelatedClaimId, claimId);
        criteria.setSortWords("order by id desc");

        if (queryOption != null && queryOption.length != 0){
            criteria.in(ClaimTrackLog::getType, Arrays.stream(queryOption).map(OperationTypeEnum::getCode).toList());
        }

        List<ClaimTrackLog> claimTrackLogList = claimTrackLogRepository.findByCriteria(criteria);

        if (claimTrackLogList == null || claimTrackLogList.isEmpty()) {
            return null;
        }

        return claimTrackLogList.get(0);
    }


    /**
     * 查询一条最晚的操作记录
     */
    public ClaimTrackLog queryLatestClaimRecordByStage(Long claimId, ClaimStageEnum stage, OperationTypeEnum... queryOption) {
        Criteria<ClaimTrackLog> criteria = new Criteria<>();

        criteria.eq(ClaimTrackLog::getRelatedClaimId, claimId);
        criteria.setSortWords("order by id desc");

        //查询这个阶段的列表
        criteria.eq(ClaimTrackLog::getStage, stage.getCode());

        if (queryOption != null && queryOption.length != 0){
            criteria.in(ClaimTrackLog::getType, Arrays.stream(queryOption).map(OperationTypeEnum::getCode).toList());
        }

        List<ClaimTrackLog> claimTrackLogList = claimTrackLogRepository.findByCriteria(criteria);

        if (claimTrackLogList == null || claimTrackLogList.isEmpty()) {
            return null;
        }
        

        return claimTrackLogList.get(0);
    }
}
