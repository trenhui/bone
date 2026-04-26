package com.bone.tpa.sdk.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.adjustment.enums.QuotaStatusEnum;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.adjustment.model.AdjustmentResult;
import com.bone.tpa.sdk.dao.impl.AdjustmentRecordRepository;
import com.bone.tpa.sdk.dao.impl.AdjustmentResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 理算
 * 数据准备服务
 *
 * 负责查询理算的基础数据
 */
@Service
@Slf4j
public class AdjustmentRecordBasicService {

    @Autowired
    private AdjustmentRecordRepository adjustmentRecordRepository;

    @Autowired
    private AdjustmentResultRepository adjustmentResultRepository;


    /**
     * 获取和该赔案相关的理算记录
     */
    public List<AdjustmentRecord> getAdjustmentRecordByClaim(Long claimId, Boolean includeOngoing) {
        Criteria<AdjustmentRecord> criteria = Criteria.create();
        criteria.eq("relatedId", claimId);

        if (!includeOngoing) {
            criteria.in("recordStatus", List.of(QuotaStatusEnum.CONFIRMED.getCode()));
        } else {
            criteria.in("recordStatus", List.of(QuotaStatusEnum.CONFIRMED.getCode(), QuotaStatusEnum.FROZEN.getCode()));
        }

        List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordRepository.findByCriteria(criteria);

        return adjustmentRecordList;
    }

    /**
     * 获取和该赔案相关的理算记录，给推送用的
     */
    public List<AdjustmentRecord> getAdjustmentRecordForPush(Long claimId) {
        Criteria<AdjustmentRecord> criteria = Criteria.create();
        criteria.eq("relatedId", claimId);

        criteria.in("recordStatus", List.of(QuotaStatusEnum.FAILED.getCode(), QuotaStatusEnum.FROZEN.getCode()));

        List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordRepository.findByCriteria(criteria);

        return adjustmentRecordList;
    }

    /**
     * 获取和该赔案相关的理算记录
     */
    public List<AdjustmentRecord> getAllAdjustmentRecordByClaim(Long claimId) {
        Criteria<AdjustmentRecord> criteria = Criteria.create();
        criteria.eq("relatedId", claimId);

        List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordRepository.findByCriteria(criteria);

        return adjustmentRecordList;
    }


    /**
     * 获取和该赔案相关的理算记录
     */
    public List<AdjustmentResult> getAdjustmentResultByClaim(Long claimId, Boolean includeOngoing) {
        Criteria<AdjustmentResult> criteria = Criteria.create();
        criteria.eq("claimId", claimId);

        if (!includeOngoing) {
            criteria.eq("resultStatus", QuotaStatusEnum.CONFIRMED.getCode());
        }

        criteria.setSortWords("order by id desc");

        List<AdjustmentResult> adjustmentResult = adjustmentResultRepository.findByCriteria(criteria);
        
        return adjustmentResult;
    }

}
