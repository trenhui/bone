package com.bone.aidoc.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.application.TrustedOcrApplicationService;
import com.bone.aidoc.config.TrustedOcrAutoConfiguration;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import com.bone.aidoc.domain.model.ValidationStatus;
import com.bone.aidoc.domain.service.ClaimFieldRisk;
import com.bone.aidoc.domain.service.OcrAcceptanceMetrics;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 端到端 HTTP 链路 PoC（ADR-0046：验证七层管线的<b>编排契约</b>，不是模型准确率）。
 *
 * <p>用 JDK 内置 HttpServer 在本地起四个假服务（票种分类 / Jev 质量分 / PaddleOCR / GLM-OCR）， Spring 上下文以 {@code
 * enabled=true} 装配真实 {@code HttpOcrClient} / {@code JevHttpQualityEvaluator} / {@code
 * HttpDocumentClassifier}，全程走真实 JDK HttpClient 传输。 覆盖：分类器重判、按票种引擎路由、多页 bbox 定位、Jev 质量分、L3
 * 交叉分歧升级人工、漏必填字段拦截、验收指标可计算、故障 fail-closed。
 *
 * <p><b>边界声明</b>：服务响应是固定桩数据，本测试<b>不构成任何字段级准确率结论</b>；真实准确率须按 ADR-0046
 * 用脱敏标注样本标定。这是「链路能跑通且故障不放行」的证据，不是「模型识别得准」的证据。
 */
class TrustedOcrHttpPipelinePoCTest {

  private static final String PRIMARY_RESPONSE =
      """
      {"fields":[
        {"name":"total_amount","value":"100.00","confidence":0.999,"page":2,"bbox":[10,20,100,40]},
        {"name":"invoice_no","value":"FP123","confidence":0.99,"page":1,"bbox":[1,2,30,20]}
      ],"line_items":[],"text":"合计金额 100.00","page_count":2,"confidence":0.99}
      """;

  private static final String SECONDARY_RESPONSE =
      """
      {"fields":[
        {"name":"total_amount","value":"100.00","confidence":0.98},
        {"name":"invoice_no","value":"FP999","confidence":0.98}
      ],"line_items":[],"text":"","page_count":1,"confidence":0.98}
      """;

  private static final String SCORES_RESPONSE =
      "{\"scores\":{\"total_amount\":0.99,\"invoice_no\":0.98}}";

  private static final String CLASSIFY_RESPONSE = "{\"doc_type\":\"INVOICE\"}";

  private static final AtomicInteger CLASSIFY_CALLS = new AtomicInteger();
  private static final AtomicInteger SCORE_CALLS = new AtomicInteger();
  private static final AtomicInteger PADDLE_CALLS = new AtomicInteger();
  private static final AtomicInteger GLM_CALLS = new AtomicInteger();

