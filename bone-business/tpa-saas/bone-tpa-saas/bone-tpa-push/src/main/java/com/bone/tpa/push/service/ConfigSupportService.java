package com.bone.tpa.push.service;

import com.bone.tpa.push.feign.request.HospitalNameCheckRequest;
import com.bone.tpa.push.feign.request.TpaPersonalImageQueryRequest;
import com.bone.tpa.push.feign.response.*;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/9/17 16:30
 */
public interface ConfigSupportService {
    CompanyInfoResponse getCompanyInfo(String companyName);
    SystemDictResponse getSystemDictInfo(String dictType);
    HospitalNameCheckResponse getHospitalNameCheckResult(HospitalNameCheckRequest request);
    Object getDutyConfig(String policyNo);
    List<PersonalImageResponse> queryPersonalClaimImages(TpaPersonalImageQueryRequest request);
}
