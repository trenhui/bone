package com.bone.aidoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * bone-ai-document 外部化配置。
 *
 * <p>Docling sidecar 地址/超时在此配置；部署形态（独立容器 / 进程）属总案 §7 #2 待拍板项， 运行时由环境变量注入，本类不做部署假设。
 */
@ConfigurationProperties("bone.ai.document")
public class DocumentProperties {

  private final Docling docling = new Docling();

  public Docling getDocling() {
    return docling;
  }

  /** Docling sidecar 配置（默认关闭，开启即作为主解析器替换 PDFBox）。 */
  public static class Docling {
    private boolean enabled = false;
    private String baseUrl = "http://localhost:5001";
    private long timeoutMs = 30000;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public long getTimeoutMs() {
      return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
      this.timeoutMs = timeoutMs;
    }
  }
}
