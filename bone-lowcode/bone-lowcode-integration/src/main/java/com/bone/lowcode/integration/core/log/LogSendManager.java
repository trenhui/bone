package com.bone.lowcode.integration.core.log;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bone.lowcode.integration.common.Constants.CAMEL_REDIS_CHANNEL;

@Component
@Slf4j
public class LogSendManager {
    private final BlockingQueue<LogEntity> queue = new LinkedBlockingQueue<>(1000);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    private AtomicBoolean running = new AtomicBoolean(true);

    @PostConstruct
    public void init() {
        run();
    }

    public void add(LogEntity logEntity) {
        boolean success = queue.offer(logEntity);
        if (!success) {
            log.warn("LogSendManager queue is full");
        }
    }

    public void run() {
        executor.submit(() -> {
            while (running.get()) {
                try {
                    LogEntity logEntity = queue.poll(1, TimeUnit.SECONDS);
                    if (logEntity != null) {
                        redisTemplate.convertAndSend(CAMEL_REDIS_CHANNEL, JSON.toJSONString(logEntity));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("LogSendManager interrupted.");
                } catch (Throwable e) {
                    log.error("redisTemplate广播异常", e);
                }
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        log.info("LogSendManager shutting down...");
        running.set(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        log.info("LogSendManager shutdown complete.");
    }
}
