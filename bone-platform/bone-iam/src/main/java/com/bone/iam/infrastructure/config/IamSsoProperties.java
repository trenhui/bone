package com.bone.iam.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
