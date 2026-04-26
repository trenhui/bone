package com.bone.lowcode.infra.infrastructure.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {

    //通用线程池
    @Bean
    public ThreadPoolExecutor commonPool() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                8,
                8,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                rejectionHandler
        );
        return pool;
    }

    // 自定义拒绝策略（阻塞式等待）
    private static final RejectedExecutionHandler rejectionHandler = (Runnable r, ThreadPoolExecutor executor) -> {
        try {
            boolean offered = executor.getQueue().offer(r, 30, TimeUnit.SECONDS);
            if (!offered) {
                throw new RejectedExecutionException("等待超时,任务被拒绝");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("等待被中断", e);
        }
    };
}
