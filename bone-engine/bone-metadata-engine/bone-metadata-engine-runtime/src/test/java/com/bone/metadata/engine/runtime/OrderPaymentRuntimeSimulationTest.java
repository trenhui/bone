package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * 订单 / 支付场景的「前后端」模拟集成测试（模式 B 运行时数据面）。
 *
 * <p><b>真源</b>：实体字段与约束直接取自 {@code bone-blueprint} 聚合根 {@code
 * com.bone.blueprint.domain.model.order.Order}（→ t_order）与 {@code
 * com.bone.blueprint.domain.model.payment.Payment}（→ bp_payment）：customer_id / total_amount /
 * status、 order_id / amount / channel / channel_trade_no(唯一,支付幂等键) 等。
 *
 * <p><b>物理表形状</b>：与 {@code JdbcPhysicalStructureGatewayAdapter.buildCreateTable} 自动建表产物完全一致 （id /
 * tenant_id / version / deleted + 业务列 + 审计列 created_at/updated_at/created_by/updated_by），
 * 因此本测试同样覆盖了「发布即自动建表」后的运行时读写的真实形态。
 *
 * <p><b>前端模拟</b>：{@link SimulatedFrontendClient} 按 {@code RuntimeRecordController} 的契约包装 {@link
 * JdbcRuntimeRecordService}——创建返回 201、校验失败 400({@code META_RUNTIME_VALIDATION_FAILED})、 唯一冲突
 * 409({@code META_RUNTIME_DUPLICATE})、版本冲突 412({@code META_PRECONDITION_FAILED})、 不存在 404；并复刻
 * {@code GlobalExceptionHandler} 的错误码映射，使测试即前端契约。
 *
 * <p>运行于 H2（MySQL 兼容模式），无需外部 MySQL；可控、可重复、CI 友好。
 */
@DisplayName("订单/支付场景 · 元数据运行时 CRUD 模拟（蓝图真源）")
class OrderPaymentRuntimeSimulationTest {

  private static final long TENANT_1 = 1L;
  private static final long TENANT_2 = 2L;

  private EmbeddedDatabase db;
  private NamedParameterJdbcTemplate jdbc;
  private RuntimeEntityCatalog catalog;
  private JdbcRuntimeRecordService service;
  private final AtomicLong idGen = new AtomicLong(1000);

