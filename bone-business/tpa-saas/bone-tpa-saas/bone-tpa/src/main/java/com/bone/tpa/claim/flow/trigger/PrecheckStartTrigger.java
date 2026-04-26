package com.bone.tpa.claim.flow.trigger;

import com.bone.tpa.claim.flow.jump.PrecheckFlowFireService;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.ClaimRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 签收进来，fier 需要走异步
 */
@Service
@Slf4j
public class PrecheckStartTrigger  extends SyncTaskTemplate {
    @Autowired
    private PrecheckFlowFireService precheckFlowFireService;
    @Autowired
    private ClaimRepository claimRepository;
    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "PrecheckStartTrigger";
    }

    @Scheduled(cron = "0 0/15 * * * *")
    public void doRetry(){
        log.info("doRetry start,{}",this.getClass().getName());
        this.execute();
    }

    /**
     * 获取告警url
     *
     * @return
     */
    @Override
    public String getAlertUrl() {
        return dingAlertUrl;
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
        Long claimNumber = Long.valueOf(task.getData());
        precheckFlowFireService.fire(claimRepository.findById(claimNumber));
    }
}
