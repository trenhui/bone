package com.bone.tpa.push.service.impl;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.push.service.SignRecordService;
import com.bone.tpa.sdk.claim.model.SignRecord;
import com.bone.tpa.sdk.dao.SignRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @Author feihaiming
 * @create 2025/10/22 15:28
 */
@Service
public class SignRecordServiceImpl implements SignRecordService {

    @Autowired
    private SignRecordRepository signRecordRepository;

    @Override
    public SignRecord getSignRecord(String batchNo) {
        Criteria<SignRecord> criteria = Criteria.create();
        SignRecord signRecord = signRecordRepository.findOneByCriteria(criteria.eq(SignRecord::getBatchNo, batchNo));
        if (Objects.nonNull(signRecord)) {
            return signRecord;
        }
        return null;
    }
}
