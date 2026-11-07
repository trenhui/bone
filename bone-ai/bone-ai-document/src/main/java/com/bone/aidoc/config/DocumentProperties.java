package com.bone.aidoc.config;

import com.bone.aidoc.domain.model.RiskLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * bone-ai-document 外部化配置。
 *
 * <p>Docling sidecar 地址/超时在此配置；部署形态（独立容器 / 进程）属总案 §7 #2 待拍板项， 运行时由环境变量注入，本类不做部署假设。
 */
@ConfigurationProperties("bone.ai.document")
public class DocumentProperties {

  private final Docling docling = new Docling();
  private final TrustedOcr trustedOcr = new TrustedOcr();

  public Docling getDocling() {
    return docling;
  }

  public TrustedOcr getTrustedOcr() {
    return trustedOcr;
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

  /**
   * 可信 OCR 管线配置（ADR-0046，默认关闭——开启才装配骨架，零风险不影响既有上下文）。
   *
   * <p>阈值语义见 {@code DefaultReviewPolicy}：均为闭合下界，须由真实标注数据按票种/字段/风险分别标定， 禁止直接套用默认值作为生产阈值。 {@code
   * riskThresholds} 是按风险等级覆盖的标定入口；未覆盖的风险等级沿用 {@link
   * com.bone.aidoc.domain.service.DefaultReviewPolicy#defaultThresholds()} 骨架值。
   *
   * <p>引擎接入：配置对应 URL（如 {@code paddle-url}）即装配该引擎的真实 HTTP 适配器（复用 {@code HttpOcrClient}）， 不配置则不含该引擎。
   * {@code jev-url} / {@code classifier-url} 非空时分别接入真实质量评估与票种分类服务，覆盖安全默认实现。
   */
  public static class TrustedOcr {
    private boolean enabled = false;

    /** PaddleOCR 服务地址（如 http://paddle-ocr:8500）；非空即接入。 */
    private String paddleUrl = "";

    /** GLM-OCR 服务地址；非空即接入，并作为多模型交叉的次引擎。 */
    private String glmUrl = "";

    /** MiniCPM-OCR 服务地址；非空即接入。 */
    private String minicpmUrl = "";

    /** Jev 类质量评估服务地址；非空即接入，覆盖默认启发式质量评估器。 */
    private String jevUrl = "";

    /** 票种分类服务地址；非空即接入，覆盖默认 NoOp 分类器。 */
    private String classifierUrl = "";

    /** 识别服务调用超时（毫秒，默认 30000）。 */
    private long timeoutMs = 30000;

    /** 单份识别文件大小上限（字节，默认 50MB）——防内存耗尽型 DoS。 */
    private long maxBytes = 50L * 1024 * 1024;

    /** 旧扁平阈值的默认骨架值（仅用于判定是否被显式改动）。 */
    private static final double LEGACY_AUTO_PASS = 0.98;

    private static final double LEGACY_QUALITY_AUTO_PASS = 0.90;
    private static final double LEGACY_MANUAL = 0.80;
    private static final double LEGACY_MANUAL_QUALITY = 0.70;

    /**
     * @deprecated ADR-0046 Phase-2 起<b>唯一口径</b>为 {@code riskThresholds}（按风险等级分别标定）。 以下 4
     *     个扁平值<b>不再被采信</b>，仅为兼容旧配置文件绑定而保留。 显式配置成非默认值时，装配期会 fail-fast
     *     拒绝启动——绝不允许出现「配了以为生效、实际被静默忽略」的可信度假象。请改用 {@code
     *     bone.ai.document.trusted-ocr.risk-thresholds.<LEVEL>.*}。
     */
    @Deprecated private double autoPassThreshold = LEGACY_AUTO_PASS;

    @Deprecated private double qualityAutoPassThreshold = LEGACY_QUALITY_AUTO_PASS;
    @Deprecated private double manualThreshold = LEGACY_MANUAL;
    @Deprecated private double manualQualityThreshold = LEGACY_MANUAL_QUALITY;

    /** 按风险等级标定阈值覆盖（键为 CRITICAL/HIGH/MEDIUM/LOW）——唯一生效口径。 */
    private java.util.Map<RiskLevel, RiskThresholdConfig> riskThresholds =
        new java.util.EnumMap<>(RiskLevel.class);

    /** 旧扁平阈值是否被显式改动（用于装配期 fail-fast，避免静默失效）。 */
    public boolean hasLegacyFlatThresholds() {
      return Double.compare(autoPassThreshold, LEGACY_AUTO_PASS) != 0
          || Double.compare(qualityAutoPassThreshold, LEGACY_QUALITY_AUTO_PASS) != 0
          || Double.compare(manualThreshold, LEGACY_MANUAL) != 0
          || Double.compare(manualQualityThreshold, LEGACY_MANUAL_QUALITY) != 0;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getPaddleUrl() {
      return paddleUrl;
    }

    public void setPaddleUrl(String v) {
      this.paddleUrl = v;
    }

    public String getGlmUrl() {
      return glmUrl;
    }

    public void setGlmUrl(String v) {
      this.glmUrl = v;
    }

    public String getMinicpmUrl() {
      return minicpmUrl;
    }

    public void setMinicpmUrl(String v) {
      this.minicpmUrl = v;
    }

    public String getJevUrl() {
      return jevUrl;
    }

    public void setJevUrl(String v) {
      this.jevUrl = v;
    }

    public String getClassifierUrl() {
      return classifierUrl;
    }

    public void setClassifierUrl(String v) {
      this.classifierUrl = v;
    }

    public long getTimeoutMs() {
      return timeoutMs;
    }

    public void setTimeoutMs(long v) {
      this.timeoutMs = v;
    }

    public long getMaxBytes() {
      return maxBytes;
    }

    public void setMaxBytes(long v) {
      this.maxBytes = v;
    }

    /** 识别服务瞬时故障重试次数（默认 2；0 表示不重试）。 */
    private int maxRetries = 2;

    /** 重试退避基准毫秒（默认 20，实际延迟按 2^n 指数 + 随机抖动，封顶 1s）。 */
    private long retryBaseDelayMs = 20;

    public int getMaxRetries() {
      return maxRetries;
    }

    public void setMaxRetries(int v) {
      this.maxRetries = v;
    }

    public long getRetryBaseDelayMs() {
      return retryBaseDelayMs;
    }

    public void setRetryBaseDelayMs(long v) {
      this.retryBaseDelayMs = v;
    }

    public double getAutoPassThreshold() {
      return autoPassThreshold;
    }

    public void setAutoPassThreshold(double v) {
      this.autoPassThreshold = v;
    }

    public double getQualityAutoPassThreshold() {
      return qualityAutoPassThreshold;
    }

    public void setQualityAutoPassThreshold(double v) {
      this.qualityAutoPassThreshold = v;
    }

    public double getManualThreshold() {
      return manualThreshold;
    }

    public void setManualThreshold(double v) {
      this.manualThreshold = v;
    }

    public double getManualQualityThreshold() {
      return manualQualityThreshold;
    }

    public void setManualQualityThreshold(double v) {
      this.manualQualityThreshold = v;
    }

    public java.util.Map<RiskLevel, RiskThresholdConfig> getRiskThresholds() {
      return riskThresholds;
    }

    public void setRiskThresholds(java.util.Map<RiskLevel, RiskThresholdConfig> v) {
      this.riskThresholds = v;
    }
  }

  /** 单风险等级阈值配置（绑定 {@code bone.ai.document.trusted-ocr.risk-thresholds.<LEVEL>.*}）。 */
  public static class RiskThresholdConfig {
    private double autoPassConfidence = 0.98;
    private double autoPassQuality = 0.90;
    private double manualConfidence = 0.80;
    private double manualQuality = 0.70;

    public double getAutoPassConfidence() {
      return autoPassConfidence;
    }

    public void setAutoPassConfidence(double v) {
      this.autoPassConfidence = v;
    }

    public double getAutoPassQuality() {
      return autoPassQuality;
    }

    public void setAutoPassQuality(double v) {
      this.autoPassQuality = v;
    }

    public double getManualConfidence() {
      return manualConfidence;
    }

    public void setManualConfidence(double v) {
      this.manualConfidence = v;
    }

    public double getManualQuality() {
      return manualQuality;
    }

    public void setManualQuality(double v) {
      this.manualQuality = v;
    }
  }
}
