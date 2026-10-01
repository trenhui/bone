package com.bone.blueprint.infrastructure.gateway.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.blueprint.domain.gateway.MasterDataGateway.ProductView;
import com.bone.blueprint.infrastructure.config.MasterDataConsumptionProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * B 段（消费者侧契约验证）：bone-masterdata ↔ bone-blueprint 全链路的另一端。
 *
 * <p><b>目的</b>：用<b>真实的</b> {@link MasterDataAclAdapter}（生产线代码，零 mock）打一条真实 HTTP 到本地 stub 服务， stub
 * 返回的 JSON 形态与 A 段（masterdata 真实产出）完全一致。证明 blueprint 能正确把 masterdata 的「已发布记录」
 * 解析为领域视图：商品（code/name/unitPrice）、客户等级编码、等级折扣率。
 *
 * <p><b>为什么不连真实 masterdata</b>：本机无 MySQL / IAM / 网关，且 ACL
 * 登录与实体/记录查询都走网关同前缀（/api/v1/iam、/api/v1/masterdata）， 单 JVM 内无法廉价提供。用同形态 stub 验证「消费者解析」是契约测试的标准做法； 与
 * A 段（生产者形态）合起来即是完整链路契约，真实多服务联调留待完整环境。
 *
 * <p><b>覆盖的边界</b>：业务键内存匹配（同实体多记录按 code 命中）、未发布/不存在数据按「不可达/未配置」降级返回 empty。
 */
class MasterDataAclAdapterE2ETest {

  private HttpServer server;
  private MasterDataAclAdapter adapter;

