package com.bone.tpa.intelligent.adjustment.service;

import com.bone.core.result.PageResult;
import com.bone.tpa.intelligent.adjustment.dto.PolicyDTO;
import com.bone.tpa.intelligent.adjustment.dto.request.PolicyQueryRequest;
import com.bone.tpa.intelligent.adjustment.model.PolicyInfoModel;
import com.bone.tpa.sdk.adjustment.model.Policy;

import java.util.List;

public interface PolicyService {

    void updatePolicyConfigStatus(String policyNo);

    /**
     * 分页查询
     * @param pageQuery
     * @return
     */
    PageResult<PolicyDTO> queryPageByCondition(PolicyQueryRequest pageQuery);


    /**
     * 获取直付保单信息
     *
     * @param personName      人员姓名
     * @param personCertId    人员身份证号
     * @return
     */
    List<PolicyInfoModel> getPolicyData(String personName, String personCertId, String claimNo);


    List<PolicyInfoModel> getPolicyDataForReviewing(String claimNo);



    Boolean savePolicyInfo(PolicyInfoModel policyNo, Long claimId);
}
