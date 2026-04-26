package com.bone.lowcode.infra.infrastructure.feign;

import com.bone.lowcode.infra.infrastructure.feign.bean.ZfBranchCompany;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfInsuranceCompany;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfSlipInfo;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfToubaoCompany;
import com.bone.lowcode.infra.infrastructure.feign.request.ZfResult;
import com.bone.lowcode.infra.infrastructure.feign.request.ZfSlipQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient("pukang-policy-server")
public interface ZfPolicyServerClient {


    /**
     * 查询保险公司
     * http://api.test0.pukangpay.com.cn/project/319/interface/api/70184
     *
     * @param request
     * @return
     */
    @PostMapping("/insu/getTopInsurancePage")
    ZfResult<List<ZfInsuranceCompany>> getTopInsuranceCompanyPage(@RequestBody ZfSlipQuery request);

    /**
     * 保险分公司查询
     * http://api.test0.pukangpay.com.cn/project/319/interface/api/70177
     *
     * @param request
     * @return
     */
    @PostMapping("/insu/getInsurancePage")
    ZfResult<List<ZfBranchCompany>> getInsurancBranchPage(@RequestBody ZfSlipQuery request);

    /**
     * 投保公司查询
     * http://api.test0.pukangpay.com.cn/project/319/interface/api/70156
     *
     * @param request
     * @return
     */
    @PostMapping("/insu/getInsuCorpPage")
    ZfResult<List<ZfToubaoCompany>> getToubaoCompanyPage(@RequestBody ZfSlipQuery request);

    /**
     * 查询保单信息
     * http://api.test0.pukangpay.com.cn/project/319/interface/api/70184
     *
     * @param request
     * @return
     */
    @PostMapping("/insu/getInsuSlipPage")
    ZfResult<List<ZfSlipInfo>> getSlipInfoPage(@RequestBody ZfSlipQuery request);

}
