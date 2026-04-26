package com.bone.tpa.push.service.impl;

import com.bone.tpa.push.feign.request.HospitalNameCheckRequest;
import com.bone.tpa.push.feign.request.TpaPersonalImageQueryRequest;
import com.bone.tpa.push.feign.response.CompanyInfoResponse;
import com.bone.tpa.push.feign.response.HospitalNameCheckResponse;
import com.bone.tpa.push.feign.response.PersonalImageResponse;
import com.bone.tpa.push.feign.response.SystemDictResponse;
import com.bone.tpa.push.service.ConfigAdapterAbstractHandler;
import com.bone.tpa.push.service.ConfigSupportService;
import com.bone.tpa.sdk.adjustment.model.LiabilityMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:44
 */
@Service("pushConfigAdapterHandler")
public class ConfigAdapterHandler extends ConfigAdapterAbstractHandler {

    @Autowired
    @Qualifier("saasConfigSupportServiceImpl")
    private ConfigSupportService configSupportService;

    @Override
    public CompanyInfoResponse handleCompanyInfo(String companyName) {
        CompanyInfoResponse companyInfo = configSupportService.getCompanyInfo(companyName);
        return companyInfo;
    }

    @Override
    public SystemDictResponse handleSystemDictInfo(String dictType) {
        SystemDictResponse systemDictInfo = configSupportService.getSystemDictInfo(dictType);
        return systemDictInfo;
    }

    @Override
    public HospitalNameCheckResponse handleHospitalExistsCheck(HospitalNameCheckRequest request) {
        HospitalNameCheckResponse hospitalNameCheckResult = configSupportService.getHospitalNameCheckResult(request);
        return hospitalNameCheckResult;
    }

    @Override
    public List<LiabilityMapping> handleSDutyConfigs(String policyNo) {
        return (List<LiabilityMapping>) configSupportService.getDutyConfig(policyNo);
    }

    @Override
    public List<PersonalImageResponse> handlePersonalClaimImages(TpaPersonalImageQueryRequest request) {
        return configSupportService.queryPersonalClaimImages(request);
    }
}
