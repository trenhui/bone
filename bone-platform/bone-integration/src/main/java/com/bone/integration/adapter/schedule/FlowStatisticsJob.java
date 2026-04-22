package com.bone.integration.adapter.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FlowStatisticsJob {
    @Scheduled(cron = "0 0 0 * * ?") // 每天凌晨执行
    public void execute() {
        log.info("开始执行流程统计任务");
        // TODO: 实现流程执行统计逻辑
        log.info("流程统计任务执行完成");
    }
}