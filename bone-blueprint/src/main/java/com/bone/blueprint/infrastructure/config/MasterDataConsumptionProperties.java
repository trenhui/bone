package com.bone.blueprint.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 主数据消费配置（bone-blueprint → bone-masterdata ACL）。
 *
 * <p>实体编码 / 等级编码是主数据建模时确定的约定值，跨环境不变；服务账号用于服务间登录网关取 token。
 */
@Data
@Component
@ConfigurationProperties(prefix = "bone.masterdata")
public class MasterDataConsumptionProperties implements EnvironmentAware {

  /** masterdata 网关地址（走统一网关，登录后携 Bearer token 访问）。 */
  private String baseUrl = "http://localhost:8888";

  /** 服务间登录账号（样板工程默认复用平台管理员）。 */
  private String serviceUsername = "admin";

  /** 服务间登录密码。 */
  private String servicePassword = "123456";

  /** 商品主数据实体编码（建模约定）。 */
  private String productEntityCode = "MD_PRODUCT";

  /** 客户等级折扣率实体编码（建模约定）。 */
  private String levelEntityCode = "CUSTOMER_LEVEL";

  /** 客户主数据实体编码（建模约定）。 */
  private String customerEntityCode = "CUSTOMER";

  /** 主数据查询结果本地缓存秒数（避免每单都打主数据服务）。 */
  private long cacheTtlSeconds = 60;

  /** 单次拉取已发布记录的页大小上限。 */
  private int fetchSize = 500;

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
          "非开发环境禁止使用默认服务密码 123456，请通过环境变量外部化 bone.masterdata.service-password");
    }
  }
}
