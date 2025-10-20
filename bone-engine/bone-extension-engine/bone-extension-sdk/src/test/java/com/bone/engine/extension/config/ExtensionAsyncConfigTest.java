package com.bone.engine.extension.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtensionAsyncConfig的单元测试类，验证异步事件处理线程池配置
 */
@SpringBootTest(classes = {ExtensionAsyncConfig.class})
public class ExtensionAsyncConfigTest {

    @Autowired
    private AsyncTaskExecutor extensionEventTaskExecutor;

    @Test
    public void testAsyncTaskExecutorConfiguration() {
        // 验证任务执行器是否正确配置
        assertNotNull(extensionEventTaskExecutor, "AsyncTaskExecutor should be configured");
        
        // 验证执行器的具体实现类型
        assertTrue(extensionEventTaskExecutor instanceof ThreadPoolTaskExecutor, 
                "Task executor should be an instance of ThreadPoolTaskExecutor");
        
        ThreadPoolTaskExecutor threadPoolTaskExecutor = (ThreadPoolTaskExecutor) extensionEventTaskExecutor;
        ThreadPoolExecutor executor = threadPoolTaskExecutor.getThreadPoolExecutor();
        
        // 验证线程池配置
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        assertEquals(availableProcessors / 2, executor.getCorePoolSize(), 
                "Core pool size should be half of available processors");
        
        assertEquals(availableProcessors, executor.getMaximumPoolSize(), 
                "Maximum pool size should match available processors");
        
        // 验证队列配置
        BlockingQueue<?> queue = executor.getQueue();
        assertNotNull(queue, "Queue should not be null");
        
        // 验证线程名称前缀
        String threadNamePrefix = threadPoolTaskExecutor.getThreadNamePrefix();
        assertTrue(threadNamePrefix.contains("extension-event-"), 
                "Thread name prefix should contain 'extension-event-'");
        
        // 验证拒绝策略
        assertTrue(executor.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy, 
                "Rejected execution handler should be CallerRunsPolicy");
    }
    
    @Test
    public void testAsyncExecutorFunctionality() throws InterruptedException {
        // 测试异步执行功能
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);
        
        extensionEventTaskExecutor.execute(() -> {
            try {
                Thread.sleep(100); // 模拟异步操作
                executed.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        
        // 等待异步操作完成
        latch.await(1, TimeUnit.SECONDS);
        
        // 验证异步操作已执行
        assertTrue(executed.get(), "Async task should have been executed");
    }
}