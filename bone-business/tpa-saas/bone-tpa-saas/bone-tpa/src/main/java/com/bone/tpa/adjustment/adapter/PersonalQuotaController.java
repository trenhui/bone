package com.bone.tpa.adjustment.adapter;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.tpa.adjustment.application.PersonalQuotaApplicationService;
import com.bone.tpa.claim.application.request.FileUploadRequestExtend;
import com.bone.tpa.intelligent.adjustment.model.GetPersonalQuotaListReq;
import com.bone.tpa.intelligent.adjustment.model.PersonInfo;
import com.bone.tpa.intelligent.adjustment.model.PersonalQuotaChangeResponse;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.sdk.adjustment.model.PersonalQuota;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/tpa/personalQuota")
@SimpleLog
public class PersonalQuotaController {

    @Autowired
    private PersonalQuotaApplicationService personalQuotaApplicationService;

    /**
     * 个人额度:初始化、加减、减人
     */
    @PostMapping("/upload")
    public Result<String> uploadFile(@RequestParam("file") MultipartFile file, FileUploadRequestExtend request) throws IOException {
        boolean flag = personalQuotaApplicationService.uploadPersonQuota(file, request);
        if (flag) return Result.ok("处理成功");
        else return Result.error("处理失败");
    }

    /**
     * 获取保单下的被保险人姓名列表
     */
    @GetMapping("/getInsuredNameList")
    public Result getInsuredNameListByPolicyNo(@RequestParam("policyNo") String policyNo,
                                               @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                               @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        PageResult<String> result = personalQuotaApplicationService.getInsuredNameListByPolicyNo(policyNo, pageNo, pageSize);
        return Result.ok(result);
    }

    /**
     * 获取保单下的个人额度操作批次
     */
    @GetMapping("/getOperateBatchList")
    public Result getOperateBatchList(@RequestParam("policyNo") String policyNo,
                                      @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                      @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        PageResult<String> result = personalQuotaApplicationService.getOperateBatchList(policyNo, pageNo, pageSize);
        return Result.ok(result);
    }

    /**
     * 获取保单下的个人额度列表
     */
    @PostMapping("/list")
    public Result getPersonalQuotaList(@RequestBody GetPersonalQuotaListReq request) {
        PageResult<PersonalQuota> result = personalQuotaApplicationService.getPersonalQuotaList(request);
        return Result.ok(result);
    }

    /**
     * 获取某条个人额度数据的保全记录
     */
    @PostMapping("/changeList")
    public Result getPersonalQuotaChangeList(@RequestBody PersonInfo personInfo) {
        PageResult<PersonalQuotaChangeResponse> result = personalQuotaApplicationService.getPersonalQuotaChangeList(personInfo);
        return Result.ok(result);
    }
}
