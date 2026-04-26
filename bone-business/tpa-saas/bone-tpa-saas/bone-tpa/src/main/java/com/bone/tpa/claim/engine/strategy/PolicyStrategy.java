package com.bone.tpa.claim.engine.strategy;

import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.intelligent.adjustment.converter.PolicyConvert;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.intelligent.adjustment.dto.PolicyDTO;
import com.bone.tpa.sdk.dao.impl.PolicyRepository;
import com.bone.tpa.intelligent.adjustment.service.PlanService;
import com.bone.tpa.sdk.adjustment.enums.PlanStatus;
import com.bone.tpa.sdk.adjustment.model.Policy;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * 保单通用处理逻辑
 *
 * 仅用于查询保单而已
 */
@Service
public class PolicyStrategy extends DefaultStrategy {

    private final String TABLE = "ia_policy";

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyConvert policyConvert;


    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.POLICY;
    }


    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        Policy policy = policyRepository.findById(request.getId());

        GenericQueryResponse response = new GenericQueryResponse(policyConvert.toDto(policy));
        response.setTenantId(String.valueOf(policy.getTenantId()));

        return response;
    }

    @Override
    public PageResult<PolicyDTO> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        /**
         * 如果传入了claimId证明传错了，帮他去掉
         */
        for (QueryParam queryParam : paramList) {
            if (queryParam.getField().equals("relatedId")) {
                paramList.remove(queryParam);
            }
        }

        PageResult<Policy> result = policyRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, null);

        List<Policy> policyList = result.getData();
        List<PolicyDTO> policyDTOList = new ArrayList<>();
        for (Policy policy : policyList) {
            PolicyDTO dto = policyConvert.toDto(policy);
            policyDTOList.add(dto);
        }

        return new PageResult<>(policyDTOList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
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
