package com.bone.tpa.claim.domain.service;


import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.dao.SignRecordTrackLogRepository;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.model.SignRecord;
import com.bone.tpa.sdk.claim.model.SignRecordTrackLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;


/**
 * ss_sign_record_track_log Service 接口
 *
 * @author 0
 */
@Service
public class SignRecordTrackLogService {
    @Autowired
    private SignRecordTrackLogRepository signRecordTrackLogRepository;

    /**
     * 创建操作记录
     */
    public void operationRecord(SignRecord signRecord, OperationTypeEnum type, String... remark) {
        //创建发票相关的操作记录
        SignRecordTrackLog signRecordTrackLog = new SignRecordTrackLog();

        signRecordTrackLog.setTenantId(signRecord.getTenantId());
        signRecordTrackLog.setRelatedSignRecordId(signRecord.getId());
        signRecordTrackLog.setSignStatus(signRecord.getSignStatus());
        signRecordTrackLog.setType(type.getCode());
        signRecordTrackLog.setMessage(type.getValue());
        signRecordTrackLog.setRemark(Arrays.toString(remark));
        signRecordTrackLog.setOperator(BizContextUtils.getUser());

        //操作记录持久化
        signRecordTrackLogRepository.save(signRecordTrackLog);
    }


    /**
     * 查询操作记录
     */
    public List<SignRecordTrackLog> queryClaimRecord(Long signId, OperationTypeEnum... queryOption) {
        Criteria<SignRecordTrackLog> criteria = new Criteria<>();

        criteria.eq(SignRecordTrackLog::getRelatedSignRecordId, signId);

        if (queryOption != null && queryOption.length != 0){
            criteria.in(SignRecordTrackLog::getType, Arrays.stream(queryOption).map(OperationTypeEnum::getCode).toList());
        }

        return signRecordTrackLogRepository.findByCriteria(criteria);
    }

}
