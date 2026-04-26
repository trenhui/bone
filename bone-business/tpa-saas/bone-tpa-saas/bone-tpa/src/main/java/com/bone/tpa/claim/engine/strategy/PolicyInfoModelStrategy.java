package com.bone.tpa.claim.engine.strategy;

import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.ClaimStakeholderService;
import com.bone.tpa.intelligent.adjustment.converter.PolicyConvert;
import com.bone.tpa.intelligent.adjustment.model.PolicyInfoModel;
import com.bone.tpa.intelligent.adjustment.service.PolicyService;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * 保单信息通用处理逻辑
 *
 * 用于审核复核页面的查询，以及保单绑定
 */
@Service
public class PolicyInfoModelStrategy extends DefaultStrategy {

    private final String TABLE = "ia_policy";

    @Autowired
    private PolicyConvert policyConvert;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimStakeholderService claimStakeholderService;

    @Autowired
    private PolicyService policyService;


    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.POLICY_INFO_MODEL;
    }


    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        throw new TpaBizException(BizErrorCode.NOT_IMPLEMENTED);
    }

    @Override
    public PageResult<PolicyInfoModel> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        /**
         * 查询保单时做一个特殊处理。如果传入了relatedId，此处默认为赔案id，将会查询关联保单，并且携带计划信息
         */
        Boolean flag = false;
        String claimId = "";
        for (QueryParam queryParam : paramList) {
            if (queryParam.getField().equals("relatedId")) {
                flag = true;
                paramList.remove(queryParam);
                claimId = String.valueOf(queryParam.getValue());
            }
        }

        //如果是查询赔案的，这里要根据赔案状态调整
        if (flag) {
            Claim claim = claimService.getById(Long.valueOf(claimId));

            ClaimStakeholder outInsurePerson = claimStakeholderService.getByClaimId(claim.getId(), claim.getBizIdentityCode(),
                    claim.getTenantId(), Collections.singletonList(PersonTypeEnum.OUT_INSURE.getCode())).get(0);

            // 如果是审核页面，这里要查直付接口
            if (claim.getStage().equals(ClaimStageEnum.AUDITING.getCode())) {
                List<PolicyInfoModel> policyInfoModelList = policyService.getPolicyData(outInsurePerson.getName(), outInsurePerson.getIdentityNo(), claim.getClaimNo());

                for (PolicyInfoModel policyInfoModel : policyInfoModelList) {
                    if (policyInfoModel.getPolicyNo().equals(claim.getPolicyNo())) {
                        policyInfoModel.setIsBind(true);
                    }
                }

                return new PageResult<>(policyInfoModelList, 1, policyInfoModelList.size(), policyInfoModelList.size());
            } else if (claim.getStage().equals(ClaimStageEnum.REVIEWING.getCode())) {
                //如果是复核，这里只需要查出一条绑定的保单就好了
                List<PolicyInfoModel> policyInfoModelList = policyService.getPolicyData(outInsurePerson.getName(), outInsurePerson.getIdentityNo(), claim.getClaimNo());

                List<PolicyInfoModel> policyInfoModelReturn = new ArrayList<>();
                for (PolicyInfoModel policyInfoModel : policyInfoModelList) {
                    if (policyInfoModel.getPolicyNo().equals(claim.getPolicyNo())) {
                        policyInfoModel.setIsBind(true);
                        policyInfoModelReturn.add(policyInfoModel);
                    }
                }

//                List<PolicyInfoModel> policyInfoModelList = policyService.getPolicyDataForReviewing(claim.getClaimNo());

                return new PageResult<>(policyInfoModelReturn, 1, 1, 1);
            }

        } else {
            throw new TpaBizException(BizErrorCode.NOT_IMPLEMENTED);
        }

        return new PageResult<>();
    }

    @Override
    public void update(String modelName, Object data) {
        throw new TpaBizException(BizErrorCode.DATA_UNCHANGEABLE);
    }

    @Override
    public void delete(DeleteRequest request) {
        throw new TpaBizException(BizErrorCode.DATA_UNCHANGEABLE);
    }
}
