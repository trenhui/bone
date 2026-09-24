package com.bone.iam.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IAM 单点登录配置（社区版默认关闭，商业版配置 IdP 后开启）。
 *
 * <p>/*
 *
 * <p>放在 {@code application.config} 与 {@link IamPasswordProperties} 同口径：SSO 是应用层用例的输入参数，
 * 且入站适配器（{@code AuthController}）需要读取它——若留在 {@code infrastructure.config}，适配器将反向依赖 {@code
 * infrastructure}（E-10.1），而应用层引用它会踩 CORE-02。
 */
@Data
@Component
@ConfigurationProperties(prefix = "bone.iam.sso")
public class IamSsoProperties {

  /** 社区版默认关闭；商业版配置 IdP 后开启 */
  private boolean enabled = false;

  private String provider = "oauth2";

  private String authorizationUrl = "";

  private String clientId = "";
}
