package com.bone.aiintegration.domain.service;

import java.net.URI;

/**
 * 出站 SSRF 防护基线（领域策略）：拒绝非 http(s) 方案、缺 host、以及命内网/云元数据保留地址的 endpoint。
 *
 * <p>endpoint 由编排节点传入，属不可信输入，平台必须在出站前拦一道，否则可借集成节点打内网或偷云凭证 （多租户平台头号风险）。把它放在领域层，使<b>应用层</b>（{@code
 * IntegrationApplicationService}）与 <b>基础设施适配器</b>（{@code HttpExternalSystemAdapter}）共用同一份判定——即使消费方
 * {@code @Bean} 覆盖 {@code ExternalSystemPort} 实现了一个不做防护的端口，平台仍会在应用层强制拦截，防护不可绕过。
 */
public final class SsrfGuard {

  private SsrfGuard() {}

  /** 校验出站 endpoint；非法（非 http(s) / 内网 / 云元数据）直接抛 {@link IllegalArgumentException}。 */
  public static void validate(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("集成 endpoint 不可为空");
    }
    URI parsed;
    try {
      parsed = URI.create(endpoint);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("集成 endpoint 非法 URI: " + endpoint, e);
    }
    String scheme = parsed.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new IllegalArgumentException("集成 endpoint 仅允许 http/https: " + endpoint);
    }
    String host = parsed.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("集成 endpoint 缺少 host: " + endpoint);
    }
    String h = host.toLowerCase();
    if (h.equals("localhost")
        || h.endsWith(".localhost")
        || h.equals("0.0.0.0")
        || h.endsWith(".internal")
        || h.endsWith(".svc")
        || h.endsWith(".cluster.local")
        || h.equals("169.254.169.254")) {
      throw new IllegalArgumentException("集成 endpoint 命中内网/元数据保留地址，拒绝出站: " + endpoint);
    }
  }
}
