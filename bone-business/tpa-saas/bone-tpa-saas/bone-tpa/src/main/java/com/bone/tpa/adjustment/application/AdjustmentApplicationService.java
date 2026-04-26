package com.bone.tpa.adjustment.application;

import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.claim.domain.service.ClaimInvoiceService;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.intelligent.adjustment.engine.AdjustEngine;
import com.bone.tpa.intelligent.adjustment.model.AdjustConclusion;
import com.bone.tpa.intelligent.adjustment.model.AdjustResult;
import com.bone.tpa.intelligent.adjustment.model.AdjustmentInfo;
import com.bone.tpa.sdk.claim.enums.VisitTypeEnum;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.impl.ClaimInvoiceRepository;
import com.bone.tpa.intelligent.adjustment.model.LiabilityToBind;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.intelligent.adjustment.service.LiabilityInfoService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityService;
import com.bone.tpa.intelligent.adjustment.service.PlanService;
import com.bone.tpa.sdk.adjustment.response.ClaimAdjustmentResponse;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AdjustmentApplicationService {

    @Autowired
    private AdjustEngine adjustEngine;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimInvoiceService claimInvoiceService;

    @Autowired
    private ClaimInvoiceRepository claimInvoiceRepository;

    @Autowired
    private PlanService planService;

    @Autowired
    private LiabilityService liabilityService;

    @Autowired
    private LiabilityInfoService liabilityInfoService;


    /**
     * 搜索该赔案所对应的全部责任，并且可以多选
     */
    public List<LiabilityToBind> queryLiabilityToBind(Long claimId, String visitTypeCn) {

        Claim claim = claimRepository.findById(claimId);

        if (claim.getPolicyNo() == null) {
            return new ArrayList<>();
        }

        if (claim.getPlanUuid() == null) {
            //todo 这里先返回空。正常应该有兜底或者特殊逻辑
            return new ArrayList<>();
        }

        PlanDTO plan = planService.queryByPlanUuid(claim.getPlanUuid());

        if (plan == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "计划未配置！");
        }

        List<LiabilityConfig> liabilityList = liabilityService.queryForBind(plan.getId());

        //筛掉后付责任
//        List<LiabilityConfig> filteredList = liabilityInfoService.filterNextLiability(liabilityList);

//        List<String> liabilityNameList = liabilityList.stream().map(LiabilityConfig::getLiabilityName).collect(Collectors.toList());

        //转化为结果
        List<LiabilityToBind> result = new ArrayList<>();
        for (LiabilityConfig liabilityConfig : liabilityList) {
            result.add(liabilityInfoService.liabilityShortConverter(liabilityConfig, VisitTypeEnum.getByValue(visitTypeCn)));
        }

        return result;
    }



    public AdjustmentInfo adjustClaim(Long claimId) {
        //这里判定是否报案
        Claim claim = claimRepository.findById(claimId);

        if (claim.getInsurerClaimNo() == null || claim.getInsurerClaimNo().isBlank()) {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "永诚保单需先报案!");
        }

        clear(claimId);

        ClaimAdjustmentResponse adjustResponse = adjustEngine.adjustClaim(claimId);

        AdjustmentInfo adjustmentInfo = new AdjustmentInfo();
        //理算过程信息
        adjustmentInfo.setAdjustmentDetailList(adjustResponse.getInvoiceResults());

        //理算结果信息
        AdjustResult adjustResult = new AdjustResult();
        adjustResult.setPayOutAmount(adjustResponse.getPayoutAmount());
        adjustResult.setPublicAmount(adjustResponse.getPublicAmount());
        adjustResult.setPrivateAmount(adjustResponse.getIndividualAmount());
        adjustmentInfo.setAdjustmentResult(adjustResult);

        //理算结论信息
        AdjustConclusion adjustConclusion = new AdjustConclusion();
        adjustConclusion.setPayOutConclusion(adjustResponse.getResult());
        adjustConclusion.setConclusionDetail(adjustResponse.getResultDetail());

        return adjustmentInfo;
    }


    public void clear(Long claimId) {
        adjustEngine.clearClaimAdjustment(claimId);
    }


    public AdjustmentInfo readjustClaim(Long claimId) {
        clear(claimId);
        return adjustClaim(claimId);
    }
}
