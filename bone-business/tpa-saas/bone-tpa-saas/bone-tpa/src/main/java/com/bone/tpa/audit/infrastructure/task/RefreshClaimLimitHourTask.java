package com.bone.tpa.audit.infrastructure.task;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.core.result.PageResult;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.bone.tpa.sdk.constants.BizConstant.DEFAULT_PAGE_SIZE;

@Component
@Slf4j
public class RefreshClaimLimitHourTask {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimRepository claimRepository;

    /**
     * 审核中的案件刷新时效
     */
//    @XxlJob("refreshClaimLimitHourTask")
    @Scheduled(cron = "0 0/20 * * * ?")
    public void refreshClaimLimitHour() {
        Criteria<Claim> criteria = Criteria.create();
        criteria.in(Claim::getStage, ClaimStageEnum.getExceptStage(ClaimStageEnum.FINISH, ClaimStageEnum.INIT));
        criteria.in(Claim::getStatus, ClaimStatusEnum.getExceptStatus(ClaimStatusEnum.COMPLETE_AUDIT, ClaimStatusEnum.Finish,
                ClaimStatusEnum.Cancel, ClaimStatusEnum.DRAFT));
        Long count = claimRepository.countByCriteria(criteria);
        log.info("定时更新赔案时效, 共{}个赔案", count);
        long pageCount = (count + DEFAULT_PAGE_SIZE - 1) / DEFAULT_PAGE_SIZE;
        criteria.addSort(Criteria.getDefaultIdSort());
        for (int i = 1; i <= pageCount; i++) {
            List<Claim> updateObj = new ArrayList<>();

            criteria.page(DEFAULT_PAGE_SIZE, (i - 1) * DEFAULT_PAGE_SIZE);
            PageResult<Claim> pageResult = claimRepository.pageByCriteria(criteria);
            List<Claim> claimList = pageResult.getData();
            if (CollectionUtil.isEmpty(claimList)) {
                continue;
            }

            log.info("更新赔案时效批次{}", i);
            for (Claim claim : claimList) {
                Claim obj = new Claim();
                obj.setId(claim.getId());
                obj.setLimitHour(claimService.limitHourCalculator(claim));

                updateObj.add(obj);
            }

            claimService.batchUpdateClaim(updateObj, OperationTypeEnum.UPDATE_LIMIT_HOUR);
        }
    }
}
