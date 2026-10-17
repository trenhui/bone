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

    /** 上传大小上限（字节，默认 50MB）——防内存耗尽型 DoS。 */
    private long maxBytes = 50L * 1024 * 1024;

    /** 页数上限（默认 1000）——防超大文档拖垮下游切片与判定。 */
    private int maxPages = 1000;

    /** 瞬时故障重试次数（默认 2，详设「重试 ≤2 次」）。 */
    private int maxRetries = 2;

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

    public long getMaxBytes() {
      return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
      this.maxBytes = maxBytes;
    }

    public int getMaxPages() {
      return maxPages;
    }

    public void setMaxPages(int maxPages) {
      this.maxPages = maxPages;
    }

    public int getMaxRetries() {
      return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
    }
  }
}
