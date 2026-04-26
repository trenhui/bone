package com.bone.lowcode.infra.infrastructure.feign.util;

import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.infrastructure.feign.ZfPolicyServerClient;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfBranchCompany;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfInsuranceCompany;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfSlipInfo;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfToubaoCompany;
import com.bone.lowcode.infra.infrastructure.feign.request.ZfResult;
import com.bone.lowcode.infra.infrastructure.feign.request.ZfSlipQuery;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ZfPolicyServerUtil {

    @Resource
    private ZfPolicyServerClient zfPolicyServerClient;

    public ZfResult<List<ZfInsuranceCompany>> getTopInsuranceCompanyPage(String nameLike) {
        ZfSlipQuery zfSlipQuery = new ZfSlipQuery();
        if (StringUtils.isNotBlank(nameLike)) {
            zfSlipQuery.setTopInsuranceName(nameLike);
        }
        ZfResult<List<ZfInsuranceCompany>> re = null;
        try {
            re = zfPolicyServerClient.getTopInsuranceCompanyPage(zfSlipQuery);
        } catch (Exception e) {
            log.error("查询直付服务发生异常:", e);
            throw new ServiceException(500, "查询直付服务发生异常:" + e.getMessage());
        }

        if (!re.isSuccess()) {
            throw new ServiceException(500, "查询直付保险公司失败,响应:" + re);
        }
        return re;
    }

    public ZfResult<List<ZfBranchCompany>> getInsuranceBranchPage(String nameLike, String parentCode) {
        ZfSlipQuery zfSlipQuery = new ZfSlipQuery();
        zfSlipQuery.setInsuParentCode(parentCode);
        if (StringUtils.isNotBlank(nameLike)) {
            zfSlipQuery.setInsuranceName(nameLike);
        }
        ZfResult<List<ZfBranchCompany>> result = null;
        try {
            result = zfPolicyServerClient.getInsurancBranchPage(zfSlipQuery);
        } catch (Exception e) {
            log.error("查询直付服务发生异常:", e);
            throw new ServiceException(500, "查询直付服务发生异常:" + e.getMessage());
        }
        if (!result.isSuccess()) {
            throw new ServiceException(500, "查询直付保险公司失败,响应:" + result);
        }
        return result;
    }

    public ZfResult<List<ZfToubaoCompany>> getToubaoCompanyPage(String nameLike) {
        ZfSlipQuery zfSlipQuery = new ZfSlipQuery();
        if (StringUtils.isNotBlank(nameLike)) {
            zfSlipQuery.setCorpName(nameLike);
        }
        ZfResult<List<ZfToubaoCompany>> re = null;
        try {
            re = zfPolicyServerClient.getToubaoCompanyPage(zfSlipQuery);
        } catch (Exception e) {
            log.error("查询直付服务发生异常:", e);
            throw new ServiceException(500, "查询直付服务发生异常:" + e.getMessage());
        }

        if (!re.isSuccess()) {
            throw new ServiceException(500, "查询直付保险公司失败,响应:" + re);
        }
        return re;
    }

    public ZfResult<List<ZfSlipInfo>> getSlipInfoPage(String nameLike, String parentName) {
        ZfSlipQuery zfSlipQuery = new ZfSlipQuery();
        zfSlipQuery.setInsuranceName(parentName);
        if (StringUtils.isNotBlank(nameLike)) {
            zfSlipQuery.setSlipCode(nameLike);
        }
        ZfResult<List<ZfSlipInfo>> re = null;
        try {
            re = zfPolicyServerClient.getSlipInfoPage(zfSlipQuery);
        } catch (Exception e) {
            log.error("查询直付服务发生异常:", e);
            throw new ServiceException(500, "查询直付服务发生异常:" + e.getMessage());
        }
        if (!re.isSuccess()) {
            throw new ServiceException(500, "查询直付保险公司失败,响应:" + re);
        }
        return re;
    }
}
