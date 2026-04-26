package com.bone.tpa.sdk.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.claim.model.ClaimCopyLog;
import com.bone.tpa.sdk.dao.ClaimCopyLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 赔案复制记录服务
 */
@Service
public class ClaimCopyLogBasicService {
    @Autowired
    private ClaimCopyLogRepository claimCopyLogRepository;

    public ClaimCopyLog getClaimCopyLogByNewId(Long newClaimId) {
        Criteria<ClaimCopyLog> criteria = new Criteria<>();
        criteria.eq(ClaimCopyLog::getNewClaimId, newClaimId);

        List<ClaimCopyLog> claimCopyLogList = claimCopyLogRepository.findByCriteria(criteria);

        if (claimCopyLogList == null || claimCopyLogList.isEmpty()) {
            return null;
        }

        return claimCopyLogList.get(0);
    }


}
