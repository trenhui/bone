package com.bone.tpa.claim.flow.trigger;

import com.bone.tpa.core.synctask.SyncTaskTemplate;
import com.bone.tpa.sdk.claim.model.SyncTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 直接审核通过到终态的后续任务
 */
@Slf4j
@Service
public class ApproveToEndTrigger extends SyncTaskTemplate {
    /**
     * 所属业务
     *
     * @return
     */
    @Override
    public String getBizType() {
        return "ApproveToEndTrigger";
    }
    @Scheduled(cron = "0 0/15 * * * *")
    public void doRetry(){
        log.info("doRetry start,{}",this.getClass().getName());
        this.execute();
    }
    /**
     * 执行一个任务
     * 在这里加Transaction标记没用
     *
     * @param task
     */
    @Override
    public void syncOneData(SyncTask task) {
        //todo 做一些推送方面的活
    }
}
