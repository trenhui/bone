package com.bone.metadata.catalog.infrastructure.enumsource;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 枚举选项源消费配置（bone-metadata-server → bone-system 字典 ACL）。
 *
 * <p>同构参照：{@code com.bone.blueprint.infrastructure.config.DictConsumptionProperties}—— 字典「下拉数据源」入口受
 * JWT 保护，消费方需以服务账号登录 IAM 取 Bearer token。各地址是本地的约定 默认值，跨环境由环境变量覆盖（{@code
 * bone.metadata.catalog.dict.*}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "bone.metadata.catalog.dict")
public class EnumSourceConsumptionProperties implements EnvironmentAware {

  /**
   * bone-system 网关地址（字典 /options 接口所在服务）。
   *
   * <p>默认 127.0.0.1 而非 localhost：macOS 把 localhost 解析为 ::1（IPv6 优先）， 而 Spring 默认绑 IPv4——用 localhost
   * 会在本机出现「服务起着却连不上」的假故障 （bone-local-e2e 已记录的同款陷阱）。
   */
  private String systemBaseUrl = "http://127.0.0.1:8083";

  /** IAM 网关地址（取服务间调用 token；同上用 127.0.0.1 避开 IPv6 解析）。 */
  private String iamBaseUrl = "http://127.0.0.1:8081";

  /** 服务间登录账号（默认复用平台管理员，跨环境由环境变量外部化）。 */
  private String serviceUsername = "admin";

  /** 服务间登录密码。 */
  private String servicePassword = "123456";

  /**
   * stale 缓存最大陈旧秒数（cache-aside + stale-on-error 的兜底窗口）。
   *
   * <p>读路径常态<b>实时</b>拉取真源，字典变更立即可见；仅在 bone-system 真不可达时回退上一次成功结果，
   * 且回退结果不得超过此陈旧上限——超过则视为过旧，放弃兜底按「不可达」处理（调用方 fail-soft 回落快照）。 写路径（物化）永不走此兜底。默认值 300s
   * 是宕机兜底的合理窗口，不影响漂移可见性。
   */
  private long cacheTtlSeconds = 300;

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @PostConstruct
  public void validate() {
    boolean nonProd = environment.acceptsProfiles(Profiles.of("dev", "test", "local", "default"));
    if ("123456".equals(servicePassword) && !nonProd) {
      throw new IllegalStateException(
          "非开发环境禁止使用默认服务密码 123456，请通过环境变量外部化 bone.metadata.catalog.dict.service-password");
    }
  }
}
