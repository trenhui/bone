package com.bone.procurement.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁管理器
 * 基于Redis实现的分布式锁，支持自动续期和可重入
 */
@Component
public class DistributedLockManager {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockManager.class);
    private static final String LOCK_PREFIX = "bone:procurement:lock:";
    private static final long DEFAULT_EXPIRE_TIME = 30; // 默认锁过期时间（秒）
    private static final long DEFAULT_WAIT_TIME = 5;  // 默认等待时间（秒）
    private static final long DEFAULT_SLEEP_TIME = 100; // 默认休眠时间（毫秒）
    
    // Redis自动续期脚本
    private static final String RENEW_SCRIPT = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('pexpire', KEYS[1], ARGV[2])
        else
            return 0
        end
    """;
    
    // Redis释放锁脚本
    private static final String RELEASE_SCRIPT = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
        else
            return 0
        end
    """;
    
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> renewScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final ThreadLocal<String> lockValues = new ThreadLocal<>(); // 用于存储当前线程的锁值
    
    @Autowired
    public DistributedLockManager(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.renewScript = new DefaultRedisScript<>(RENEW_SCRIPT, Long.class);
        this.releaseScript = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
    }
    
    /**
     * 获取分布式锁
     * @param lockName 锁名称
     * @return 是否获取成功
     */
    public boolean lock(String lockName) {
        return lock(lockName, DEFAULT_EXPIRE_TIME, DEFAULT_WAIT_TIME);
    }
    
    /**
     * 获取分布式锁
     * @param lockName 锁名称
     * @param expireTime 过期时间（秒）
     * @param waitTime 等待时间（秒）
     * @return 是否获取成功
     */
    public boolean lock(String lockName, long expireTime, long waitTime) {
        String lockKey = LOCK_PREFIX + lockName;
        String lockValue = generateLockValue();
        long startTime = System.currentTimeMillis();
        long waitTimeMs = waitTime * 1000;
        
        while (true) {
            // 尝试获取锁
            Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, expireTime, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(success)) {
                // 启动自动续期
                startAutoRenewal(lockKey, lockValue, expireTime);
                lockValues.set(lockValue);
                logger.debug("Lock acquired: {}", lockName);
                return true;
            }
            
            // 检查是否超时
            if (System.currentTimeMillis() - startTime > waitTimeMs) {
                logger.warn("Failed to acquire lock {} within {} seconds", lockName, waitTime);
                return false;
            }
            
            // 休眠一段时间后重试
            try {
                Thread.sleep(DEFAULT_SLEEP_TIME);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Lock acquisition interrupted for: {}", lockName, e);
                return false;
            }
        }
    }
    
    /**
     * 释放分布式锁
     * @param lockName 锁名称
     * @return 是否释放成功
     */
    public boolean unlock(String lockName) {
        String lockKey = LOCK_PREFIX + lockName;
        String lockValue = lockValues.get();
        
        if (lockValue == null) {
            logger.warn("No lock value found for: {}", lockName);
            return false;
        }
        
        try {
            Long result = redisTemplate.execute(
                    releaseScript,
                    Collections.singletonList(lockKey),
                    lockValue
            );
            
            lockValues.remove();
            
            if (result != null && result > 0) {
                logger.debug("Lock released: {}", lockName);
                return true;
            } else {
                logger.warn("Failed to release lock: {} (lock may be expired or owned by another thread)", lockName);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error releasing lock: {}", lockName, e);
            return false;
        }
    }
    
    /**
     * 尝试获取锁，如果获取失败则返回false
     * @param lockName 锁名称
     * @return 是否获取成功
     */
    public boolean tryLock(String lockName) {
        String lockKey = LOCK_PREFIX + lockName;
        String lockValue = generateLockValue();
        
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, DEFAULT_EXPIRE_TIME, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(success)) {
            // 启动自动续期
            startAutoRenewal(lockKey, lockValue, DEFAULT_EXPIRE_TIME);
            lockValues.set(lockValue);
            logger.debug("Try lock acquired: {}", lockName);
            return true;
        }
        
        return false;
    }
    
    /**
     * 自动续期
     * @param lockKey 锁键
     * @param lockValue 锁值
     * @param expireTime 过期时间（秒）
     */
    private void startAutoRenewal(String lockKey, String lockValue, long expireTime) {
        Thread renewalThread = new Thread(() -> {
            try {
                while (true) {
                    // 休眠锁过期时间的三分之一
                    Thread.sleep(expireTime * 1000 / 3);
                    
                    // 检查线程是否被中断
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    
                    // 续期
                    Long result = redisTemplate.execute(
                            renewScript,
                            Collections.singletonList(lockKey),
                            lockValue,
                            String.valueOf(expireTime * 1000)
                    );
                    
                    // 如果续期失败，说明锁已失效
                    if (result == null || result == 0) {
                        logger.debug("Lock renewal failed, exiting renewal thread for: {}", lockKey);
                        break;
                    }
                    
                    logger.debug("Lock renewed for: {}", lockKey);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Renewal thread interrupted for: {}", lockKey);
            } catch (Exception e) {
                logger.error("Error in renewal thread for: {}", lockKey, e);
            }
        }, "Lock-Renewal-Thread-" + lockKey);
        
        renewalThread.setDaemon(true);
        renewalThread.start();
    }
    
    /**
     * 执行带有锁保护的操作
     * @param lockName 锁名称
     * @param supplier 要执行的操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T executeWithLock(String lockName, Supplier<T> supplier) {
        return executeWithLock(lockName, DEFAULT_EXPIRE_TIME, DEFAULT_WAIT_TIME, supplier);
    }
    
    /**
     * 执行带有锁保护的操作
     * @param lockName 锁名称
     * @param expireTime 锁过期时间（秒）
     * @param waitTime 等待时间（秒）
     * @param supplier 要执行的操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T executeWithLock(String lockName, long expireTime, long waitTime, Supplier<T> supplier) {
        boolean locked = lock(lockName, expireTime, waitTime);
        if (!locked) {
            throw new RuntimeException("Failed to acquire lock: " + lockName);
        }
        
        try {
            return supplier.get();
        } finally {
            unlock(lockName);
        }
    }
    
    /**
     * 执行带有锁保护的无返回值操作
     * @param lockName 锁名称
     * @param action 要执行的操作
     */
    public void executeWithLock(String lockName, Runnable action) {
        executeWithLock(lockName, () -> {
            action.run();
            return null;
        });
    }
    
    /**
     * 生成唯一的锁值
     * @return 锁值
     */
    private String generateLockValue() {
        return UUID.randomUUID().toString() + ":" + Thread.currentThread().getId();
    }
    
    /**
     * 检查锁是否存在
     * @param lockName 锁名称
     * @return 是否存在
     */
    public boolean isLocked(String lockName) {
        String lockKey = LOCK_PREFIX + lockName;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }
    
    /**
     * 强制释放锁（谨慎使用，可能导致锁竞争问题）
     * @param lockName 锁名称
     */
    public void forceUnlock(String lockName) {
        String lockKey = LOCK_PREFIX + lockName;
        redisTemplate.delete(lockKey);
        logger.warn("Force unlocked: {}", lockName);
    }
}