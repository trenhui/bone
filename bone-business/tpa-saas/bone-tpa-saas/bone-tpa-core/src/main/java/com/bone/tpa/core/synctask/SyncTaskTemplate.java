package com.bone.tpa.core.synctask;

import com.bone.core.util.PkListUtil;
import com.bone.tpa.core.redis.RedisLockManage;
import com.bone.tpa.sdk.dao.biz.CommonSyncTaskBiz;
import com.bone.tpa.sdk.claim.model.SyncTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * SyncTaskTemplate
 *
 * @author helei
 * @date 2021-12-31
 * @time 12:40
 */
@Slf4j
public abstract class SyncTaskTemplate {


    @Value("${ding.alert.url:}")
    protected String dingAlertUrl;
    @Resource
    protected CommonSyncTaskBiz sycJobManager;

    @Resource
    protected  AlertRobotManager alertRobotManager;

    @Resource
    protected RedisLockManage redisLockManage;

    @Value("${ding.alert.url:}")
    protected String defaultRobotUrl;


    /**
     * default  delayMinute 3
     * default tryFireSecond 1
     * default subType null
     *
     * @param data
     *
     * @return
     */
    public SyncTask addJobAndTryFire(String data) {
        return addJobAndTryFire(data, 3, 1);
    }

    public SyncTask addJobAndTryFire(String data, String subType) {
        return addJobAndTryFire(data, 3, 1, subType);
    }

    /**
     * @param data
     * @param delayMinute 补偿任务的执行y延迟时间（单位，分钟）
     * @param tryFireSecondDelay 第一次快速执行任务的延迟时间（单位秒）,为了防止事务提交的时间差
     *
     * @return
     */

    public SyncTask addJobAndTryFire(String data, Integer delayMinute, Integer tryFireSecondDelay) {
        return addJobAndTryFire(data, delayMinute, tryFireSecondDelay, null);
    }

    /**
     * @param data
     * @param delayMinute 补偿任务的执行y延迟时间（单位，分钟）
     * @param tryFireSecondDelay 第一次快速执行任务的延迟时间（单位秒）,为了防止事务提交的时间差
     * @param subType subType
     *
     * @return
     */
    public SyncTask addJobAndTryFire(String data, Integer delayMinute,
                                           Integer tryFireSecondDelay, String subType) {

        if (tryFireSecondDelay == null) {
            tryFireSecondDelay = 5;
        }
        SyncTask rs = addJobWithSubtype(subType, data, delayMinute);
        final String traceId = MDC.get("traceId");

        //用延迟线程池，是为了防止事务没提交
        ThreadExecutorUtil.SCHDEULED_THREAD_POOL.schedule(new Runnable() {

            @Override
            public void run() {
                try {
                    String localTraceId = traceId;
                    if(StringUtils.isBlank(localTraceId)){
                        localTraceId = UUID.randomUUID().toString();
                    }
                    MDC.put("traceId", localTraceId);
                    executeOneTask(rs);
                } catch (Throwable e) {
                    log.error("firsTry,error:{}", rs.getId(), e);
                } finally {
                    MDC.remove("traceId");

                }
            }
        }, tryFireSecondDelay, TimeUnit.SECONDS);
        return rs;
    }

    public SyncTask addJobWithSubtype(String subType, String data, Integer delayMinute) {

        Calendar calendar = Calendar.getInstance();
        if (delayMinute != null) {
            calendar.add(Calendar.MINUTE, delayMinute);

        }
        Long id = sycJobManager.addTask(getAppName(), getBizType(),
                subType, data, calendar.getTime());
        return sycJobManager.getById(id);
    }

    public String getAppName() {
        return "saas";
    }

    /**
     * 所属业务
     *
     * @return
     */
    public abstract String getBizType();

    /**
     * 获取告警url
     * @return
     */
    public String getAlertUrl() {
        return dingAlertUrl;
    }

    public String getAlertMode() {
        return AlertRobotManager.QIWEI_MODE;
    }