  @BeforeEach
  void setUp() {
    // H2 MySQL 兼容模式：支持反引号标识符 + information_schema 大小写不敏感，贴近生产 MySQL 形态
    db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("md_order_payment_sim;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
            .build();
    jdbc = new NamedParameterJdbcTemplate(db);

    createOrderTable();
    createPaymentTable();

    catalog =
        (code, tenantId) -> {
          if ("bp_order".equals(code)) {
            return Optional.of(orderEntity());
          }
          if ("bp_payment".equals(code)) {
            return Optional.of(paymentEntity());
          }
          return Optional.empty();
        };
    service = new JdbcRuntimeRecordService(jdbc, catalog);
  }

  @AfterEach
  void tearDown() {
    if (db != null) {
      db.shutdown();
    }
  }

  // ===================== 物理表（与 buildCreateTable 产物一致） =====================

  private void createOrderTable() {
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE `bp_order` ("
                + "`id` BIGINT NOT NULL,"
                + "`tenant_id` BIGINT NOT NULL,"
                + "`customer_id` BIGINT,"
                + "`total_amount` DECIMAL(20,6),"
                + "`status` VARCHAR(255),"
                + "`version` INT NOT NULL DEFAULT 0,"
                + "`deleted` SMALLINT NOT NULL DEFAULT 0,"
                + "`created_at` DATETIME,"
                + "`updated_at` DATETIME,"
                + "`created_by` VARCHAR(64),"
                + "`updated_by` VARCHAR(64),"
                + "PRIMARY KEY (`id`))");
  }

  private void createPaymentTable() {
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE `bp_payment` ("
                + "`id` BIGINT NOT NULL,"
                + "`tenant_id` BIGINT NOT NULL,"
                + "`order_id` BIGINT,"
                + "`customer_id` BIGINT,"
                + "`amount` DECIMAL(20,6),"
                + "`channel` VARCHAR(255),"
                + "`status` VARCHAR(255),"
                + "`channel_trade_no` VARCHAR(255),"
                + "`pay_url` VARCHAR(255),"
                + "`version` INT NOT NULL DEFAULT 0,"
                + "`deleted` SMALLINT NOT NULL DEFAULT 0,"
                + "`created_at` DATETIME,"
                + "`updated_at` DATETIME,"
                + "`created_by` VARCHAR(64),"
                + "`updated_by` VARCHAR(64),"
                + "PRIMARY KEY (`id`))");
  }

  // ===================== 实体快照（蓝图字段） =====================

  private PublishedRuntimeEntity orderEntity() {
    return new PublishedRuntimeEntity(
        "bp_order",
        "bp_order",
        "id",
        TENANT_1,
        List.of(
            new RuntimeFieldColumn("id", "LONG", false, false, true),
            new RuntimeFieldColumn("customer_id", "LONG", true, false, false),
            new RuntimeFieldColumn("total_amount", "DECIMAL", true, false, false),
            new RuntimeFieldColumn("status", "STRING", true, false, false)));
  }

  private PublishedRuntimeEntity paymentEntity() {
    return new PublishedRuntimeEntity(
        "bp_payment",
        "bp_payment",
        "id",
        TENANT_1,
        List.of(
            new RuntimeFieldColumn("id", "LONG", false, false, true),
            new RuntimeFieldColumn("order_id", "LONG", true, false, false),
            new RuntimeFieldColumn("customer_id", "LONG", true, false, false),
            new RuntimeFieldColumn("amount", "DECIMAL", true, false, false),
            new RuntimeFieldColumn("channel", "STRING", true, false, false),
            new RuntimeFieldColumn("status", "STRING", true, false, false),
            new RuntimeFieldColumn("channel_trade_no", "STRING", false, true, false),
            new RuntimeFieldColumn("pay_url", "STRING", false, false, false)));
  }

  // ===================== 前端模拟客户端 =====================

  /** 复刻 RuntimeRecordController + GlobalExceptionHandler 的 HTTP 契约，使测试即前端契约。 */
  private final class SimulatedFrontendClient {
    private final String entityCode;

    SimulatedFrontendClient(String entityCode) {
      this.entityCode = entityCode;
    }

    SimResponse create(Map<String, Object> body, String operator) {
      try {
        Map<String, Object> r =
            service.create(entityCode, TENANT_1, body, idGen.incrementAndGet(), operator);
        return SimResponse.ok(201, r);
      } catch (RuntimeRecordException e) {
        return SimResponse.from(e);
      }
    }

    SimResponse update(
        String recordId, Map<String, Object> body, Integer ifMatch, String operator) {
      try {
        Map<String, Object> r =
            service.update(entityCode, TENANT_1, recordId, body, ifMatch, operator);
        return SimResponse.ok(200, r);
      } catch (RuntimeRecordException e) {
        return SimResponse.from(e);
      }
    }

    SimResponse get(String recordId, long tenantId) {
      try {
        Map<String, Object> r = service.getById(entityCode, tenantId, recordId);
        return SimResponse.ok(200, r);
      } catch (RuntimeRecordException e) {
        return SimResponse.from(e);
      }
    }

    SimResponse delete(String recordId, String operator) {
      try {
        service.delete(entityCode, TENANT_1, recordId);
        return SimResponse.ok(200, Map.of());
      } catch (RuntimeRecordException e) {
        return SimResponse.from(e);
      }
    }

    SimResponse page(int page, int size) {
      var p = service.page(entityCode, TENANT_1, page, size);
      return SimResponse.ok(200, Map.of("records", p.getRecords(), "total", p.getTotal()));
    }
  }

  private record SimResponse(
      int status, String errorCode, Map<String, Object> body, String message) {
    static SimResponse ok(int status, Map<String, Object> body) {
      return new SimResponse(status, null, body, null);
    }

    static SimResponse from(RuntimeRecordException e) {
      int status =
          switch (e.getErrorCode()) {
            case "META_RUNTIME_RECORD_NOT_FOUND", "META_RUNTIME_ENTITY_NOT_FOUND" -> 404;
            case "META_PRECONDITION_FAILED" -> 412;
            case "META_RUNTIME_VALIDATION_FAILED" -> 400;
            case "META_RUNTIME_DUPLICATE" -> 409;
            default -> 400;
          };
      return new SimResponse(status, e.getErrorCode(), null, e.getMessage());
    }

    boolean success() {
      return status >= 200 && status < 300;
    }
  }

  // ===================== 场景 1：创建订单 + 审计列 + 系统列 =====================

  @Test
  @DisplayName("前端创建订单 → 201，审计列/租户列/版本号正确写入")
  void orderCreate_writesAuditAndSystemColumns() {
    var client = new SimulatedFrontendClient("bp_order");
    SimResponse resp =
        client.create(
            Map.of("customer_id", 1001L, "total_amount", 199.99, "status", "CREATED"), "u-shop-1");

    assertTrue(resp.success(), "创建应成功: " + resp);
    Map<String, Object> row = resp.body();
    assertNotNull(row.get("id"));
    assertEquals(1L, ((Number) row.get("tenant_id")).longValue(), "租户隔离列");
    assertEquals(0L, ((Number) row.get("version")).longValue(), "乐观锁初值");
    assertEquals(0, ((Number) row.get("deleted")).intValue(), "软删初值");
    assertEquals("u-shop-1", row.get("created_by"), "P0-2 创建者审计");
    assertEquals("u-shop-1", row.get("updated_by"), "P0-2 更新者审计");
    assertNotNull(row.get("created_at"), "P0-2 创建时间");
    assertNotNull(row.get("updated_at"), "P0-2 更新时间");
    assertEquals("CREATED", row.get("status"));
    assertEquals(199.99, ((Number) row.get("total_amount")).doubleValue(), 0.001);
  }

  // ===================== 场景 2：创建支付单（引用订单） =====================

  @Test
  @DisplayName("前端创建支付单（引用订单、唯一渠道流水号）→ 201")
  void paymentCreate_withUniqueChannelTradeNo() {
    var orders = new SimulatedFrontendClient("bp_order");
    SimResponse o =
        orders.create(
            Map.of("customer_id", 1001L, "total_amount", 88.0, "status", "CREATED"), "u-shop-1");
    String orderId = String.valueOf(o.body().get("id"));

    var pays = new SimulatedFrontendClient("bp_payment");
    SimResponse p =
        pays.create(
            Map.of(
                "order_id",
                Long.parseLong(orderId),
                "customer_id",
                1001L,
                "amount",
                88.0,
                "channel",
                "WECHAT",
                "status",
                "PENDING",
                "channel_trade_no",
                "WX-INV-0001"),
            "u-pay");

    assertTrue(p.success(), "支付单创建应成功: " + p);
    assertEquals("PENDING", p.body().get("status"));
    assertEquals("WX-INV-0001", p.body().get("channel_trade_no"));
    assertEquals("u-pay", p.body().get("created_by"), "P0-2 支付操作者审计");
  }

  // ===================== 场景 3：必填校验 =====================

  @Test
  @DisplayName("缺失必填字段 total_amount → 400 META_RUNTIME_VALIDATION_FAILED")
  void validation_missingRequiredField_returns400() {
    var client = new SimulatedFrontendClient("bp_order");
    SimResponse resp = client.create(Map.of("customer_id", 1001L, "status", "CREATED"), "u-x");

    assertEquals(400, resp.status());
    assertEquals("META_RUNTIME_VALIDATION_FAILED", resp.errorCode());
    assertTrue(resp.message().contains("total_amount"), "应指出缺失字段");
  }

  // ===================== 场景 4：类型校验 =====================

  @Test
  @DisplayName("total_amount 传字符串（类型不符）→ 400 META_RUNTIME_VALIDATION_FAILED")
  void validation_typeMismatch_returns400() {
    var client = new SimulatedFrontendClient("bp_order");
    SimResponse resp =
        client.create(
            Map.of("customer_id", 1001L, "total_amount", "不是数字", "status", "CREATED"), "u-x");

    assertEquals(400, resp.status());
    assertEquals("META_RUNTIME_VALIDATION_FAILED", resp.errorCode());
    assertTrue(resp.message().contains("total_amount"));
  }

  // ===================== 场景 5：唯一冲突（支付幂等键） =====================

  @Test
  @DisplayName("重复 channel_trade_no → 409 META_RUNTIME_DUPLICATE（支付回调幂等去重）")
  void duplicateUniqueChannelTradeNo_returns409() {
    var pays = new SimulatedFrontendClient("bp_payment");
    SimResponse first =
        pays.create(
            Map.of(
                "order_id",
                1L,
                "customer_id",
                1001L,
                "amount",
                10.0,
                "channel",
                "WECHAT",
                "status",
                "PENDING",
                "channel_trade_no",
                "WX-DUP-1"),
            "u-pay");
    assertTrue(first.success());

    SimResponse second =
        pays.create(
            Map.of(
                "order_id",
                1L,
                "customer_id",
                1001L,
                "amount",
                10.0,
                "channel",
                "WECHAT",
                "status",
                "PENDING",
                "channel_trade_no",
                "WX-DUP-1"),
            "u-pay");

    assertEquals(409, second.status());
    assertEquals("META_RUNTIME_DUPLICATE", second.errorCode());
  }

  // ===================== 场景 6：乐观锁（If-Match） =====================

  @Test
  @DisplayName("用过期 If-Match 版本更新 → 412 META_PRECONDITION_FAILED")
  void optimisticLock_staleVersion_returns412() {
    var client = new SimulatedFrontendClient("bp_order");
    SimResponse created =
        client.create(
            Map.of("customer_id", 1001L, "total_amount", 50.0, "status", "CREATED"), "u-1");
    String id = String.valueOf(created.body().get("id"));
    int currentVersion = ((Number) created.body().get("version")).intValue();

    // 用错误的（更小的）版本号模拟并发冲突
    SimResponse conflict = client.update(id, Map.of("status", "PAID"), currentVersion - 1, "u-2");
    assertEquals(412, conflict.status());
    assertEquals("META_PRECONDITION_FAILED", conflict.errorCode());

    // 用正确版本号应成功
    SimResponse ok = client.update(id, Map.of("status", "PAID"), currentVersion, "u-2");
    assertTrue(ok.success(), "正确版本号应更新成功: " + ok);
  }

  // ===================== 场景 7：多租户隔离 + 按租户审计 =====================

  @Test
  @DisplayName("租户 A 无法读取租户 B 的订单；审计列按操作者区分")
  void crossTenantIsolation_andPerTenantAudit() {
    var c1 = new SimulatedFrontendClient("bp_order");
    SimResponse o1 =
        c1.create(
            Map.of("customer_id", 1001L, "total_amount", 1.0, "status", "CREATED"), "u-tenant-1");
    String id1 = String.valueOf(o1.body().get("id"));

    // 以租户 2、操作者 u-tenant-2 再建一笔
    Map<String, Object> r2 =
        service.create(
            "bp_order",
            TENANT_2,
            Map.of("customer_id", 2002L, "total_amount", 2.0, "status", "CREATED"),
            idGen.incrementAndGet(),
            "u-tenant-2");
    String id2 = String.valueOf(r2.get("id"));

    // 租户 1 看不到租户 2 的订单
    SimResponse crossGet = c1.get(id2, TENANT_1);
    assertEquals(404, crossGet.status(), "跨租户读取应 404");

    // 各自审计列归属正确
    assertEquals("u-tenant-1", o1.body().get("created_by"));
    assertEquals("u-tenant-2", r2.get("created_by"));

    // 租户 1 列表仅 1 条
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> t1Rows =
        (List<Map<String, Object>>) c1.page(1, 10).body().get("records");
    assertEquals(1, t1Rows.size());
  }

  // ===================== 场景 8：分页/排序/过滤 + 软删 =====================

  @Test
  @DisplayName("按金额排序 + 软删除后查询不可见")
  void pageSortFilter_andSoftDelete() {
    var client = new SimulatedFrontendClient("bp_payment");
    client.create(
        Map.of(
            "order_id",
            1L,
            "customer_id",
            1L,
            "amount",
            10.0,
            "channel",
            "WECHAT",
            "status",
            "PENDING",
            "channel_trade_no",
            "WX-S-1"),
        "u");
    client.create(
        Map.of(
            "order_id",
            1L,
            "customer_id",
            1L,
            "amount",
            30.0,
            "channel",
            "ALIPAY",
            "status",
            "PENDING",
            "channel_trade_no",
            "WX-S-2"),
        "u");
    client.create(
        Map.of(
            "order_id",
            1L,
            "customer_id",
            1L,
            "amount",
            20.0,
            "channel",
            "CARD",
            "status",
            "PENDING",
            "channel_trade_no",
            "WX-S-3"),
        "u");

    var page =
        service.page(
            "bp_payment", TENANT_1, 1, 10, RuntimePageQuery.parse("amount", "-amount", null));
    assertEquals(3, page.getRecords().size());
    assertEquals(30.0, ((Number) page.getRecords().get(0).get("amount")).doubleValue(), 0.001);

    // 软删除第一笔后，租户内查询不再返回（deleted=1 过滤）
    String firstId = String.valueOf(page.getRecords().get(0).get("id"));
    client.delete(firstId, "u-admin");
    var after = service.page("bp_payment", TENANT_1, 1, 10);
    assertEquals(2, after.getRecords().size(), "软删除后应只剩 2 条");
    assertThrows(
        RuntimeRecordException.class, () -> service.getById("bp_payment", TENANT_1, firstId));
  }

  // ===================== 场景 9：端到端业务流（订单→支付→已支付） + 更新审计 =====================

  @Test
  @DisplayName("端到端：创建订单→创建支付→支付成功→订单置 PAID，更新审计列生效")
  void businessFlow_orderPaid_withUpdateAudit() {
    var orders = new SimulatedFrontendClient("bp_order");
    SimResponse o =
        orders.create(
            Map.of("customer_id", 1001L, "total_amount", 128.0, "status", "CREATED"), "u-shop");
    String orderId = String.valueOf(o.body().get("id"));
    int orderVer = ((Number) o.body().get("version")).intValue();

    var pays = new SimulatedFrontendClient("bp_payment");
    SimResponse p =
        pays.create(
            Map.of(
                "order_id",
                Long.parseLong(orderId),
                "customer_id",
                1001L,
                "amount",
                128.0,
                "channel",
                "WECHAT",
                "status",
                "PENDING",
                "channel_trade_no",
                "WX-FLOW-1"),
            "u-pay");
    String payId = String.valueOf(p.body().get("id"));
    int payVer = ((Number) p.body().get("version")).intValue();

    // 支付渠道回调成功（模拟 Payment.confirmSuccess）
    SimResponse paid = pays.update(payId, Map.of("status", "SUCCESS"), payVer, "u-pay-callback");
    assertTrue(paid.success(), "支付成功应更新: " + paid);
    assertEquals("SUCCESS", paid.body().get("status"));
    assertEquals("u-pay-callback", paid.body().get("updated_by"), "P0-2 更新者应为回调方");

    // 订单确认支付（模拟 Order.confirmPaid）
    SimResponse confirmed =
        orders.update(orderId, Map.of("status", "PAID"), orderVer, "u-order-svc");
    assertTrue(confirmed.success(), "订单置 PAID 应成功: " + confirmed);
    assertEquals("PAID", confirmed.body().get("status"));
    assertEquals("u-order-svc", confirmed.body().get("updated_by"), "P0-2 更新者应为订单服务");

    // 重复回调：再次用同一流水号创建 → 唯一冲突（幂等去重，不重复入账）
    SimResponse dup =
        pays.create(
            Map.of(
                "order_id",
                Long.parseLong(orderId),
                "customer_id",
                1001L,
                "amount",
                128.0,
                "channel",
                "WECHAT",
                "status",
                "PENDING",
                "channel_trade_no",
                "WX-FLOW-1"),
            "u-pay");
    assertEquals(409, dup.status(), "同渠道流水号重复回调应被唯一约束拦截");
  }

  // ===================== 反例：未知实体 =====================

  @Test
  @DisplayName("前端调用未发布实体 → 404 META_RUNTIME_ENTITY_NOT_FOUND")
  void unknownEntity_returns404() {
    var client = new SimulatedFrontendClient("not_exist");
    SimResponse resp = client.create(Map.of("x", 1), "u");
    assertEquals(404, resp.status());
    assertEquals("META_RUNTIME_ENTITY_NOT_FOUND", resp.errorCode());
  }
}
