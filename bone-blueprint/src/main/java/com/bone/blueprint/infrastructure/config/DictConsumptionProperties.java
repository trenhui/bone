package com.bone.blueprint.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 字典消费配置（bone-blueprint → bone-system 字典 ACL）。
 *
 * <p>字典「下拉数据源」入口受 JWT 保护，消费方需以服务账号登录 IAM 取 Bearer token（与 MasterDataConsumptionProperties 同范式）。
 * 各地址是本地的约定默认值，跨环境由环境变量覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "bone.dict")
public class DictConsumptionProperties {

  /** bone-system 网关地址（字典 /options 接口所在服务）。 */
  private String systemBaseUrl = "http://localhost:8083";

  /** IAM 网关地址（取服务间调用 token）。 */
  private String iamBaseUrl = "http://localhost:8081";

  /** 服务间登录账号（样板工程默认复用平台管理员）。 */
  private String serviceUsername = "admin";

  /** 服务间登录密码。 */
  private String servicePassword = "123456";

  /** 字典选项本地缓存秒数（字典是「几乎不变」的配置，缓存友好，避免每单都打字典服务）。 */
  private long cacheTtlSeconds = 300;
}
