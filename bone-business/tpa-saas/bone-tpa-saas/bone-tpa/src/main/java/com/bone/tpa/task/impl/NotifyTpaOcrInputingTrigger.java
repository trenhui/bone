package com.bone.tpa.task.impl;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.sync.ClaimToTpaChangeService;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.TpaSubmitClaimResult;
import com.bone.tpa.sdk.claim.model.SyncTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotifyTpaOcrInputingTrigger extends SyncTaskTemplate {

    @Autowired
    ClaimToTpaChangeService toTpaChangeService;
    @Autowired
    CommonLogService commonLogService;


    @Autowired
    TpaDataSyncFeign tpaDataSyncFeign;

    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "notify_tpa_ocr_inputing";
    }

    /**
     * 获取告警url
     *
     * @return
     */
    @Override
    public String getAlertUrl() {
        return super.dingAlertUrl;
    }

    @Override
    public String getAlertMode() {
        return "dingding";
    }

    /**
     * 执行一个任务
     * 在这里加Transaction标记没用
     *
     * @param task
     */
    @Override
    public void syncOneData(SyncTask task) {
        notifyToTpa(Long.valueOf(task.getData()));
    }

    public void notifyToTpa(Long claimId){
        TpaSubmitClaimResult request = new TpaSubmitClaimResult();
        request.setClaimNumber(claimId);
        request.setClaimInfo(toTpaChangeService.toSyncVo(claimId));
        request.setEventType(Integer.valueOf(EventType.saas通知tpa案件自动化录入中.getCode()));
        ApiResult<Map<String, Object>> remoteRs = tpaDataSyncFeign.submitClaimResult(request);
        commonLogService.addClaimLogAsync(claimId, CommonLogType.TO_TPA_LOG, "saas通知tpa案件自动化录入中,claimId:{},remoteRs:{}", claimId, JSONObject.toJSONString(remoteRs));
        if (!remoteRs.isSuccess()) {
            throw new RuntimeException("saas通知tpa案件自动化录入中失败:" + remoteRs.getMessage());
        }
    }
}
