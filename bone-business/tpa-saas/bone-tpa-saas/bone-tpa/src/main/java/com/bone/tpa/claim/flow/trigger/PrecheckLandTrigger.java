package com.bone.tpa.claim.flow.trigger;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.claim.flow.jump.PrecheckFlowFireService;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.ClaimRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PrecheckLandTrigger extends SyncTaskTemplate {
    @Autowired
    PrecheckFlowFireService precheckFlowFireService;

    @Autowired
    ClaimRepository claimRepository;
    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "PrecheckLandTrigger";
    }
    @Override
    public String getAlertUrl() {
        return dingAlertUrl;
    }
    @Scheduled(cron = "0 0/15 * * * *")
    public void doRetry(){
        log.info("doRetry start,{}",this.getClass().getName());
        this.execute();
    }
    @Override
    public String getAlertMode() {
        return AlertRobotManager.QIWEI_MODE;
    }


    /**
     * 执行一个任务
     * 在这里加Transaction标记没用
     *
     * @param task
     */
    @Override
    public void syncOneData(SyncTask task) {

        String data = task.getData();
        JSONObject jsonObject =   JSONObject.parseObject(data);

        Claim claim =  claimRepository.findById(jsonObject.getLong("claimNumber"));
        precheckFlowFireService.doLandEvent(claim);
    }
}
