package com.bone.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.application.RuntimeRelationApplicationService;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * {@link VoucherGenerateHandler} 黄金样例（业财一体方案 §11）：报销打款 → 凭证草案全链路。
 *
 * <p>行规则（对齐 doc 12 §4.5 报销实例）：DEBIT×N（按明细行，科目走 expense_category.code 映射）+ DEBIT 进项税（condition=专票）+
 * CREDIT（header.amount，科目走 expense_reimbursement.channel 映射）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherGenerateHandlerTest {

  private static final long TENANT = 1001L;

  @Mock private NamedParameterJdbcTemplate jdbc;
  @Mock private RuntimeEntityCatalog runtimeEntityCatalog;
  @Mock private JdbcRuntimeRecordService recordService;
  @Mock private RuntimeRelationApplicationService relationService;
  @Mock private TenantProvider tenantProvider;

  private VoucherGenerateHandler handler;

  /** 捕获 (entityCode, body) 的创建流水。 */
  private final List<Map<String, Object>> createdVouchers = new ArrayList<>();

  private final List<Map<String, Object>> createdEntries = new ArrayList<>();
  private final List<Map<String, Object>> createdDims = new ArrayList<>();

  private static final String ITEMS_CHILD_RULE =
      "{\"childTable\":{\"childEntityCode\":\"expense_reimbursement_item\",\"foreignKey\":\"claim_id\"}}";
  private static final String CATEGORY_REF_RULE =
      "{\"reference\":{\"targetEntityCode\":\"expense_category\",\"labelField\":\"name\"}}";

  private static final String LINE_RULES =
      "[{\"direction\":\"DEBIT\",\"scope\":\"CHILD:items\",\"amount_formula\":\"items.amount\","
          + "\"account_source\":\"expense_category.code\","
          + "\"summary_template\":\"{applicant} {period} 报销\","
          + "\"dim_defaults\":[{\"dimension_code\":\"DEPT\",\"value_field\":\"department\"}]},"
          + "{\"direction\":\"DEBIT\",\"scope\":\"CHILD:items\","
          + "\"condition\":{\"child\":\"items\",\"field\":\"invoice_type\",\"op\":\"eq\",\"value\":\"SPECIAL\"},"
          + "\"amount_formula\":\"items.amount * 0.13\","
          + "\"account_source\":\"expense_reimbursement_item.invoice_type\","
          + "\"summary_template\":\"进项税\"},"
          + "{\"direction\":\"CREDIT\",\"scope\":\"HEADER\",\"amount_formula\":\"header.amount\","
          + "\"account_source\":\"expense_reimbursement.channel\","
          + "\"summary_template\":\"报销打款\"}]";

  @BeforeEach
  void setUp() {
    handler =
        new VoucherGenerateHandler(
            jdbc, runtimeEntityCatalog, recordService, relationService, tenantProvider);
    when(tenantProvider.currentTenantId()).thenReturn(TENANT);
    stubCatalog("fin_voucher", "fin_voucher");
    stubCatalog("fin_voucher_template", "fin_voucher_template");
    stubCatalog("fin_account_mapping", "fin_account_mapping");
    // 创建流水捕获
    doAnswer(
            inv -> {
              String entity = inv.getArgument(0);
              Map<String, Object> body = inv.getArgument(2);
              if ("fin_voucher".equals(entity)) {
                createdVouchers.add(body);
              } else if ("fin_voucher_entry".equals(entity)) {
                createdEntries.add(body);
              } else if ("fin_voucher_entry_dim".equals(entity)) {
                createdDims.add(body);
              }
              return Map.of();
            })
        .when(recordService)
        .create(anyString(), anyLong(), anyMap(), anyLong(), any(), isNull());
  }

  private void stubCatalog(String entityCode, String table) {
    when(runtimeEntityCatalog.findPublishedRuntime(entityCode, TENANT))
        .thenReturn(
            java.util.Optional.of(
                new PublishedRuntimeEntity(entityCode, table, "id", TENANT, List.of())));
  }

  private void stubMetaFieldQueries() {
    // 第一遍：来源实体字段（items → CHILD_TABLE 绑定）
    ResultSet rs1 = mock(ResultSet.class);
    try {
      when(rs1.getString("code")).thenReturn("items");
      when(rs1.getString("type")).thenReturn("CHILD_TABLE");
      when(rs1.getString("validation_rules")).thenReturn(ITEMS_CHILD_RULE);
      // 第二遍：子实体字段（category_id → REFERENCE 绑定）
      ResultSet rs2 = mock(ResultSet.class);
      when(rs2.getString("entity_code")).thenReturn("expense_reimbursement_item");
      when(rs2.getString("field_code")).thenReturn("category_id");
      when(rs2.getString("type")).thenReturn("REFERENCE");
      when(rs2.getString("validation_rules")).thenReturn(CATEGORY_REF_RULE);
      doAnswer(
              inv -> {
                String sql = inv.getArgument(0);
                RowCallbackHandler cb = inv.getArgument(2);
                cb.processRow(sql.contains("IN (:codes)") ? rs2 : rs1);
                return null;
              })
          .when(jdbc)
          .query(anyString(), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private void stubNoExistingVoucher() {
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenThrow(new EmptyResultDataAccessException(1));
  }

  private void stubTemplate() {
    Map<String, Object> template = new LinkedHashMap<>();
    template.put("template_code", "TPL_EXPENSE_REIMBURSEMENT_PAYOUT");
    template.put("template_version", "1.0.0");
    template.put("line_rules", LINE_RULES);
    template.put("rounding_diff_account", null);
    template.put("ledger_rule", "default:MAIN");
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Map.class)))
        .thenReturn(template);
  }

  /** 科目映射：按 value 参数分派（TRAVEL/MEAL/SPECIAL/PAYROLL）。 */
  private void stubMappings() {
    when(jdbc.queryForObject(
            argThat((String sql) -> sql != null && sql.contains("fin_account_mapping")),
            any(MapSqlParameterSource.class),
            eq(String.class)))
        .thenAnswer(
            inv -> {
              MapSqlParameterSource params = inv.getArgument(1);
              String value = String.valueOf(params.getValue("value"));
              String key = String.valueOf(params.getValue("key"));
              if (key.startsWith("expense_category.code")) {
                return "TRAVEL".equals(value) ? "6602.01" : "6602.02";
              }
              if (key.startsWith("expense_reimbursement_item.invoice_type")) {
                return "2221.01.02";
              }
              return "2211.01";
            });
  }

  private Map<String, Object> header() {
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("amount", new BigDecimal("163.50"));
    header.put("currency", "CNY");
    header.put("expense_date", "2026-10-15");
    header.put("applicant", "张三");
    header.put("department", "D100");
    header.put("period", "2026-10");
    header.put("paymentSerial", "PAY-123");
    header.put("channel", "PAYROLL");
    return header;
  }

  private void stubSourceRecords() {
    when(recordService.getById("expense_reimbursement", TENANT, "9")).thenReturn(header());
    when(recordService.getById("expense_category", TENANT, "11"))
        .thenReturn(Map.of("code", "TRAVEL", "name", "差旅"));
    when(recordService.getById("expense_category", TENANT, "12"))
        .thenReturn(Map.of("code", "MEAL", "name", "餐饮"));
    when(relationService.listChildren("expense_reimbursement", "9", "expense_reimbursement_item"))
        .thenReturn(List.of(item("11", "100", "SPECIAL"), item("12", "50.50", "NONE")));
  }

  private static Map<String, Object> item(String categoryId, String amount, String invoiceType) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("category_id", categoryId);
    row.put("amount", new BigDecimal(amount));
    row.put("invoice_type", invoiceType);
    row.put("expense_date", "2026-10-14");
    return row;
  }

  private static RuntimeActionContext ctx() {
    return new RuntimeActionContext(
        "expense_reimbursement", TENANT, "9", null, Map.of("channel", "PAYROLL"), "u1");
  }

  @Test
  @DisplayName("黄金路径：报销打款 → 4 行凭证（2 费用 + 1 进项税 + 1 贷方），借贷平衡，维度落库")
  void goldenPath_generatesBalancedVoucher() {
    stubNoExistingVoucher();
    stubTemplate();
    stubMetaFieldQueries();
    stubMappings();
    stubSourceRecords();

    Map<String, Object> result = handler.execute(ctx());

    assertThat(result).containsEntry("idempotent", false);
    assertThat(result.get("lineCount")).isEqualTo(4);
    // 凭证头：模板快照 + 账套 + 本位币 + 平衡合计
    assertThat(createdVouchers).hasSize(1);
    Map<String, Object> voucher = createdVouchers.get(0);
    assertThat(voucher)
        .containsEntry("voucher_type", "BUSINESS")
        .containsEntry("template_code", "TPL_EXPENSE_REIMBURSEMENT_PAYOUT")
        .containsEntry("template_version", "1.0.0")
        .containsEntry("ledger_code", "MAIN")
        .containsEntry("coa_code", "*")
        .containsEntry("base_currency", "CNY")
        .containsEntry("total_debit", "163.50")
        .containsEntry("total_credit", "163.50");
    // 分录行：科目来自编码口径映射（TRAVEL/MEAL/SPECIAL/PAYROLL）
    assertThat(createdEntries).hasSize(4);
    assertThat(createdEntries)
        .extracting(e -> e.get("account_code").toString())
        .containsExactly("6602.01", "6602.02", "2221.01.02", "2211.01");
    assertThat(createdEntries)
        .extracting(e -> e.get("direction").toString())
        .containsExactly("DEBIT", "DEBIT", "DEBIT", "CREDIT");
    // 结算号来自打款流水；摘要渲染
    assertThat(createdEntries.get(3)).containsEntry("settle_no", "PAY-123");
    assertThat(createdEntries.get(0).get("summary").toString()).contains("张三");
    // 维度：仅费用行进 DEPT=D100（进项税/贷方无 dim_defaults）
    assertThat(createdDims).hasSize(2);
    assertThat(createdDims).allSatisfy(d -> assertThat(d).containsEntry("dimension_value", "D100"));
  }

  @Test
  @DisplayName("一单一证：已生成则复用返回，不再创建（D10）")
  void existingVoucher_reused() {
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn("777");

    Map<String, Object> result = handler.execute(ctx());

    assertThat(result).containsEntry("idempotent", true).containsEntry("voucherId", "777");
    assertThat(createdVouchers).isEmpty();
    assertThat(createdEntries).isEmpty();
  }

  @Test
  @DisplayName("科目映射无命中 → 422 fail-closed（不静默丢行）")
  void mappingMiss_failsClosed() {
    stubNoExistingVoucher();
    stubTemplate();
    stubMetaFieldQueries();
    stubSourceRecords();
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenThrow(new EmptyResultDataAccessException(1));

    assertThatThrownBy(() -> handler.execute(ctx()))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED))
        .hasMessageContaining("无命中");
    assertThat(createdVouchers).isEmpty();
  }

  @Test
  @DisplayName("模板缺失 → 422 fail-closed")
  void templateMiss_failsClosed() {
    stubNoExistingVoucher();
    stubMetaFieldQueries();
    stubSourceRecords();
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Map.class)))
        .thenThrow(new EmptyResultDataAccessException(1));

    assertThatThrownBy(() -> handler.execute(ctx()))
        .isInstanceOf(RuntimeRecordException.class)
        .hasMessageContaining("未找到凭证模板");
  }

  @Test
  @DisplayName("借贷不平且无尾差科目 → 422（不平绝不落库）")
  void unbalanced_failsClosed() {
    stubNoExistingVoucher();
    // 贷方公式引用一个不存在的头字段 → 行展开失败先行暴露；此处改为贷方金额公式少一行：
    // 直接用单边模板（仅 DEBIT 行）构造不平
    Map<String, Object> template = new LinkedHashMap<>();
    template.put("template_code", "TPL_UNBALANCED");
    template.put("template_version", "1.0.0");
    template.put(
        "line_rules",
        "[{\"direction\":\"DEBIT\",\"scope\":\"CHILD:items\",\"amount_formula\":\"items.amount\","
            + "\"account_source\":\"expense_category.code\"}]");
    template.put("rounding_diff_account", null);
    template.put("ledger_rule", "default:MAIN");
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Map.class)))
        .thenReturn(template);
    stubMetaFieldQueries();
    stubMappings();
    stubSourceRecords();

    assertThatThrownBy(() -> handler.execute(ctx()))
        .isInstanceOf(RuntimeRecordException.class)
        .hasMessageContaining("借贷不平");
    assertThat(createdVouchers).isEmpty();
  }
}