  private static HttpServer server;
  private static ExecutorService executor;
  private static String base;

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/classify",
        ex -> {
          CLASSIFY_CALLS.incrementAndGet();
          respond(ex, CLASSIFY_RESPONSE);
        });
    server.createContext(
        "/score",
        ex -> {
          SCORE_CALLS.incrementAndGet();
          respond(ex, SCORES_RESPONSE);
        });
    server.createContext(
        "/paddle/ocr",
        ex -> {
          PADDLE_CALLS.incrementAndGet();
          respond(ex, PRIMARY_RESPONSE);
        });
    server.createContext(
        "/glm/ocr",
        ex -> {
          GLM_CALLS.incrementAndGet();
          respond(ex, SECONDARY_RESPONSE);
        });
    executor = Executors.newFixedThreadPool(4);
    server.setExecutor(executor);
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) {
      server.stop(0);
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @BeforeEach
  void resetCounters() {
    CLASSIFY_CALLS.set(0);
    SCORE_CALLS.set(0);
    PADDLE_CALLS.set(0);
    GLM_CALLS.set(0);
  }

  @Test
  void full_pipeline_over_real_http_wires_all_seven_layers() {
    runnerWith(base, base)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              TrustedOcrDocument doc = parse(ctx.getBean(TrustedOcrApplicationService.class));

              // ② 分类器重判 GENERAL → INVOICE；按票种模板把 PaddleOCR 排为主引擎
              assertThat(CLASSIFY_CALLS.get()).isEqualTo(1);
              assertThat(PADDLE_CALLS.get()).isEqualTo(1);
              assertThat(GLM_CALLS.get()).isEqualTo(1);
              assertThat(SCORE_CALLS.get()).isEqualTo(1);
              assertThat(doc.docType()).isEqualTo(OcrDocType.INVOICE);
              assertThat(doc.primaryEngineId()).isEqualTo("paddle-ocr");
              assertThat(doc.engineIds()).containsExactly("paddle-ocr", "glm-ocr");
              assertThat(doc.pageCount()).isEqualTo(2);

              // ⑥ Jev 真实质量分（非启发式）
              assertThat(doc.qualityReport().evaluatorId()).isEqualTo("jev-http");
              assertThat(doc.qualityReport().scoreFor("total_amount")).isEqualTo(0.99);

              Map<String, OcrFieldEvidence> byName =
                  doc.fields().stream()
                      .collect(Collectors.toMap(OcrFieldEvidence::fieldName, f -> f));

              // ④ 多页逐字段定位：金额在第 2 页且带坐标；质量分达标 → 自动通过
              OcrFieldEvidence amount = byName.get("total_amount");
              assertThat(amount.page()).isEqualTo(2);
              assertThat(amount.hasEvidenceLocation()).isTrue();
              assertThat(amount.reviewStatus()).isEqualTo(ReviewStatus.AUTO_PASSED);

              // ⑥' L3 交叉分歧（次引擎给出不同发票号）→ 必须升级人工，不得自动通过
              assertThat(byName.get("invoice_no").reviewStatus())
                  .isEqualTo(ReviewStatus.MANUAL_REVIEW);

              // ⑦' 必填字段漏识别：补占位证据而非静默消失
              assertThat(doc.fields()).hasSize(3);
              OcrFieldEvidence missing = byName.get("invoice_date");
              assertThat(missing).isNotNull();
              assertThat(missing.value()).isEmpty();
              assertThat(missing.rawText()).contains("[MISSING_REQUIRED_FIELD]");
              assertThat(missing.validationStatus()).isEqualTo(ValidationStatus.FAILED);
              assertThat(missing.reviewStatus()).isEqualTo(ReviewStatus.MANUAL_REVIEW);
              assertThat(doc.decisionCounts().get(ReviewStatus.MANUAL_REVIEW)).isEqualTo(2);

              // 验收指标可计算化：关键字段 100% 定位覆盖、漏字段可计数
              OcrAcceptanceMetrics metrics = OcrAcceptanceMetrics.from(doc, new ClaimFieldRisk());
              assertThat(metrics.missingRequiredFields()).isEqualTo(1);
              assertThat(metrics.criticalFields()).isEqualTo(1);
              assertThat(metrics.criticalEvidenceCoverage()).isEqualTo(1.0);
              assertThat(metrics.meetsKeyFieldEvidenceCoverageTarget()).isTrue();
              assertThat(metrics.criticalAutoPassed()).isEqualTo(1);
              assertThat(metrics.manualReview()).isEqualTo(2);
            });
  }

  @Test
  void jev_service_down_fails_closed_instead_of_faking_quality() throws IOException {
    String dead = deadBase();
    runnerWith(base, dead)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              TrustedOcrDocument doc = parse(ctx.getBean(TrustedOcrApplicationService.class));

              // 服务不可达 → 质量分不可用，CRITICAL 强制二次识别，不得用启发式分数伪装模型判断
              assertThat(doc.qualityReport().evaluatorId()).isEqualTo("jev-http");
              assertThat(Double.isNaN(doc.qualityReport().scoreFor("total_amount"))).isTrue();
              Map<String, OcrFieldEvidence> byName =
                  doc.fields().stream()
                      .collect(Collectors.toMap(OcrFieldEvidence::fieldName, f -> f));
              assertThat(byName.get("total_amount").reviewStatus())
                  .isEqualTo(ReviewStatus.SECONDARY_RECOGNITION);
              OcrAcceptanceMetrics metrics = OcrAcceptanceMetrics.from(doc, new ClaimFieldRisk());
              assertThat(metrics.criticalAutoPassed()).isZero();
            });
  }

  @Test
  void classifier_service_down_falls_back_to_general_without_guessing() throws IOException {
    String dead = deadBase();
    runnerWith(dead, base)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              TrustedOcrDocument doc = parse(ctx.getBean(TrustedOcrApplicationService.class));

              // 分类失败 → 回落 GENERAL；GENERAL 无版式契约，不得凭空制造必填字段告警
              assertThat(CLASSIFY_CALLS.get()).isZero();
              assertThat(doc.docType()).isEqualTo(OcrDocType.GENERAL);
              assertThat(doc.fields()).hasSize(2);
              assertThat(doc.fields())
                  .noneMatch(
                      f -> f.rawText() != null && f.rawText().contains("[MISSING_REQUIRED_FIELD]"));
              OcrAcceptanceMetrics metrics = OcrAcceptanceMetrics.from(doc, new ClaimFieldRisk());
              assertThat(metrics.missingRequiredFields()).isZero();
            });
  }

  private static ApplicationContextRunner runnerWith(String classifierUrl, String jevUrl) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TrustedOcrAutoConfiguration.class))
        .withPropertyValues(
            "bone.ai.document.trusted-ocr.enabled=true",
            "bone.ai.document.trusted-ocr.paddle-url=" + base + "/paddle",
            "bone.ai.document.trusted-ocr.glm-url=" + base + "/glm",
            "bone.ai.document.trusted-ocr.classifier-url=" + classifierUrl,
            "bone.ai.document.trusted-ocr.jev-url=" + jevUrl,
            "bone.ai.document.trusted-ocr.timeout-ms=3000");
  }

  private static TrustedOcrDocument parse(TrustedOcrApplicationService service) {
    return service.parse(
        new ByteArrayInputStream("fake-image".getBytes(StandardCharsets.UTF_8)),
        "tenant-1",
        "ref-1",
        "doc-1",
        OcrDocType.GENERAL);
  }

  private static void respond(HttpExchange ex, String json) throws IOException {
    ex.getRequestBody().readAllBytes();
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    ex.sendResponseHeaders(200, body.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(body);
    }
  }

  /** 取一个已释放的本地端口，模拟服务不可达（连接被拒，快速失败）。 */
  private static String deadBase() throws IOException {
    try (ServerSocket ss = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return "http://127.0.0.1:" + ss.getLocalPort();
    }
  }
}
