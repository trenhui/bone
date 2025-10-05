package com.bone.core.aspect;

import com.bone.core.exception.DistributedLockException;
import com.bone.core.exception.IdempotentException;
import com.bone.core.exception.InvalidRequestException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class EnhancedIdempotentAspect {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedIdempotentAspect.class);
    private static final String LUA_SCRIPT =
            "if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then\n" +
                    "   redis.call('pexpire', KEYS[1], ARGV[2])\n" +
                    "   return 1\n" +
                    "else\n" +
                    "   return 0\n" +
                    "end";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Value("${idempotent.key-prefix:REQ_ID:}")
    private String keyPrefix;

    @Value("${idempotent.ttl-minutes:10}")
    private int ttlMinutes;

    @Value("${idempotent.retry-count:3}")
    private int retryCount;

    @Value("${idempotent.retry-interval:100}")
    private long retryInterval;

    @Pointcut("@annotation(com.bone.core.annotation.Idempotent)")
    public void idempotentPointcut() {}

    @Around("idempotentPointcut()")
    public Object checkIdempotent(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();

        // 1. 获取并校验请求ID
        String requestId = resolveRequestId(request);
        validateRequestId(requestId);

        // 2. 生成Redis键
        String redisKey = buildRedisKey(requestId);

        // 3. 原子性设置键值
        boolean acquired = acquireIdempotentLock(redisKey);
        if (!acquired) {
            handleDuplicateRequest(redisKey, requestId);
        }

        try {
            // 4. 执行目标方法
            return joinPoint.proceed();
        } catch (Exception ex) {
            // 5. 异常处理
            handleIdempotentException(redisKey, ex);
            throw ex;
        } finally {
            // 6. 清理逻辑（根据配置决定是否立即删除）
            cleanupIdempotentKey(redisKey);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        // 支持多来源获取请求ID
        String requestId = request.getHeader("X-Request-ID");
        if (!StringUtils.hasText(requestId)) {
            requestId = request.getParameter("requestId");
        }
        return requestId;
    }

    private void validateRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            logger.error("缺少幂等标识符");
            throw new InvalidRequestException("请求必须包含唯一标识");
        }
        if (requestId.length() > 64) {
            logger.warn("过长的请求ID: {}", requestId);
            throw new InvalidRequestException("请求标识符格式无效");
        }
    }

    private String buildRedisKey(String requestId) {
        return keyPrefix + requestId;
    }

    private boolean acquireIdempotentLock(String key) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(LUA_SCRIPT);
        script.setResultType(Long.class);

        for (int i = 0; i < retryCount; i++) {
            try {
                Long result = redisTemplate.execute(
                        script,
                        Collections.singletonList(key),
                        "PROCESSING",
                        String.valueOf(TimeUnit.MINUTES.toMillis(ttlMinutes))
                );

                if (result != null && result == 1) {
                    return true;
                }

                if (i < retryCount - 1) {
                    Thread.sleep(retryInterval);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DistributedLockException("获取幂等锁中断", e);
            } catch (Exception e) {
                logger.error("Redis操作异常，重试次数: {}/{}", i+1, retryCount, e);
                if (i == retryCount - 1) {
                    throw new IdempotentException("无法获取幂等锁", e);
                }
            }
        }
        return false;
    }

    private void handleDuplicateRequest(String key, String requestId) {
        String currentStatus = redisTemplate.opsForValue().get(key);
        logger.warn("重复请求检测 - Key: {}, 状态: {}", key, currentStatus);

        if ("PROCESSED".equals(currentStatus)) {
            throw new IdempotentException("请求已处理完成");
        }
        throw new IdempotentException("请求正在处理中");
    }

    private void handleIdempotentException(String key, Exception ex) {
        try {
            if (needMarkFailure(ex)) {
                redisTemplate.opsForValue().set(key, "FAILED", ttlMinutes, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            logger.error("更新幂等状态失败", e);
        }
    }

    private boolean needMarkFailure(Exception ex) {
        // 根据异常类型决定是否标记失败
        return !(ex instanceof IdempotentException);
    }

    private void cleanupIdempotentKey(String key) {
        try {
            if (shouldDeleteImmediately()) {
                redisTemplate.delete(key);
            } else {
                redisTemplate.opsForValue().set(key, "PROCESSED", 1, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            logger.error("清理幂等键失败", e);
        }
    }

    private boolean shouldDeleteImmediately() {
        // 根据业务需求配置是否立即删除
        return true;
    }

    // 配置类
    @Configuration
    @ConfigurationProperties(prefix = "idempotent")
    public static class IdempotentProperties {
        private String keyPrefix = "REQ_ID:";
        private int ttlMinutes = 10;
        private int retryCount = 3;
        private long retryInterval = 100;
        private boolean asyncCleanup = false;

        // Getter/Setter...
    }
}