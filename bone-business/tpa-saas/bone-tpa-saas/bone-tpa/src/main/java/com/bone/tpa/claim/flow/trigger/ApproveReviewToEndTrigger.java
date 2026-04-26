package com.bone.tpa.claim.flow.trigger;

import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.sdk.claim.model.SyncTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class ApproveReviewToEndTrigger extends SyncTaskTemplate {
    @Scheduled(cron = "0 0/15 * * * *")
    public void doRetry(){
        log.info("doRetry start,{}",this.getClass().getName());
        this.execute();
    }
    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "ApproveReviewToEndTrigger";
    }

    /**
     * 执行一个任务
     * 在这里加Transaction标记没用
     *
     * @param task
     */
    @Override
    public void syncOneData(SyncTask task) {
        //todo

    }
}
