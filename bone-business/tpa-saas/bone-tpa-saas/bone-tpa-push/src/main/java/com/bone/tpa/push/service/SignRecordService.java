package com.bone.tpa.push.service;

import com.bone.tpa.sdk.claim.model.SignRecord;

/**
 * @Author feihaiming
 * @create 2025/10/22 15:27
 */
public interface SignRecordService {
    SignRecord getSignRecord(String batchNo);
}
