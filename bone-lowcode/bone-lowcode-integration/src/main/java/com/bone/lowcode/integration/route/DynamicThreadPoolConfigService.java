//package com.bone.lowcode.integration.route;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import javax.annotation.PostConstruct;
//import javax.annotation.PreDestroy;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//@Configuration
//public class DynamicThreadPoolConfigService {
//
//    private ThreadPoolExecutor executorService;
//
//    private final ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor();
//    private final AtomicBoolean shutdownFlag = new AtomicBoolean(false);
//
//    @Value("${threadpool.corePoolSize:50}")
//    private int corePoolSize;
//
//    @Value("${threadpool.maxPoolSize:200}")
//    private int maxPoolSize;
//
//    @Value("${threadpool.keepAliveTime:60}")
//    private long keepAliveTime;
//
//    @Value("${threadpool.queueCapacity:1000}")
//    private int queueCapacity;
//
//    @PostConstruct
//    public void init() {
//        // 初始化线程池配置
//        this.executorService = new ThreadPoolExecutor(
//                corePoolSize,
//                maxPoolSize,
//                keepAliveTime, TimeUnit.SECONDS,
//                new LinkedBlockingQueue<>(queueCapacity),
//                Executors.defaultThreadFactory(),
//                new ThreadPoolExecutor.AbortPolicy()  // 使用AbortPolicy避免过载时的内存泄露
//        );
//    }
//
//    @Bean
//    public ExecutorService getExecutorService() {
//        return this.executorService;
//    }
//
//
//    /**
//     * 应用关闭时关闭线程池。
//     */
//    @PreDestroy
//    public void shutdown() {
//        shutdownFlag.set(true);
//        executorService.shutdown();
//        monitorExecutor.shutdown();
//        try {
//            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
//                executorService.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            executorService.shutdownNow();
//        }
//    }
//}