package com.bone.tpa.adjustment.application;

import com.bone.core.exception.ServiceException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.claim.application.enums.CertificateConfigEnums;
import com.bone.tpa.claim.application.request.PolicyCertificateConfig;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.vo.InsuranceCompanyImageVO;
import com.bone.tpa.sdk.adjustment.model.Policy;
import com.bone.tpa.sdk.adjustment.response.CertificateConfig;
import com.bone.tpa.sdk.dao.impl.PolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CertificateConfigApplicationService {

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;

    public Object getOptions(String insuranceName, String certificateType) {
        if (!"理赔申请书".equals(certificateType) && !"理赔通知书".equals(certificateType)) {
            throw new ServiceException(500, "certificateType参数目前只支持:理赔申请书、理赔通知书");
        }
        ApiResult<List<InsuranceCompanyImageVO>> remoteCategory = tpaDataSyncFeign.getImageMapDetail(insuranceName);
        if (!remoteCategory.isSuccess()) {
            throw new ServiceException(500, "获取tpa影像分类失败,保险公司名称:" + insuranceName);
        }
        List<InsuranceCompanyImageVO> typeVOList = remoteCategory.getData();
        if (CollectionUtils.isEmpty(typeVOList)) {
            throw new ServiceException(500, "tpa返回影像分类为空,保险公司名称:" + insuranceName);
        }
        List<Map<String, String>> imageTypeList = typeVOList.stream().map(i -> Map.of(
                "imageType", i.getImageClassifyCode(),
                "imageTypePK", i.getImageMapCode(),
                "desc", i.getFieldName()
        )).toList();

        HashMap<String, List> res = new HashMap<>();
        res.put("claimAuditType", Arrays.asList(CertificateConfigEnums.ClaimAuditTypeEnum.values()));
        if ("理赔申请书".equals(certificateType)) {
            res.put("certificateTemplate", List.of(CertificateConfigEnums.CertificateTemplateEnum.ITEM2));
        } else if ("理赔通知书".equals(certificateType)) {
            res.put("certificateTemplate", List.of(CertificateConfigEnums.CertificateTemplateEnum.ITEM1));
        }
        res.put("claimProcessNode", Arrays.asList(CertificateConfigEnums.ClaimProcessNodeEnum.values()));
        res.put("adjustmentConclusion", Arrays.asList(CertificateConfigEnums.AdjustmentConclusionEnum.values()));
        res.put("imageType", imageTypeList);
        return res;
    }

    public CertificateConfig getOptionByPolicyNo(String policyNo) {
        Criteria<Policy> policyCriteria = Criteria.create();
        policyCriteria.eq(Policy::getPolicyNo, policyNo);
        List<Policy> policyList = policyRepository.findByCriteria(policyCriteria);
        if (CollectionUtils.isEmpty(policyList) || policyList.size() > 1) {
            throw new ServiceException(500, "目标保单不存在或数量大于1,保单号:" + policyNo);
        }

        Policy policy = policyList.get(0);
        CertificateConfig config = policy.queryCertificateConfig();
        return config;
    }

    public boolean save(PolicyCertificateConfig param) {
        Criteria<Policy> policyCriteria = Criteria.create();
        policyCriteria.eq(Policy::getPolicyNo, param.getPolicyNo());
        List<Policy> policyList = policyRepository.findByCriteria(policyCriteria);
        if (CollectionUtils.isEmpty(policyList) || policyList.size() > 1) {
            throw new ServiceException(500, "目标保单不存在或数量大于1,保单号:" + param.getPolicyNo());
        }

        Policy policy = new Policy();
        policy.setId(policyList.get(0).getId());
        policy.updateCertificateConfig(param.getConfig());
        policyRepository.update(policy);
        return true;
    }
}
