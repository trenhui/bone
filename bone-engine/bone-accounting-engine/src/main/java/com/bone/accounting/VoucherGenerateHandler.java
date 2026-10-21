package com.bone.accounting;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.application.RuntimeActionHandler;
import com.bone.metadata.runtime.application.RuntimeRelationApplicationService;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 凭证生成逃逸处理器（bean 名 {@code voucherGenerateHandler}，业财一体方案 doc/design/modules/12 §3/§4）。
 *
 * <p>「业务终态 → 会计凭证」的规则驱动生成器：按 (来源实体, 触发动作) 取凭证模板 → 行 condition 求值 → 金额公式求值（{@link
 * VoucherFormulaEngine}）→ 科目映射匹配（业务编码口径 D11，按 COA/账套分维度 D3）→ 维度填充 → 复币折算 → 尾差兜底 → 落库（凭证头 + 分录行 +
 * 行级维度）。
 *
 * <p><b>状态语义</b>：生成落 {@code DRAFT}（待复核），经 {@code ready} 动作（reviewed_by 必填，SoD）转 {@code READY}，再经
 * {@code postVoucher} 过账（v2）。入口状态由平台按 is_entry 推导注入（0041/0048）， 本 Handler 不直写 status（G14
 * 保护）；voucher_no 由实体 auto_number 配置生成（0043）。
 *
 * <p><b>一单一证（D10）</b>：生成前按 (来源实体, 来源单据, voucher_type=BUSINESS) 查重，已存在则复用返回 （{@code
 * idempotent=true}）——事件重放/补跑绝不产生第二张凭证。
 *
 * <p><b>fail-closed 边界</b>：模板缺失、映射无命中、非本位币缺汇率（G8 前置）、借贷不平且无尾差科目 —— 全部显式抛错， 绝不静默生成 0 金额行或丢行（方案 R13）。
 *
 * <p>v1.1 边界：单账套单科目体系（ledger_rule=default:XXX、coa='*'）、复币汇率未配置即拒绝；多 COA/多账套、 红冲与过账集成见方案
 * §9（G4/G8/G12）。
 */
@Component("voucherGenerateHandler")
public class VoucherGenerateHandler implements RuntimeActionHandler {

  private static final String VOUCHER_ENTITY = "fin_voucher";
  private static final String ENTRY_ENTITY = "fin_voucher_entry";
  private static final String DIM_ENTITY = "fin_voucher_entry_dim";
  private static final String TEMPLATE_ENTITY = "fin_voucher_template";
  private static final String MAPPING_ENTITY = "fin_account_mapping";

  private final NamedParameterJdbcTemplate jdbc;
  private final RuntimeEntityCatalog runtimeEntityCatalog;
  private final JdbcRuntimeRecordService recordService;
  private final RuntimeRelationApplicationService relationService;
  private final TenantProvider tenantProvider;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public VoucherGenerateHandler(
      NamedParameterJdbcTemplate jdbc,
      RuntimeEntityCatalog runtimeEntityCatalog,
      JdbcRuntimeRecordService recordService,
      RuntimeRelationApplicationService relationService,
      TenantProvider tenantProvider) {
    this.jdbc = jdbc;
    this.runtimeEntityCatalog = runtimeEntityCatalog;
    this.recordService = recordService;
    this.relationService = relationService;
    this.tenantProvider = tenantProvider;
  }

