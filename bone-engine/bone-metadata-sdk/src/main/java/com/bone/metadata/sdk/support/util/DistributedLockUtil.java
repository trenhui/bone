package com.bone.metadata.sdk.support.util;

import com.bone.core.exception.LockAcquireFailedException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLockUtil {

  private static final Logger log = LoggerFactory.getLogger(DistributedLockUtil.class);

  private final RedissonClient redissonClient;
  private final Map<String, ReentrantLock> localLockMap = new ConcurrentHashMap<>();
  private final boolean redisAvailable;

  public DistributedLockUtil() {
    this.redissonClient = null;
    this.redisAvailable = false;

    log.warn("RedissonClient is not available, will use local locking mechanism");
  }

  //    @Autowired(required = false)
  //    public DistributedLockUtil(RedissonClient redissonClient) {
  //        this.redissonClient = redissonClient;
  //        this.redisAvailable = (redissonClient != null);
  //
  //        if (!redisAvailable) {
  //            log.warn("RedissonClient is not available, will use local locking mechanism");
  //        }
  //    }

  /** 获取锁并执行业务逻辑 */
  public <T> T executeWithLock(
      String lockKey, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> supplier) {
    boolean locked = false;
    boolean isDistributedLock = false;
    RLock redisLock = null;
    ReentrantLock localLock = null;

    try {
      // 首先尝试获取 Redis 锁
      if (redisAvailable) {
        redisLock = redissonClient.getLock(lockKey);
        locked = redisLock.tryLock(waitTime, leaseTime, unit);
        if (locked) {
          isDistributedLock = true;
          log.debug("Successfully acquired Redis lock: {}", lockKey);
          return supplier.get();
        }
        log.debug("Failed to acquire Redis lock, attempting local lock: {}", lockKey);
      }

      // 如果 Redis 锁不可用或获取失败，则使用本地锁
      if (!locked) {
        localLock = getLocalLock(lockKey);
        locked = localLock.tryLock(waitTime, unit);
        if (locked) {
          log.debug("Successfully acquired local lock: {}", lockKey);
          return supplier.get();
        }
      }

      throw new LockAcquireFailedException("Failed to acquire lock: " + lockKey);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Lock acquisition interrupted: {}", lockKey, e);
      throw new LockAcquireFailedException("Lock acquisition interrupted: " + lockKey, e);
    } finally {
      // 释放锁
      if (locked) {
        if (isDistributedLock && redisLock != null) {
          releaseRedisLock(redisLock, lockKey);
        } else if (localLock != null) {
          releaseLocalLock(localLock, lockKey);
        }
      }
    }
  }

  /** 获取本地锁对象 */
  private ReentrantLock getLocalLock(String lockKey) {
    return localLockMap.computeIfAbsent(lockKey, k -> new ReentrantLock());
  }

  /** 释放本地锁 */
  private void releaseLocalLock(ReentrantLock localLock, String lockKey) {
    if (localLock != null && localLock.isHeldByCurrentThread()) {
      localLock.unlock();
      log.debug("Released local lock: {}", lockKey);

      // 清理无用的本地锁对象
      if (!localLock.hasQueuedThreads()) {
        localLockMap.remove(lockKey, localLock);
      }
    }
  }

  /** 释放 Redis 锁 */
  private void releaseRedisLock(RLock redisLock, String lockKey) {
    if (redisLock != null && redisLock.isHeldByCurrentThread()) {
      try {
        redisLock.unlock();
        log.debug("Released Redis lock: {}", lockKey);
      } catch (Exception e) {
        log.warn("Failed to release Redis lock: {}", lockKey, e);
      }
    }
  }
}