  @BeforeEach
  void startStubAndAdapter() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          Map<String, String> q = queryParams(exchange.getRequestURI().getQuery());
          String body;
          if ("/api/v1/iam/login".equals(path)) {
            body = "{\"code\":0,\"message\":\"ok\",\"data\":{\"token\":\"stub-token\"}}";
          } else if ("/api/v1/masterdata/entities".equals(path)) {
            body = entityPayload(q.get("keyword"));
          } else if ("/api/v1/masterdata/records/by-code".equals(path)) {
            // 主路径契约：masterdata 按业务键点查，只返回「已发布+当前版本+生效窗口含此刻」的记录
            body = byCodePayload(q.get("masterDataEntityId"), q.get("code"));
          } else if ("/api/v1/masterdata/records".equals(path)) {
            body = recordPayload(q.get("masterDataEntityId"));
          } else {
            exchange.sendResponseHeaders(404, -1);
            return;
          }
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });
    server.start();

    MasterDataConsumptionProperties props = new MasterDataConsumptionProperties();
    props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
    props.setServiceUsername("admin");
    props.setServicePassword("123456");
    props.setProductEntityCode("MD_PRODUCT");
    props.setCustomerEntityCode("CUSTOMER");
    props.setLevelEntityCode("CUSTOMER_LEVEL");
    props.setCacheTtlSeconds(0); // 每次都重新拉取，避免缓存掩盖 stub 行为
    props.setFetchPageSize(500);
    adapter = new MasterDataAclAdapter(props);
  }

  @AfterEach
  void stopStub() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("B段-商品：按 code 命中已发布记录，映射为 ProductView(code,name,unitPrice)")
  void findPublishedProductMapsCorrectly() {
    Optional<ProductView> p1 = adapter.findPublishedProduct("P001");
    assertThat(p1).isPresent();
    assertThat(p1.get().code()).isEqualTo("P001");
    assertThat(p1.get().name()).isEqualTo("测试商品");
    assertThat(p1.get().unitPrice()).isEqualByComparingTo("99.0");

    // 同实体多记录：按业务键 P002 精确命中（验证内存匹配，而非整拉第一条）
    Optional<ProductView> p2 = adapter.findPublishedProduct("P002");
    assertThat(p2).isPresent();
    assertThat(p2.get().code()).isEqualTo("P002");
    assertThat(p2.get().unitPrice()).isEqualByComparingTo("199.0");
  }

  @Test
  @DisplayName("B段-客户等级：按 customer_code 命中，返回 level_code")
  void findCustomerLevelCodeMapsCorrectly() {
    Optional<String> level = adapter.findCustomerLevelCode("C001");
    assertThat(level).contains("VIP");
  }

  @Test
  @DisplayName("B段-折扣率：按 level_code 命中，返回 (0,1] 区间内的折扣率")
  void findLevelDiscountRateMapsCorrectly() {
    Optional<java.math.BigDecimal> rate = adapter.findLevelDiscountRate("VIP");
    assertThat(rate).contains(new java.math.BigDecimal("0.88"));
  }

  @Test
  @DisplayName("B段-降级：不存在/未配置的数据按 empty 降级（不抛异常）")
  void downgradeWhenNotFound() {
    assertThat(adapter.findPublishedProduct(null)).isEmpty();
    assertThat(adapter.findPublishedProduct("")).isEmpty();
    assertThat(adapter.findPublishedProduct("NO_SUCH")).isEmpty();
    assertThat(adapter.findCustomerLevelCode("NO_SUCH")).isEmpty();
    assertThat(adapter.findLevelDiscountRate("NO_SUCH")).isEmpty();
  }

  // ===================== stub 数据（与 A 段 masterdata 真实产出形态一致） =====================

  private String entityPayload(String keyword) {
    // 故意混入一条 DRAFT 伪记录，验证适配器只认 PUBLISHED
    return "{"
        + "\"code\":0,"
        + "\"data\":{\"records\":["
        + "{\"id\":1,\"entityCode\":\"MD_PRODUCT\",\"status\":\"PUBLISHED\",\"name\":\"商品主数据\"},"
        + "{\"id\":2,\"entityCode\":\"CUSTOMER\",\"status\":\"PUBLISHED\",\"name\":\"客户主数据\"},"
        + "{\"id\":3,\"entityCode\":\"CUSTOMER_LEVEL\",\"status\":\"PUBLISHED\",\"name\":\"客户等级折扣率\"},"
        + "{\"id\":9,\"entityCode\":\"MD_PRODUCT\",\"status\":\"DRAFT\",\"name\":\"草稿噪音\"}"
        + "]}}";
  }

  /**
   * 点查契约 stub：与 masterdata {@code GET /records/by-code} 真实响应形态一致—— 命中返回记录
   * DTO（recordCode/displayName/data）， 未命中 data 为 null（不区分不存在/未发布/已过期）。 客户/等级记录尚未补登 record_code，因此只有
   * data JSON（验证存量回退路径被触发前点查也放行空 recordCode 形态）。
   */
  private String byCodePayload(String entityId, String code) {
    if ("1".equals(entityId) && "P001".equals(code)) {
      return "{\"code\":0,\"data\":{\"id\":10,\"status\":\"PUBLISHED\",\"recordCode\":\"P001\","
          + "\"displayName\":\"测试商品\",\"data\":\"{\\\"code\\\":\\\"P001\\\",\\\"name\\\":\\\"测试商品\\\",\\\"price\\\":99.0}\"}}";
    }
    if ("1".equals(entityId) && "P002".equals(code)) {
      return "{\"code\":0,\"data\":{\"id\":11,\"status\":\"PUBLISHED\",\"recordCode\":\"P002\","
          + "\"displayName\":\"高端商品\",\"data\":\"{\\\"code\\\":\\\"P002\\\",\\\"name\\\":\\\"高端商品\\\",\\\"price\\\":199.0}\"}}";
    }
    if ("2".equals(entityId) && "C001".equals(code)) {
      return "{\"code\":0,\"data\":{\"id\":20,\"status\":\"PUBLISHED\","
          + "\"data\":\"{\\\"customer_code\\\":\\\"C001\\\",\\\"level_code\\\":\\\"VIP\\\"}\"}}";
    }
    if ("3".equals(entityId) && "VIP".equals(code)) {
      return "{\"code\":0,\"data\":{\"id\":30,\"status\":\"PUBLISHED\","
          + "\"data\":\"{\\\"level_code\\\":\\\"VIP\\\",\\\"discount_rate\\\":0.88}\"}}";
    }
    return "{\"code\":0,\"data\":null}";
  }

  private String recordPayload(String entityId) {
    if ("1".equals(entityId)) {
      // 商品：data 为「字符串形态 JSON」，含 code/name/price；两条已发布按 code 区分
      return "{"
          + "\"code\":0,"
          + "\"data\":{\"records\":["
          + "{\"id\":10,\"status\":\"PUBLISHED\",\"data\":\"{\\\"code\\\":\\\"P001\\\",\\\"name\\\":\\\"测试商品\\\",\\\"price\\\":99.0}\"},"
          + "{\"id\":11,\"status\":\"PUBLISHED\",\"data\":\"{\\\"code\\\":\\\"P002\\\",\\\"name\\\":\\\"高端商品\\\",\\\"price\\\":199.0}\"},"
          + "{\"id\":12,\"status\":\"DRAFT\",\"data\":\"{\\\"code\\\":\\\"P003\\\",\\\"name\\\":\\\"草稿商品\\\",\\\"price\\\":9.9}\"}"
          + "]}}";
    }
    if ("2".equals(entityId)) {
      // 客户：data 含 customer_code/level_code
      return "{"
          + "\"code\":0,"
          + "\"data\":{\"records\":["
          + "{\"id\":20,\"status\":\"PUBLISHED\",\"data\":\"{\\\"customer_code\\\":\\\"C001\\\",\\\"level_code\\\":\\\"VIP\\\"}\"}"
          + "]}}";
    }
    if ("3".equals(entityId)) {
      // 客户等级折扣率：data 含 level_code/discount_rate
      return "{"
          + "\"code\":0,"
          + "\"data\":{\"records\":["
          + "{\"id\":30,\"status\":\"PUBLISHED\",\"data\":\"{\\\"level_code\\\":\\\"VIP\\\",\\\"discount_rate\\\":0.88}\"}"
          + "]}}";
    }
    return "{\"code\":0,\"data\":{\"records\":[]}}";
  }

  private Map<String, String> queryParams(String query) {
    Map<String, String> map = new HashMap<>();
    if (query == null || query.isBlank()) {
      return map;
    }
    for (String pair : query.split("&")) {
      int idx = pair.indexOf('=');
      if (idx > 0) {
        map.put(pair.substring(0, idx), pair.substring(idx + 1));
      }
    }
    return map;
  }
}