    /**
     * 触发任务扫描
     *
     * @throws Exception
     */
    public void execute() {

        Date now = new Date();
        List<SyncTask> executeList = sycJobManager.waitingExecuteList(getAppName(),
                getBizType(), null, 0L, now);
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.MINUTE, 5);
        List<SyncTask> failList = new ArrayList<>();

        Integer retryTimeFail = getRetryTimeToFail();

        Integer retryTimeAlert = getRetryTimeToAlert();


        log.info("biztype:{},retryFailTime :{},retryAlertTime:{}", getBizType(), retryTimeFail, retryTimeAlert);
        while (!executeList.isEmpty()) {
            for (SyncTask task : executeList) {
                Integer retryTime = task.getRetryTimes() + 1;
                try {
                    MDC.put("traceId",UUID.randomUUID().toString());
                    executeOneTask(task);
                } catch (Throwable e) {
                    log.error("task fail data:{}", task);
                    log.error("SyncTaskTemplate fail", e);
                    if (task.getRetryTimes() >= retryTimeAlert) {
                        failList.add(task);
                    }
                    Calendar deloayCal = Calendar.getInstance();
                    deloayCal.add(Calendar.MINUTE, 5);
                    log.error("tryWaitingRecord  fail : " + task.getId(), e);
                    if (task.getRetryTimes() >= retryTimeFail) {
                        //做错误标记
                        task.setRetryTimes(retryTime);
                        task.setStatus(2);

                        sycJobManager.updateTaskStatus(task);
                    } else {
                        //5分钟后定时任务唤起重试
                        task.setRetryTimes(retryTime);
                        task.setFireTime(deloayCal.getTime());
                        sycJobManager.updateTaskStatus(task);
                    }
                }finally {
                    MDC.remove("traceId");
                }
            }

            executeList = sycJobManager.waitingExecuteList(getAppName(),
                    getBizType(), null, PkListUtil.last(executeList).getId(), now);

        }

        if (!PkListUtil.isEmpty(failList)) {
            try {
                doFailAlert(failList);
            } catch (Exception e) {
                log.error("doFailAlert error ", e);
            }
        }

        return;
    }

    public int getOneLockTime() {
        return 60;
    }



    private String getExcutingLockKey(SyncTask task) {
        String lockKey = String.format("usercenter_sync_singlielock_%s", task.getId());

        return lockKey;
    }

    public void executeOneTask(SyncTask syncTask) {

        SyncTask checkExist = sycJobManager.getById(syncTask.getId());
        if (checkExist == null) {
            return;
        }
        if (checkExist.getStatus() != 0) {
            return;
        }
        String lockKey = getExcutingLockKey(syncTask);
        boolean ret = redisLockManage.tryLock(lockKey,getOneLockTime());
        if (!ret) {
            return;
        }

        try {
            syncOneData(syncTask);
            syncTask.setStatus(1);
            sycJobManager.updateTaskStatus(syncTask);
        } finally {
            redisLockManage.unlock(lockKey);
        }

    }

    /**
     * 获取执行次数告警的值
     *
     * @return
     */
    public int getRetryTimeToAlert() {
        return 3;
    }

    /**
     * 获取重试次数-》失败的阀值
     *
     * @return
     */
    public int getRetryTimeToFail() {
        return 10;
    }

    /**
     * 预警事件
     *
     * @param failList
     * @param failList
     */
    public void doFailAlert(List<SyncTask> failList) {
        StringBuilder sb = new StringBuilder();
        sb.append(getBizType() + "失败,数量:");
        sb.append(failList.size());
        if(failList.size()<5){
            sb.append(",taskId:");
            failList.stream().forEach(t->{
                sb.append(t.getId()+",");
            });
        }
        alertRobotManager.doAlertAsyncDefault( getBizType()+" 业务监控:"+sb.toString());
    }

    public String lockKey() {
        return getAppName() + getBizType() + "-lock";
    }

    /**
     * 执行一个任务
     * 在这里加Transaction标记没用
     *
     * @param task
     */
    public abstract void syncOneData(SyncTask task);

}
