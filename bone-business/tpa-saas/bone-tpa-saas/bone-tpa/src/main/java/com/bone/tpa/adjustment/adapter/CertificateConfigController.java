package com.bone.tpa.adjustment.adapter;

import com.bone.core.result.Result;
import com.bone.tpa.adjustment.application.CertificateConfigApplicationService;
import com.bone.tpa.claim.application.CreateCertificateApplicationService;
import com.bone.tpa.claim.application.request.PolicyCertificateConfig;
import com.bone.tpa.sdk.adjustment.response.CertificateConfig;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/tpa/certificateConfig")
public class CertificateConfigController {


    @Autowired
    private CertificateConfigApplicationService applicationService;

    /**
     * 获取单证配置的下拉框选项值
     */
    @GetMapping("/getOptions")
    public Result getOption(@NotEmpty(message = "insuranceName不能为空") @RequestParam("insuranceName") String insuranceName,
                            @RequestParam("certificateType") String certificateType) {
        Object re = applicationService.getOptions(insuranceName, certificateType);
        return Result.ok(re);
    }

    /**
     * 获取指定保单下的单证配置
     */
    @GetMapping("/getOptionByPolicyNo")
    public Result getOptionByPolicyNo(@NotEmpty(message = "policyNo不能为空") @RequestParam("policyNo") String policyNo) {
        CertificateConfig re = applicationService.getOptionByPolicyNo(policyNo);
        return Result.ok(re);
    }

    /**
     * 保存指定保单下的单证配置
     */
    @PostMapping("/save")
    public Result save(@Validated @RequestBody PolicyCertificateConfig param) {
        boolean re = applicationService.save(param);
        if (re) {
            return Result.ok("保存完成");
        } else {
            return Result.error("保存失败");
        }
    }

    @Autowired
    private CreateCertificateApplicationService createCertificateApplicationService;

    /**
     * 获取指定保单下的单证配置
     */
    @GetMapping("/test1")
    public Result getOptionByPolicyNo1(@RequestParam("claimNo") Long claimNo) {
        //审核通过后读取单证配置并且生成证书
        createCertificateApplicationService.loadConfigAndCreateCertificate(claimNo);
        return Result.ok("123");
    }
}
