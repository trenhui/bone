package com.bone.tpa.push.service;

import com.bone.tpa.push.feign.request.HospitalNameCheckRequest;
import com.bone.tpa.push.feign.request.TpaPersonalImageQueryRequest;
import com.bone.tpa.push.feign.response.*;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:38
 */
public abstract class ConfigAdapterAbstractHandler {
    public abstract CompanyInfoResponse handleCompanyInfo(String companyId);
    public abstract SystemDictResponse handleSystemDictInfo(String dictType);
    public abstract HospitalNameCheckResponse handleHospitalExistsCheck(HospitalNameCheckRequest request);
    public abstract <T> T handleSDutyConfigs(String policyNo);
    public abstract List<PersonalImageResponse> handlePersonalClaimImages(TpaPersonalImageQueryRequest request);
}
