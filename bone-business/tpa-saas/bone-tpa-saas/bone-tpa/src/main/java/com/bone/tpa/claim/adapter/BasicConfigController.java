package com.bone.tpa.claim.adapter;

import com.bone.core.result.Result;
import com.bone.tpa.claim.application.BasicConfigApplicationService;
import com.bone.tpa.claim.application.dto.EnumOptionDTO;
import com.bone.tpa.claim.application.dto.EnumSelectDTO;
import com.bone.tpa.config.NoLoginUri;
import com.bone.tpa.facade.feign.PkCoreFeign;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.biz.CommonSyncTaskBiz;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 枚举相关控制器
 */
@RestController
@RequestMapping("/tpa")
public class BasicConfigController {
    @Autowired
    private BasicConfigApplicationService basicConfigApplicationService;

    @Autowired
    private CommonSyncTaskBiz commonSyncTaskBiz;

    @Autowired
    private PkCoreFeign pkCoreFeign;

    @NoLoginUri
    @GetMapping("/oss/config")
    public Result<Map<String, Object>> getOssConfig() {
        Map<String, Object> mp =  pkCoreFeign.getToken(new HashMap());
        return Result.ok(mp);
    }

    /**
     * 该接口用于获取特定枚举的所有内容
     */
    @NoLoginUri
    @GetMapping("/enums/optionSet")
    public Result<List<EnumOptionDTO>> queryEnumValue(@RequestParam("enumCode") String enumCode) {
        return Result.ok(basicConfigApplicationService.queryEnumValue(enumCode));
    }


    /**
     * 该接口用于获取现存所有的枚举名称列表
     */
    @NoLoginUri
    @GetMapping("/enums/list")
    public Result<List<EnumSelectDTO>> queryEnumList() {
        return Result.ok(basicConfigApplicationService.queryEnumList());
    }

    /**
     * 该接口用于获取当前登录的人员
     */
    @GetMapping("/user")
    public Result<String> getCurrentLoginUser() {
        return Result.ok(basicConfigApplicationService.getCurrentLoginUser());
    }

    /**
     * 该接口用于检查特定的调度任务的状态
     */
    @GetMapping("/task")
    public Result<Integer> checkTaskStatus(@RequestParam("taskId") Long id) {
        SyncTask syncTask = commonSyncTaskBiz.getById(id);
        if (syncTask == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "异步任务不存在: " + id);
        }

        return Result.ok(syncTask.getStatus());
    }
}
