package com.bone.tpa.claim.domain.service;

import com.bone.core.result.PageResult;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.claim.application.converter.ClaimCopyLogConverter;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimCopyLog;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.InvoiceProjectItem;
import com.bone.tpa.sdk.dao.ClaimCopyLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 赔案复制记录服务
 */
@Service
public class ClaimCopyLogService {
    @Autowired
    private ClaimCopyLogConverter claimCopyLogConverter;

    @Autowired
    private ClaimCopyLogRepository claimCopyLogRepository;


    private final String tableName = "ss_claim_copy_log";

    /**
     * 查询发票下面的所有费用项目详情
     *
     * @param request   列表查询请求
     * @return
     */
    public PageResult<ClaimCopyLog> getClaimCopyLog(QueryListRequest request) {
        return claimCopyLogRepository.queryByCondition(request.getQueryParams(), request.getSortingFields(), (request.getPageNo() - 1) * request.getPageSize(), request.getPageSize(),
                                tableName, null);
    }

    public ClaimCopyLog getClaimCopyLogByNewId(Long newClaimId) {
        Criteria<ClaimCopyLog> criteria = new Criteria<>();
        criteria.eq(ClaimCopyLog::getNewClaimId, newClaimId);

        List<ClaimCopyLog> claimCopyLogList = claimCopyLogRepository.findByCriteria(criteria);

        if (claimCopyLogList == null || claimCopyLogList.isEmpty()) {
            return null;
        }

        return claimCopyLogList.get(0);
    }

    public void insertBatch(List<ClaimCopyLog> claimCopyLogList) {
        claimCopyLogRepository.insertBatch(claimCopyLogList);
    }

}
