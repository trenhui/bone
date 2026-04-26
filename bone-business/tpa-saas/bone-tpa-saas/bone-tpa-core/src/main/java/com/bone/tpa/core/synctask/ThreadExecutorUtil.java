package com.bone.tpa.core.synctask;

import java.util.concurrent.*;

/**
 * ThreadExecutorUtil
 *
 * @author helei
 * @date 2021-12-27
 * @time 17:21
 */
public class ThreadExecutorUtil {

    /**
     * 系统核心数
     */
    public static int PROCEEE_NO = Runtime.getRuntime().availableProcessors()*2;
    /**
     * 延迟线程池
     */
    public static final ScheduledExecutorService SCHDEULED_THREAD_POOL = new ScheduledThreadPoolExecutor(PROCEEE_NO);
    /**
     * 同步线程池
     */
    public static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(PROCEEE_NO,
            PROCEEE_NO, 1000L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
}
