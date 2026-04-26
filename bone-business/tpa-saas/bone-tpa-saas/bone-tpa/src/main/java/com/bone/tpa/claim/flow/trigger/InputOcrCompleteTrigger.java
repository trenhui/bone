package com.bone.tpa.claim.flow.trigger;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.claim.flow.stage.InputStageService;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.sdk.claim.model.SyncTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InputOcrCompleteTrigger extends SyncTaskTemplate {
    @Autowired
    private InputStageService inputStageService;
    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "InputOcrCompleteTrigger";
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
        String data = task.getData();
        JSONObject jsonObject =   JSONObject.parseObject(data);

        inputStageService.inputOcrCompleteAction(jsonObject.getLong("claimNumber"));
    }
}
