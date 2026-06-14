package com.bone.core.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/**
 * 分布式ID生成器（基于雪花算法，支持时钟回拨防护）
 *
 * @author 梅山 2023-10-1
 */
@Slf4j
public final class DistributedIdGenerator {

  private static final String DEFAULT_PREFIX = "BONE";
  private static final int DEFAULT_SEQUENCE_LENGTH = 6;
  private static final long DEFAULT_EPOCH = 1609459200000L; // 2021-01-01

  // 雪花算法实例（双重校验锁）
  private static volatile Snowflake snowflake;

  // 序列号生成器（线程安全）
  private static final AtomicLong sequenceGenerator = new AtomicLong(0);
  private static final long maxSequence = 999_999L;

  // 时钟回拨容忍阈值（毫秒）
  private static final long maxClockBackwardTolerance = 1000L;

  private DistributedIdGenerator() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /** 初始化雪花算法配置 */
  public static synchronized void initialize(long datacenterId, long workerId, long epoch) {
    if (snowflake != null) {
      log.warn("Snowflake already initialized, ignoring duplicate initialization");
      return;
    }
    snowflake = new Snowflake(datacenterId, workerId, epoch);
    log.info(
        "Snowflake ID generator initialized: datacenterId={}, workerId={}, epoch={}",
        datacenterId,
        workerId,
        epoch);
  }

  /** 生成UUID（无横线，小写格式） */
  public static String generateUuid() {
    return UUID.randomUUID().toString().replace("-", "").toLowerCase();
  }

  /** 生成雪花ID（字符串格式） */
  public static String generateSnowflakeId() {
    return String.valueOf(generateLongId());
  }

  /** 生成雪花ID（长整型） */
  public static long generateLongId() {
    return getSnowflakeInstance().nextId();
  }

  /** 生成业务流水号（带日期前缀） */
  public static String generateBusinessNumber() {
    return generateBusinessNumber(DEFAULT_PREFIX);
  }

  public static String generateBusinessNumber(String prefix) {
    Assert.notNull(prefix, "Prefix cannot be blank");
    String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    long sequence = getNextSequenceNumber();
    return String.format("%s%s%06d", prefix, date, sequence);
  }

  /** 生成订单编号（带时间戳） */
  public static String generateOrderNumber() {
    return generateOrderNumber(DEFAULT_PREFIX);
  }

  public static String generateOrderNumber(String prefix) {
    Assert.hasText(prefix, "Prefix cannot be blank");
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    long sequence = getNextSequenceNumber();
    return String.format("%s%s%04d", prefix, timestamp, sequence);
  }

  /** 获取雪花算法实例（懒加载） */
  private static Snowflake getSnowflakeInstance() {
    if (snowflake == null) {
      synchronized (DistributedIdGenerator.class) {
        if (snowflake == null) {
          // 默认从环境变量加载，或使用默认值
          long datacenterId = getEnvironmentLong("BONE_DATACENTER_ID", 0L);
          long workerId = getEnvironmentLong("BONE_WORKER_ID", 0L);
          long epoch = getEnvironmentLong("BONE_EPOCH", DEFAULT_EPOCH);
          snowflake = new Snowflake(datacenterId, workerId, epoch);
        }
      }
    }
    return snowflake;
  }

  /** 获取下一个序列号（带溢出保护） */
  private static synchronized long getNextSequenceNumber() {
    long current = sequenceGenerator.incrementAndGet();
    if (current > maxSequence) {
      sequenceGenerator.set(0);
      current = sequenceGenerator.incrementAndGet();
    }
    return current;
  }

  private static long getEnvironmentLong(String key, long defaultValue) {
    try {
      String value = System.getenv(key);
      return value != null ? Long.parseLong(value) : defaultValue;
    } catch (Exception e) {
      log.warn("Failed to parse environment variable {}, using default: {}", key, defaultValue, e);
      return defaultValue;
    }
  }

  /** 雪花算法内部实现 */
  private static class Snowflake {
    private static final long sequenceBits = 12L;
    private static final long workerIdBits = 5L;
    private static final long datacenterIdBits = 5L;

    private static final long maxWorkerId = ~(-1L << workerIdBits);
    private static final long maxDatacenterId = ~(-1L << datacenterIdBits);
    private static final long sequenceMask = ~(-1L << sequenceBits);

    private static final long workerIdShift = sequenceBits;
    private static final long datacenterIdShift = sequenceBits + workerIdBits;
    private static final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;

    private final long datacenterId;
    private final long workerId;
    private final long epoch;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public Snowflake(long datacenterId, long workerId, long epoch) {
      if (workerId > maxWorkerId || workerId < 0) {
        throw new IllegalArgumentException(
            String.format("Worker ID must be between 0 and %d", maxWorkerId));
      }
      if (datacenterId > maxDatacenterId || datacenterId < 0) {
        throw new IllegalArgumentException(
            String.format("Datacenter ID must be between 0 and %d", maxDatacenterId));
      }
      this.datacenterId = datacenterId;
      this.workerId = workerId;
      this.epoch = epoch;
    }

    public synchronized long nextId() {
      long timestamp = System.currentTimeMillis();

      // 时钟回拨处理
      if (timestamp < lastTimestamp) {
        long offset = lastTimestamp - timestamp;
        if (offset <= maxClockBackwardTolerance) {
          // 小范围回拨，等待时钟追上
          waitForClockCatchUp(offset);
          timestamp = System.currentTimeMillis();
          if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock still behind after waiting");
          }
        } else {
          throw new IllegalStateException(
              String.format("Clock moved backwards too much: %d milliseconds", offset));
        }
      }

      if (lastTimestamp == timestamp) {
        sequence = (sequence + 1) & sequenceMask;
        if (sequence == 0) {
          timestamp = waitUntilNextMillis(lastTimestamp);
        }
      } else {
        sequence = 0L;
      }

      lastTimestamp = timestamp;

      return ((timestamp - epoch) << timestampLeftShift)
          | (datacenterId << datacenterIdShift)
          | (workerId << workerIdShift)
          | sequence;
    }

    private void waitForClockCatchUp(long offset) {
      try {
        Thread.sleep(offset);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Clock catch-up wait interrupted", e);
      }
    }

    private long waitUntilNextMillis(long lastTimestamp) {
      long timestamp = System.currentTimeMillis();
      while (timestamp <= lastTimestamp) {
        timestamp = System.currentTimeMillis();
      }
      return timestamp;
    }
  }
}
