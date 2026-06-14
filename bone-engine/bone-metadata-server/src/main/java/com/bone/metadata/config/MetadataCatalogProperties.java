package com.bone.metadata.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 元数据 catalog / runtime 横切配置（缓存、幂等）。 */
@Data
@ConfigurationProperties(prefix = "bone.metadata.catalog")
public class MetadataCatalogProperties {

  /** 已发布 RUNTIME 实体缓存 TTL（Caffeine / Redis 共用）。 */
  private Duration runtimeEntityCacheTtl = Duration.ofSeconds(60);

  private RuntimeEntityCache runtimeEntityCache = new RuntimeEntityCache();
  private Idempotency idempotency = new Idempotency();

  @Data
  public static class RuntimeEntityCache {
    /** memory（Caffeine，默认）或 redis（集群）。 */
    private String backend = "memory";

    private String keyPrefix = "bone:metadata:runtime-entity:";
  }

  @Data
  public static class Idempotency {
    /** memory（默认）或 redis（集群）。 */
    private String backend = "memory";

    private String keyPrefix = "bone:metadata:idempotency:";
  }
}