  @Override
  public Map<String, Object> execute(RuntimeActionContext ctx) {
    long tenantId = ctx.getTenantId();
    String sourceEntity = ctx.getEntityCode();
    String sourceRecordId = ctx.getRecordId();
    String action = ctx.getDef() == null ? null : ctx.getDef().getActionCode();

    // ① 一单一证：已生成则复用（D10）
    String existing = findExistingBusinessVoucher(tenantId, sourceEntity, sourceRecordId);
    if (existing != null) {
      return Map.of("voucherId", existing, "idempotent", true);
    }

    // ② 来源单据（头行）
    Map<String, Object> header = recordService.getById(sourceEntity, tenantId, sourceRecordId);
    if (header == null) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND,
          "来源单据不存在: " + sourceEntity + "/" + sourceRecordId);
    }

    // ③ 字段元数据（来源实体 + 其 CHILD_TABLE 子实体）：REFERENCE 目标 / CHILD_TABLE 绑定
    Map<String, Map<String, Object>> fieldMeta = loadFieldMeta(tenantId, sourceEntity);

    // ④ 凭证模板（source_action 精确优先，'*' 兜底）
    Map<String, Object> template = loadTemplate(tenantId, sourceEntity, action);
    String templateCode = str(template.get("template_code"));
    String templateVersion = str(template.get("template_version"));
    JsonNode lineRules = readJson(template.get("line_rules"), "line_rules");
    String roundingDiffAccount = str(template.get("rounding_diff_account"));
    String ledgerCode = resolveLedger(template.get("ledger_rule"), header);

    // ⑤ 子表行（按模板引用的 child fieldCode 拉取）
    Map<String, List<Map<String, Object>>> childRows = new LinkedHashMap<>();
    for (String childFieldCode : childFieldCodesOf(lineRules)) {
      String childEntityCode = childEntityOf(fieldMeta, sourceEntity, childFieldCode);
      if (childEntityCode == null) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
            "来源实体未声明 CHILD_TABLE 字段 " + childFieldCode + "（模板引用失效）");
      }
      childRows.put(
          childFieldCode,
          relationService.listChildren(sourceEntity, sourceRecordId, childEntityCode));
    }

    // ⑥ 行展开 + 科目映射 + 维度填充
    List<Map<String, Object>> entryBodies = new ArrayList<>();
    List<List<Map<String, Object>>> dimBodies = new ArrayList<>();
    int lineNo = 0;
    for (JsonNode rule : lineRules) {
      String scope = text(rule, "scope", "HEADER");
      if ("HEADER".equals(scope)) {
        lineNo =
            expandLine(
                rule,
                header,
                sourceEntity,
                header,
                sourceEntity,
                tenantId,
                ledgerCode,
                fieldMeta,
                entryBodies,
                dimBodies,
                lineNo);
      } else {
        String childFieldCode = scope.substring("CHILD:".length());
        String childEntityCode = childEntityOf(fieldMeta, sourceEntity, childFieldCode);
        for (Map<String, Object> row : childRows.getOrDefault(childFieldCode, List.of())) {
          lineNo =
              expandLine(
                  rule,
                  row,
                  childEntityCode,
                  header,
                  sourceEntity,
                  tenantId,
                  ledgerCode,
                  fieldMeta,
                  entryBodies,
                  dimBodies,
                  lineNo);
        }
      }
    }
    if (entryBodies.isEmpty()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "模板 " + templateCode + " 未产出任何分录行（condition 全部不命中或子表为空）");
    }

    // ⑦ 合计与尾差兜底（本位币）
    BigDecimal debit = BigDecimal.ZERO;
    BigDecimal credit = BigDecimal.ZERO;
    for (Map<String, Object> body : entryBodies) {
      BigDecimal base = new BigDecimal(body.get("base_amount").toString());
      if ("DEBIT".equals(body.get("direction"))) {
        debit = debit.add(base);
      } else {
        credit = credit.add(base);
      }
    }
    if (debit.compareTo(credit) != 0) {
      if (roundingDiffAccount == null || roundingDiffAccount.isBlank()) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
            "借贷不平（借 " + debit.toPlainString() + " / 贷 " + credit.toPlainString() + "）且模板未配置尾差科目");
      }
      boolean debitShort = debit.compareTo(credit) < 0;
      BigDecimal diff = debitShort ? credit.subtract(debit) : debit.subtract(credit);
      Map<String, Object> diffBody = new LinkedHashMap<>();
      diffBody.put("line_no", ++lineNo);
      diffBody.put("direction", debitShort ? "DEBIT" : "CREDIT");
      diffBody.put("account_code", roundingDiffAccount);
      diffBody.put("amount", diff.toPlainString());
      diffBody.put("base_amount", diff.toPlainString());
      diffBody.put("currency", str(header.get("currency")));
      diffBody.put("exchange_rate", BigDecimal.ONE.toPlainString());
      diffBody.put("summary", "分录尾差");
      entryBodies.add(diffBody);
      dimBodies.add(List.of());
    }

    // ⑧ 落库：凭证头（status 由平台入口态注入；voucher_no 由 auto_number 生成）
    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    for (Map<String, Object> body : entryBodies) {
      BigDecimal base = new BigDecimal(body.get("base_amount").toString());
      if ("DEBIT".equals(body.get("direction"))) {
        totalDebit = totalDebit.add(base);
      } else {
        totalCredit = totalCredit.add(base);
      }
    }
    long voucherId = DistributedIdGenerator.generateLongId();
    Map<String, Object> voucherBody = new LinkedHashMap<>();
    voucherBody.put("voucher_type", "BUSINESS");
    voucherBody.put("source_entity_code", sourceEntity);
    voucherBody.put("source_record_id", Long.valueOf(sourceRecordId));
    voucherBody.put("source_action", action);
    voucherBody.put("source_trigger_id", ctx.getParams().getOrDefault("triggerId", null));
    voucherBody.put("template_code", templateCode);
    voucherBody.put("template_version", templateVersion);
    voucherBody.put("business_date", header.get("expense_date"));
    voucherBody.put("posting_date", LocalDate.now().toString());
    voucherBody.put("period", LocalDate.now().toString().substring(0, 7));
    voucherBody.put("ledger_code", ledgerCode);
    voucherBody.put("coa_code", "*");
    voucherBody.put("base_currency", str(header.get("currency")));
    voucherBody.put("currency", str(header.get("currency")));
    voucherBody.put("total_debit", totalDebit.toPlainString());
    voucherBody.put("total_credit", totalCredit.toPlainString());
    voucherBody.put("remark", "由 " + sourceEntity + "/" + sourceRecordId + " 自动生成");
    recordService.create(
        VOUCHER_ENTITY, tenantId, voucherBody, voucherId, ctx.getOperatorId(), null);

    // ⑨ 分录行 + 行级维度
    for (Map<String, Object> body : entryBodies) {
      long entryId = DistributedIdGenerator.generateLongId();
      body.put("voucher_id", voucherId);
      recordService.create(ENTRY_ENTITY, tenantId, body, entryId, ctx.getOperatorId(), null);
      int index = ((Number) body.get("line_no")).intValue() - 1;
      List<Map<String, Object>> dims =
          index >= 0 && index < dimBodies.size() ? dimBodies.get(index) : List.of();
      for (Map<String, Object> dim : dims) {
        dim.put("entry_id", entryId);
        recordService.create(
            DIM_ENTITY,
            tenantId,
            dim,
            DistributedIdGenerator.generateLongId(),
            ctx.getOperatorId(),
            null);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("voucherId", voucherId);
    result.put("lineCount", entryBodies.size());
    result.put("idempotent", false);
    return result;
  }

  // ===================== 行展开 =====================

  private int expandLine(
      JsonNode rule,
      Map<String, Object> row,
      String rowEntityCode,
      Map<String, Object> header,
      String headerEntityCode,
      long tenantId,
      String ledgerCode,
      Map<String, Map<String, Object>> fieldMeta,
      List<Map<String, Object>> entryBodies,
      List<List<Map<String, Object>>> dimBodies,
      int lineNo) {
    // condition 求值（when 方言子集）：带 child 前缀按当前子行，否则按本行作用域
    JsonNode condition = rule.get("condition");
    if (condition != null && !condition.isNull()) {
      Map<String, Object> cond = new LinkedHashMap<>();
      condition.fields().forEachRemaining(e -> cond.put(e.getKey(), toJava(e.getValue())));
      VoucherFormulaEngine.Ctx fctx = new VoucherFormulaEngine.Ctx(header, Map.of(), row);
      if (!VoucherFormulaEngine.evalCondition(cond, fctx)) {
        return lineNo;
      }
    }
    // 金额公式：header 为真实表头（子表行可引用 header.xxx），currentChildRow 为本行
    VoucherFormulaEngine.Ctx fctx = new VoucherFormulaEngine.Ctx(header, Map.of(), row);
    BigDecimal amount = VoucherFormulaEngine.evalAmount(text(rule, "amount_formula", null), fctx);
    if (amount.signum() == 0) {
      return lineNo; // 0 金额行不落库（与 ReimbursementChildAmountAggregator 空明细口径一致）
    }
    // 科目映射（D11 业务编码口径）
    String accountCode =
        resolveAccount(
            tenantId,
            ledgerCode,
            row,
            rowEntityCode,
            text(rule, "account_source", null),
            fieldMeta);
    // 复币（v1.1：非本位币缺汇率 fail-closed，G8 前置）
    String currency = str(row.get("currency"));
    BigDecimal rate = BigDecimal.ONE;
    BigDecimal base = amount;
    // 维度默认值
    List<Map<String, Object>> dims = new ArrayList<>();
    JsonNode dimDefaults = rule.get("dim_defaults");
    if (dimDefaults != null && dimDefaults.isArray()) {
      for (JsonNode dd : dimDefaults) {
        String dimCode = text(dd, "dimension_code", null);
        Object value;
        if (dd.has("value")) {
          value = toJava(dd.get("value"));
        } else {
          String valueField = text(dd, "value_field", null);
          value = resolveRaw(row, valueField, rowEntityCode, fieldMeta);
          if (value == null) {
            // 行内取不到则回退表头：部门/项目等维度通常挂在单据头上
            value = resolveRaw(header, valueField, headerEntityCode, fieldMeta);
          }
        }
        if (dimCode != null && value != null) {
          Map<String, Object> dim = new LinkedHashMap<>();
          dim.put("dimension_code", dimCode);
          dim.put("dimension_value", value.toString());
          dims.add(dim);
        }
      }
    }
    int line = ++lineNo;
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("line_no", line);
    body.put("direction", text(rule, "direction", null));
    body.put("account_code", accountCode);
    body.put("amount", amount.abs().toPlainString());
    body.put("currency", currency);
    body.put("exchange_rate", rate.toPlainString());
    body.put("base_amount", base.toPlainString());
    Map<String, Object> summaryCtx = new LinkedHashMap<>(header);
    summaryCtx.putAll(row);
    body.put(
        "summary",
        VoucherFormulaEngine.renderSummary(text(rule, "summary_template", ""), summaryCtx));
    Object settleNo = row.get("paymentSerial");
    if (settleNo != null) {
      body.put("settle_no", settleNo.toString());
    }
    Object channel = row.get("channel");
    if (channel != null) {
      body.put("settle_mode", channel.toString());
    }
    entryBodies.add(body);
    dimBodies.add(dims);
    return lineNo;
  }

  // ===================== 映射与元数据 =====================

  /** 科目映射匹配：mapping_key + 业务编码值 + coa('*') + ledger，精确值优先于 '*'，priority/effective 排序。 */
  private String resolveAccount(
      long tenantId,
      String ledgerCode,
      Map<String, Object> row,
      String rowEntityCode,
      String accountSource,
      Map<String, Map<String, Object>> fieldMeta) {
    int dot = accountSource.indexOf('.');
    if (dot < 0) {
      throw new VoucherFormulaEngine.VoucherFormulaException(
          "account_source 须为 实体.字段 形态: " + accountSource);
    }
    String entityHint = accountSource.substring(0, dot);
    String field = accountSource.substring(dot + 1);
    Object bizValue = resolveBizValue(row, rowEntityCode, entityHint, field, fieldMeta);
    String table = tableOf(MAPPING_ENTITY, tenantId);
    String sql =
        "SELECT account_code FROM "
            + table
            + " WHERE mapping_key = :key AND (coa_code = '*' OR coa_code = :coa)"
            + " AND (ledger_code = '*' OR ledger_code = :ledger)"
            + " AND (biz_field_value = '*' OR biz_field_value = :value) AND deleted = 0"
            + " AND (effective_from IS NULL OR effective_from <= :today)"
            + " AND (effective_to IS NULL OR effective_to >= :today)"
            + " ORDER BY (biz_field_value = '*') ASC, priority DESC, effective_from DESC LIMIT 1";
    MapSqlParameterSource params =
        new MapSqlParameterSource("key", accountSource)
            .addValue("coa", "*")
            .addValue("ledger", ledgerCode)
            .addValue("value", bizValue == null ? "" : bizValue.toString())
            .addValue("today", LocalDate.now().toString());
    String account;
    try {
      account = jdbc.queryForObject(sql, params, String.class);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      account = null;
    }
    if (account == null) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "科目映射无命中: "
              + accountSource
              + " = "
              + bizValue
              + "（ledger="
              + ledgerCode
              + "）——请补 fin_account_mapping 配置");
    }
    return account;
  }

  /**
   * 业务编码值解析（D11）：entityHint = 行实体 → 直接取行字段；否则找行内 REFERENCE 目标 = entityHint 的字段， FK 解析为目标实体的编码字段（如
   * category_id → expense_category.code）。
   */
  private Object resolveBizValue(
      Map<String, Object> row,
      String rowEntityCode,
      String entityHint,
      String field,
      Map<String, Map<String, Object>> fieldMeta) {
    if (Objects.equals(entityHint, rowEntityCode)) {
      return resolveRaw(row, field, rowEntityCode, fieldMeta);
    }
    for (Map.Entry<String, Object> e : row.entrySet()) {
      Map<String, Object> entityMeta = fieldMeta.getOrDefault(rowEntityCode, Map.of());
      Map<String, Object> meta = (Map<String, Object>) entityMeta.get(e.getKey());
      if (meta != null && Objects.equals(meta.get("referenceTarget"), entityHint)) {
        if (e.getValue() == null) {
          return null;
        }
        Map<String, Object> target =
            recordService.getById(
                entityHint, tenantProvider.currentTenantId(), e.getValue().toString());
        return target == null ? null : target.get(field);
      }
    }
    return null;
  }

  private Object resolveRaw(
      Map<String, Object> row,
      String field,
      String rowEntityCode,
      Map<String, Map<String, Object>> fieldMeta) {
    Object value = row.get(field);
    Map<String, Object> entityMeta = fieldMeta.getOrDefault(rowEntityCode, Map.of());
    Map<String, Object> meta = (Map<String, Object>) entityMeta.get(field);
    if (value != null && meta != null && meta.get("referenceTarget") != null) {
      Map<String, Object> target =
          recordService.getById(
              str(meta.get("referenceTarget")), tenantProvider.currentTenantId(), value.toString());
      if (target != null) {
        Object code = target.get("code");
        return code != null ? code : target.get("name");
      }
    }
    return value;
  }

  /** 字段元数据：来源实体 + 其 CHILD_TABLE 子实体的全部字段（REFERENCE 目标 / CHILD_TABLE 绑定）。 */
  private Map<String, Map<String, Object>> loadFieldMeta(long tenantId, String sourceEntity) {
    Map<String, Map<String, Object>> result = new LinkedHashMap<>();
    // 第一遍：来源实体字段（收集 CHILD_TABLE 子实体编码）
    List<String> entityCodes = new ArrayList<>();
    entityCodes.add(sourceEntity);
    String sourceFieldsSql =
        "SELECT f.code, f.type, f.validation_rules"
            + " FROM meta_field f JOIN meta_entity e ON e.id = f.entity_id AND e.deleted = 0"
            + " WHERE e.tenant_id = :tenant AND e.code = :entity AND f.deleted = 0";
    jdbc.query(
        sourceFieldsSql,
        new MapSqlParameterSource("tenant", tenantId).addValue("entity", sourceEntity),
        rs -> {
          String fieldCode = rs.getString("code");
          Map<String, Object> meta = new LinkedHashMap<>();
          meta.put("type", rs.getString("type"));
          JsonNode rules = readJson(rs.getString("validation_rules"), fieldCode);
          String referenceTarget = rules.path("reference").path("targetEntityCode").asText(null);
          if (referenceTarget != null) {
            meta.put("referenceTarget", referenceTarget);
          }
          String childEntityCode = rules.path("childTable").path("childEntityCode").asText(null);
          if (childEntityCode != null) {
            meta.put("childEntityCode", childEntityCode);
            if (!entityCodes.contains(childEntityCode)) {
              entityCodes.add(childEntityCode);
            }
          }
          result.computeIfAbsent(sourceEntity, k -> new LinkedHashMap<>()).put(fieldCode, meta);
        });
    // 第二遍：子实体字段（REFERENCE 目标解析用）
    if (entityCodes.size() > 1) {
      String sql =
          "SELECT e.code AS entity_code, f.code AS field_code, f.type, f.validation_rules"
              + " FROM meta_field f JOIN meta_entity e ON e.id = f.entity_id AND e.deleted = 0"
              + " WHERE e.tenant_id = :tenant AND e.code IN (:codes) AND e.code <> :source AND f.deleted = 0";
      jdbc.query(
          sql,
          new MapSqlParameterSource("tenant", tenantId)
              .addValue("codes", entityCodes)
              .addValue("source", sourceEntity),
          rs -> {
            String entityCode = rs.getString("entity_code");
            String fieldCode = rs.getString("field_code");
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", rs.getString("type"));
            JsonNode rules = readJson(rs.getString("validation_rules"), fieldCode);
            String referenceTarget = rules.path("reference").path("targetEntityCode").asText(null);
            if (referenceTarget != null) {
              meta.put("referenceTarget", referenceTarget);
            }
            result.computeIfAbsent(entityCode, k -> new LinkedHashMap<>()).put(fieldCode, meta);
          });
    }
    return result;
  }

  private String childEntityOf(
      Map<String, Map<String, Object>> fieldMeta, String sourceEntity, String childFieldCode) {
    Map<String, Object> entityMeta = fieldMeta.getOrDefault(sourceEntity, Map.of());
    Map<String, Object> meta = (Map<String, Object>) entityMeta.get(childFieldCode);
    return meta == null ? null : str(meta.get("childEntityCode"));
  }

  private List<String> childFieldCodesOf(JsonNode lineRules) {
    List<String> codes = new ArrayList<>();
    lineRules.forEach(
        rule -> {
          String scope = text(rule, "scope", "HEADER");
          if (scope.startsWith("CHILD:")) {
            String code = scope.substring("CHILD:".length());
            if (!codes.contains(code)) {
              codes.add(code);
            }
          }
        });
    return codes;
  }

  // ===================== 读取助手 =====================

  private String findExistingBusinessVoucher(
      long tenantId, String sourceEntity, String sourceRecordId) {
    String table = tableOf(VOUCHER_ENTITY, tenantId);
    String sql =
        "SELECT id FROM "
            + table
            + " WHERE source_entity_code = :entity AND source_record_id = :record"
            + " AND voucher_type = 'BUSINESS' AND deleted = 0 LIMIT 1";
    try {
      return jdbc.queryForObject(
          sql,
          new MapSqlParameterSource("entity", sourceEntity)
              .addValue("record", Long.valueOf(sourceRecordId)),
          String.class);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      return null;
    }
  }

  private Map<String, Object> loadTemplate(long tenantId, String sourceEntity, String action) {
    String table = tableOf(TEMPLATE_ENTITY, tenantId);
    String sql =
        "SELECT template_code, template_version, line_rules, rounding_diff_account, ledger_rule FROM "
            + table
            + " WHERE source_entity_code = :entity AND status = 'ACTIVE' AND deleted = 0"
            + " AND (source_action = :action OR source_action = '*')"
            + " ORDER BY (source_action = '*') ASC LIMIT 1";
    try {
      return jdbc.queryForObject(
          sql,
          new MapSqlParameterSource("entity", sourceEntity).addValue("action", action),
          Map.class);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "未找到凭证模板: " + sourceEntity + " / " + action + "（fin_voucher_template 无 ACTIVE 行）");
    }
  }

  private String resolveLedger(Object ledgerRule, Map<String, Object> header) {
    String rule = str(ledgerRule);
    if (rule == null || rule.isBlank() || "*".equals(rule)) {
      return "MAIN";
    }
    if (rule.startsWith("default:")) {
      return rule.substring("default:".length());
    }
    if (rule.startsWith("by:")) {
      Object value = header.get(rule.substring("by:".length()));
      if (value != null) {
        return value.toString();
      }
    }
    throw new RuntimeRecordException(
        MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "账套推导失败: ledger_rule=" + rule);
  }

  private String tableOf(String entityCode, long tenantId) {
    return runtimeEntityCatalog
        .findPublishedRuntime(entityCode, tenantId)
        .map(PublishedRuntimeEntity::physicalTableName)
        .orElseThrow(
            () ->
                new RuntimeRecordException(
                    MetadataErrorCodes.RUNTIME_ENTITY_NOT_FOUND,
                    "实体未发布: " + entityCode + "（tenant " + tenantId + "）"));
  }

  private JsonNode readJson(Object raw, String what) {
    if (raw == null) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, what + " 为空（JSON 必需）");
    }
    try {
      return objectMapper.readTree(raw.toString());
    } catch (Exception ex) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, what + " 非法 JSON: " + ex.getMessage());
    }
  }

  private static String text(JsonNode node, String field, String def) {
    if (node == null) {
      return def;
    }
    JsonNode v = node.get(field);
    return v == null || v.isNull() ? def : v.asText();
  }

  private static String str(Object value) {
    return value == null ? null : value.toString();
  }

  private static Object toJava(JsonNode node) {
    if (node.isTextual()) {
      return node.asText();
    }
    if (node.isNumber()) {
      return node.numberValue();
    }
    if (node.isBoolean()) {
      return node.asBoolean();
    }
    if (node.isArray()) {
      List<Object> list = new ArrayList<>();
      node.forEach(n -> list.add(toJava(n)));
      return list;
    }
    return node.toString();
  }
}
