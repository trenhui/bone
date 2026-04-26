package com.bone.tpa.audit.domain.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.audit.infrastructure.feign.request.TpaHistoryClaimQueryRequest;
import com.bone.tpa.audit.infrastructure.feign.response.TpaHistoryClaimQueryResponse;
import com.bone.tpa.sdk.adjustment.model.AdjustmentResult;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.ClaimStakeholderRepository;
import com.bone.tpa.sdk.dao.impl.AdjustmentResultRepository;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * @Author feihaiming
 * @create 2025/9/28 10:53
 */
@Service
@Slf4j
public class ClaimAuditService {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private AdjustmentResultRepository adjustmentResultRepository;

    @Autowired
    private ClaimStakeholderRepository claimStakeholderRepository;

    public List<TpaHistoryClaimQueryResponse> queryHistoryClaims(TpaHistoryClaimQueryRequest request) {
        Criteria<ClaimStakeholder> criteria = new Criteria<>();
        criteria.eq(ClaimStakeholder::getIdentityNo, request.getOutIdentityNo())
                .eq(ClaimStakeholder::getName, request.getOutUserName())
                .eq(ClaimStakeholder::getPersonType, PersonTypeEnum.OUT_INSURE.getCode());
        List<ClaimStakeholder> stakeholderList = claimStakeholderRepository.findByCriteria(criteria);

        if (CollectionUtils.isEmpty(stakeholderList)) {
            return Collections.emptyList();
        }
        List<Long> claimNos = stakeholderList.stream().map(ClaimStakeholder::getRelatedId).distinct().collect(Collectors.toList());
        Criteria<Claim> claimCriteria = new Criteria<>();
        claimCriteria.in(Claim::getClaimNo, claimNos)
                .eq(Claim::getInsuranceName, request.getInsuranceName());
        List<Claim> claimList = claimRepository.findByCriteria(claimCriteria);
        if (CollectionUtils.isEmpty(claimList)) {
            return Collections.emptyList();
        }

        Criteria<AdjustmentResult> adjustmentCriteria = new Criteria<>();
        adjustmentCriteria.in(AdjustmentResult::getClaimId, claimNos);
        List<AdjustmentResult> adjustmentResults = adjustmentResultRepository.findByCriteria(adjustmentCriteria);
        Map<Long, AdjustmentResult> adjustmentResultMap = Maps.newHashMap();
        if (!CollectionUtils.isEmpty(adjustmentResults)) {
            adjustmentResultMap = adjustmentResults.stream().collect(Collectors.toMap(AdjustmentResult::getClaimId, Function.identity(), (a, b) -> a));
        }
        Map<Long, AdjustmentResult> finalAdjustmentResultMap = adjustmentResultMap;
        return claimList.stream().map(c -> {
            TpaHistoryClaimQueryResponse response = new TpaHistoryClaimQueryResponse();
            response.setClaimNo(c.getClaimNo());
            response.setTaskNo(c.getInsurerClaimNo());
            response.setClaimStatus(getClaimStatusName(c.getStatus()));
            response.setPolicyNo(c.getPolicyNo());
            response.setPersonCertId(request.getOutIdentityNo());
            response.setPersonName(request.getOutUserName());
            response.setInsuranceName(c.getInsuranceName());
            response.setInsureName(c.getInsureName());

            Integer iv = null;
            if (StringUtils.isNotBlank(c.getStatus())) {
                iv = Integer.valueOf(c.getStatus());
            }
            if (iv != null && iv >= 42 && !ObjectUtil.equals(iv, 46) && finalAdjustmentResultMap.containsKey(c.getId())) {
                AdjustmentResult adjustmentResult = finalAdjustmentResultMap.get(c.getId());
                BigDecimal val = adjustmentResult.getPublicAmount() == null ? BigDecimal.ZERO : adjustmentResult.getPublicAmount();
                response.setPublicAmount(String.valueOf(val));
                val = adjustmentResult.getPayoutAmount() == null ? BigDecimal.ZERO : adjustmentResult.getPayoutAmount();
                response.setCompensationAmount(val);
                List<String> strings = adjustmentResult.getResultCode();
                if (CollectionUtil.isNotEmpty(strings)) {
                    if (StringUtils.equals(strings.get(0), "D")) {
                        response.setIsReject("拒赔");
                    } else {
                        response.setIsReject("赔付");
                    }
                }

                Date finishTime = null;
                String auditUser = null;
                if (c.getStage().equals(ClaimStageEnum.FINISH.getCode())) {
                    if (c.getReviewingPassTime() == null) {
                        if (c.getAuditingPassTime() != null) {
                            finishTime = c.getAuditingPassTime();
                            auditUser = c.getAuditingOperatorName();
                        }
                    } else {
                        finishTime = c.getReviewingPassTime();
                        auditUser = c.getReviewingOperatorName();
                    }
                }
                if (finishTime != null) {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    response.setAuditDate(simpleDateFormat.format(finishTime));
                }

                response.setAuditUser(auditUser);
            }

            // 赔案颜色标记
            if ("1".equals(c.getHangUpStatus())) {
                response.setColorMark(1);
            } else {
                response.setColorMark(0);
            }

            return response;
        }).collect(Collectors.toList());
    }

    private String getClaimStatusName(String claimStatus) {
        // 当前环节
        if ("1".equals(claimStatus)) {
            return "签收";
        } else if ("10".equals(claimStatus) || "11".equals(claimStatus)) {
            return "初审";
        } else if ("12".equals(claimStatus) || "21".equals(claimStatus) || "22".equals(claimStatus) || "23".equals(claimStatus)) {
            return "录入";
        } else if ("40".equals(claimStatus) || "41".equals(claimStatus)) {
            return "审核";
        } else if ("42".equals(claimStatus)) {
            return "推送";
        } else if ("50".equals(claimStatus)) {
            return "完成";
        }

        return null;
    }
}
