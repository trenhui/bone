package com.bone.ai.gateway.infrastructure.quota;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.ai.gateway.config.LlmGatewayProperties;
import com.bone.ai.gateway.domain.gateway.TenantQuotaGuard;
import com.bone.core.exception.BizException;
import java.time.Duration;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 租户配额闸门（多副本实现）：Redis 固定窗口计数，供多副本 / 集群部署替换进程内版。
 *
 * <p><b>为何需要它</b>：默认 {@link InMemoryTenantQuotaGuard} 是进程内计数——多副本部署下全局配额为副本数的 N 倍，
 * 「防失控租户刷爆共享模型通道」的闸门形同虚设（详设 §6 生产就绪清单硬依赖项）。本实现把计数搬到 Redis，
 * 语义与进程内版逐条对齐：同一租户同一窗口内第 {@code limit+1} 次调用抛 {@code 429 AI_GATEWAY_QUOTA_EXCEEDED}。
 *
 * <p><b>键设计</b>：{@code bone:ai:quota:{tenantId}:{windowStart}}——窗口起点进键，窗口切换天然换键，旧键靠 TTL 自动回收，
 * 无需 CAS 换窗（进程内版才需要）。TTL = 2 倍窗口：覆盖「窗口刚结束仍有迟到的 in-flight 调用在旧键上自增」的边界。
 *
 * <p><b>Redis 故障时 fail-open（刻意选择，务必读）</b>：计数不可用时放行并 WARN——配额是<b>成本治理闸门</b>而非安全联锁；
 * 安全联锁（成本红线 / PII 脱敏 / 证据链）都不经过 Redis。若此处 fail-closed，一次 Redis 抖动会把全部租户的 LLM
 * 调用打成 429，违背「AI 故障不阻断业务」的架构红线（§4 #18）。代价是故障窗口内配额短暂虚警为宽，由告警
 * （WARN 日志 / 计数失败率指标）兜底感知。
 *
 * <p>装配：见 {@code RedisQuotaAutoConfiguration}——仅当 classpath 有 Redisson 且存在 {@link RedissonClient} bean 时生效，
 * 否则继续用进程内默认实现；接入方也可以自己的 {@code @Bean TenantQuotaGuard} 覆盖两者。
 */
public class RedisTenantQuotaGuard implements TenantQuotaGuard {

  private static final Logger log = LoggerFactory.getLogger(RedisTenantQuotaGuard.class);

  /** 配额计数键前缀（窗口起点拼在租户后，见类注释）。 */
  static final String KEY_PREFIX = "bone:ai:quota:";

  private final RedissonClient redissonClient;
  private final long maxRequestsPerWindow;
  private final long windowMillis;

  /**
   * @param maxRequestsPerWindow 每窗口请求数上限；{@code <= 0} = 配额关闭（闸门装配但恒放行， 与进程内版「配额未配置则不建闸门」等价，此处容忍是为了让
   *     {@code @ConditionalOnBean} 装配不必再依赖配置值判定）
   */
  public RedisTenantQuotaGuard(
      RedissonClient redissonClient, long maxRequestsPerWindow, long windowMillis) {
    if (redissonClient == null) {
      throw new IllegalArgumentException("RedissonClient 不能为空");
    }
    this.redissonClient = redissonClient;
    this.maxRequestsPerWindow = maxRequestsPerWindow;
    this.windowMillis = Math.max(windowMillis, 1000L);
  }

  /** 从配置构造（与进程内版同源：同一份 {@code bone.ai.gateway.quota-*} 配置）。 */
  public RedisTenantQuotaGuard(RedissonClient redissonClient, LlmGatewayProperties props) {
    this(
        redissonClient,
        props.getQuotaMaxRequestsPerWindow(),
        props.getQuotaWindowSeconds() * 1000L);
  }

  @Override
  public void acquire(String tenantId) {
    // 与进程内版一致：租户缺失不拦（POC 单租户口径，配额不该变成可用性故障）
    if (tenantId == null || tenantId.isBlank() || maxRequestsPerWindow <= 0) {
      return;
    }
    long now = System.currentTimeMillis();
    long windowStart = now - (now % windowMillis);
    String key = KEY_PREFIX + tenantId + ":" + windowStart;
    try {
      RMap<String, Long> counter = redissonClient.getMap(key);
      // addAndGet 原子自增；首次创建即置 TTL（2 倍窗口，见类注释边界说明）
      Long used = counter.addAndGet("count", 1);
      if (used != null && used == 1L) {
        // Duration 重载：expire(long, TimeUnit) 在 Redisson 3.x 已标记过时
        counter.expire(Duration.ofMillis(windowMillis * 2));
      }
      if (used != null && used > maxRequestsPerWindow) {
        long remainingMs = windowStart + windowMillis - now;
        throw new BizException(
            429,
            "租户配额超限：window="
                + windowMillis
                + "ms, limit="
                + maxRequestsPerWindow
                + ", retryAfter="
                + remainingMs
                + "ms, tenantId="
                + tenantId,
            AiGatewayErrorCodes.QUOTA_EXCEEDED);
      }
    } catch (BizException e) {
      throw e; // 配额超限是业务语义，按契约上抛
    } catch (Exception e) {
      // Redis 不可用：fail-open（成本闸门让位业务可用性，见类注释决策记录）
      log.warn(
          "Quota check unavailable, fail-open (cost guard degraded, business not blocked): tenantId={}, key={}, error={}",
          tenantId,
          key,
          e.toString());
    }
  }

  /** 当前窗口已用请求数（观测 / 测试用；Redis 不可用时返回 -1 表示未知）。 */
  public long used(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return 0;
    }
    long now = System.currentTimeMillis();
    long windowStart = now - (now % windowMillis);
    try {
      Long used = redissonClient.<String, Long>getMap(KEY_PREFIX + tenantId + ":" + windowStart).get("count");
      return used == null ? 0 : used;
    } catch (Exception e) {
      log.warn("Quota usage read failed: tenantId={}, error={}", tenantId, e.toString());
      return -1;
    }
  }
}
