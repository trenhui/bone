package com.bone.metadata.runtime.application;

import com.bone.metadata.catalog.application.UiSchemaApplicationService;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.engine.runtime.RuntimeFieldColumn;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 动作执行前的界面协议强制编排（把 {@link UiSchemaRuleEngine} 纯求值器接到真实数据上）。
 *
 * <p><b>编排三件事</b>：① 取协议 schema（发布快照优先，见 {@code UiSchemaApplicationService#schemaForExecution}）；②
 * 把子表（CHILD_TABLE）行读出来挂进 数据，使 {@code items.amount} 这类路径可求值（明细合计规则的真值在子表里）；③ ERROR 级违规抛 422
 * 阻断，WARNING 只记日志。
 *
 * <p><b>为何在「动作」而非「记录 CRUD」上强制</b>：低代码表单的提交语义由动作承载（提交/审批/打款都是动作），状态流转点是数据合规的关键闸口；普通 {@code PUT}
 * 是编辑态，允许草稿数据不完整。若在 CRUD 也强制，建单过程（还没填完明细）会被拦死。
 *
 * <p><b>fail-open 边界</b>：子表读取失败（关系未声明 / 表未发布）时<b>不</b>静默放行含子表路径的规则——那会让「明细合计」规则形同虚设。改为记 WARN
 * 并让该规则按「无数据」求值：sum=0 与申报金额不等 → 阻断，用户看到明确的规则失败而非静默通过。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UiSchemaRuleEnforcer {

  private final UiSchemaRuleEngine engine;
  private final UiSchemaApplicationService uiSchemaApplicationService;
  private final MetaEntityRepository metaEntityRepository;
  private final TenantProvider tenantProvider;
  private final RuntimeRelationApplicationService relationService;
  private final RuntimeEntityCatalog runtimeEntityCatalog;

  /**
   * 强制协议规则，违规即抛 422。
   *
   * @param entityCode 实体 code
   * @param recordId 记录 id（子表读取锚点）
   * @param record 当前记录数据
   * @param operatorId 当前操作者（{@code $me} 与 baseRequired 求值用）
   * @throws RuntimeRecordException 有 ERROR 级违规时（{@code META_RUNTIME_UI_RULES_VIOLATED}）
   */
  public void enforceBeforeAction(
      String entityCode, String recordId, Map<String, Object> record, String operatorId) {
    List<UiSchemaRuleEngine.Violation> violations =
        evaluate(entityCode, recordId, record, operatorId);
    if (violations.isEmpty()) {
      return;
    }
    List<UiSchemaRuleEngine.Violation> errors =
        violations.stream().filter(v -> "ERROR".equals(v.level())).toList();
    violations.stream()
        .filter(v -> "WARNING".equals(v.level()))
        .forEach(
            v ->
                log.warn("[ui-rules] {} {} 规则告警: {}", entityCode, recordId, v.code(), v.message()));
    if (errors.isEmpty()) {
      return;
    }
    String detail =
        errors.stream()
            .map(v -> (v.code() == null ? "" : "[" + v.code() + "] ") + v.message())
            .reduce((a, b) -> a + "；" + b)
            .orElse("");
    log.warn("[ui-rules] 阻断动作 {}/{}：{} 条规则不通过", entityCode, recordId, errors.size());
    throw new RuntimeRecordException(
        MetadataErrorCodes.RUNTIME_UI_RULES_VIOLATED, "界面提交校验未通过：" + detail);
  }

  /** 求值（不抛异常），供预览/测试复用。 */
  public List<UiSchemaRuleEngine.Violation> evaluate(
      String entityCode, String recordId, Map<String, Object> record, String operatorId) {
    long tenantId = tenantProvider.currentTenantId();
    MetaEntity entity = metaEntityRepository.findByTenantAndCode(tenantId, entityCode).orElse(null);
    if (entity == null) {
      // 实体不存在不该由规则强制背锅（动作编排前面已查过 def），跳过即可
      return List.of();
    }
    JsonNode schema = uiSchemaApplicationService.schemaForExecution(entity.getId()).orElse(null);
    if (schema == null) {
      return List.of();
    }
    Map<String, List<Map<String, Object>>> childRows = readChildRows(schema, entityCode, recordId);
    return engine.enforce(
        schema,
        record,
        childRows,
        UiSchemaRuleEngine.Ctx.of(
            operatorId, uiSchemaApplicationService.baseRequiredOf(entity.getId())),
        physicallyBackedFields(tenantId, entityCode));
  }

  /**
   * 更新前<b>字段锁</b>强制（数据完整性，区别于 UI {@code readonly} 渲染提示）。
   *
   * <p><b>为何在「记录 CRUD 更新」路径</b>：冻结 / 对账锁是<b>状态相关的不可变性</b>——只要记录处于某状态，特定字段就不可改，与是否走动作无关。 因此在 {@code
   * RuntimeRecordApplicationService#update} 每次保存前触发，而非仅动作端点（动作端点另有 {@link #enforceBeforeAction}
   * 守聚合断言）。草稿编辑态允许数据不完整，但已设值字段的锁仍生效——锁的是「不可变」，不是「必填」。
   *
   * <p><b>性能</b>：先判 uiSchema 是否声明 {@code
   * fieldLockRules}（通常已被缓存），<b>未声明则直接放行、绝不额外读当前记录</b>——避免对未声明锁的实体造成 每次更新多发一次 SELECT 的回退；仅当确有锁规则，才经
   * {@code currentRecordSupplier} 惰性取当前存储记录。
   *
   * <p>行为：基于<b>当前存储记录</b>（{@code currentRecordSupplier}）计算锁定集，再比对 {@code payload}；若 payload
   * 改写了被锁字段（值不等）即抛 {@code META_RUNTIME_VALIDATION_FAILED}（422），异常携带命中规则的 {@code code}/{@code
   * message} 便于前端精确提示。 payload 未含该字段 → 视为未改，放行。无 uiSchema / 无锁定规则 → 空操作。
   *
   * @param currentRecordSupplier 惰性提供更新前当前记录（用于判定 {@code when} 与比对旧值）；仅在确有锁规则时被调用，可为 null（null
   *     时直接放行）
   * @param payload 本次待写入的载荷（仅比对其中出现的字段）
   */
  public void enforceFieldLock(
      String entityCode,
      String recordId,
      Supplier<Map<String, Object>> currentRecordSupplier,
      Map<String, Object> payload,
      String operatorId) {
    long tenantId = tenantProvider.currentTenantId();
    MetaEntity entity = metaEntityRepository.findByTenantAndCode(tenantId, entityCode).orElse(null);
    if (entity == null) {
      return;
    }
    JsonNode schema = uiSchemaApplicationService.schemaForExecution(entity.getId()).orElse(null);
    if (schema == null) {
      return;
    }
    JsonNode rules = schema.path("fieldLockRules");
    if (!rules.isArray() || rules.isEmpty()) {
      return; // 未声明锁 → 零额外 IO，直接放行
    }
    // 确有锁规则，才惰性取当前记录（避免对无锁实体多发 SELECT）
    Map<String, Object> currentRecord =
        currentRecordSupplier == null ? null : currentRecordSupplier.get();
    if (currentRecord == null) {
      return;
    }
    List<String> locked =
        engine.lockedFields(
            schema,
            currentRecord,
            UiSchemaRuleEngine.Ctx.of(
                operatorId, uiSchemaApplicationService.baseRequiredOf(entity.getId())));
    if (locked.isEmpty()) {
      return;
    }
    List<String> violated = new ArrayList<>();
    for (String field : locked) {
      if (!payload.containsKey(field)) {
        continue;
      }
      if (!valuesEqual(currentRecord.get(field), payload.get(field))) {
        violated.add(field);
      }
    }
    if (!violated.isEmpty()) {
      String ruleTail = resolveRuleMessage(schema, violated);
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "以下字段在当前状态下已锁定、不可修改：" + String.join(", ", violated) + ruleTail);
    }
  }

  /** 从 fieldLockRules 解析命中字段所属规则的 code/message，拼接进异常文案（未命中则返回空串）。 */
  private static String resolveRuleMessage(JsonNode schema, List<String> violated) {
    JsonNode rules = schema.path("fieldLockRules");
    for (JsonNode r : rules) {
      if (ruleCoversAnyField(r, violated)) {
        String code = r.path("code").asText("");
        String msg = r.path("message").asText("");
        return (code.isEmpty() ? "" : "（" + code + "）") + (msg.isEmpty() ? "" : " —— " + msg);
      }
    }
    return "";
  }

  /** 规则 fields 是否覆盖 violated 中的任一字段（避免依赖引擎私有辅助）。 */
  private static boolean ruleCoversAnyField(JsonNode rule, List<String> violated) {
    JsonNode fields = rule.path("fields");
    if (!fields.isArray()) {
      return false;
    }
    for (JsonNode f : fields) {
      if (violated.contains(f.asText())) {
        return true;
      }
    }
    return false;
  }

  /** 值等价判定：数值走容差（防 DECIMAL(20,6) vs double 误判），否则 {@code Objects.equals}。 */
  private static boolean valuesEqual(Object cur, Object next) {
    Double c = toDoubleOrNull(cur);
    Double n = toDoubleOrNull(next);
    if (c != null && n != null) {
      return Math.abs(c - n) <= UiSchemaRuleEngine.MONEY_TOLERANCE.doubleValue();
    }
    return java.util.Objects.equals(cur, next);
  }

  private static Double toDoubleOrNull(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Number n) {
      return n.doubleValue();
    }
    if (o instanceof Boolean) {
      return null;
    }
    try {
      return Double.valueOf(String.valueOf(o));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 「物理表真有列」的字段码集合 = 服务端可求值域。
   *
   * <p><b>为什么必须收窄强制域</b>：{@code ATTACHMENT} / {@code CHILD_TABLE}
   * 字段在主记录上<b>没有列</b>（附件与明细行归关系端点管），主记录里恒为 {@code null}。若对它们强制 linkage {@code
   * required}，规则将<b>永远</b>不通过——理赔样例的「材料缺失请补件」（{@code LINK-EVID-01} 要求 {@code evidences}
   * 非空）会让所有申请都提交不了，即协议自身与存储模型冲突。此处用引擎的 {@code hasColumn} 作权威判据（而非硬编码类型清单）， 避免类型枚举变动时静默错判。
   */
  private Set<String> physicallyBackedFields(long tenantId, String entityCode) {
    return runtimeEntityCatalog
        .findPublishedRuntime(entityCode, tenantId)
        .map(
            e ->
                e.columns().stream()
                    .filter(c -> e.hasColumn(c.code()))
                    .map(RuntimeFieldColumn::code)
                    .collect(Collectors.toSet()))
        .orElse(Set.of());
  }

  /**
   * 读 schema 中声明的全部 CHILD_TABLE 子行，按控件 fieldCode 键入（与协议路径 {@code items.amount} 的首段一致）。
   *
   * <p>同时扫 base 与全部 variant 的视图（变体可能换子表，如核赔端把明细设为只读但仍是同一子表）。
   */
  private Map<String, List<Map<String, Object>>> readChildRows(
      JsonNode schema, String entityCode, String recordId) {
    Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
    Map<String, String> byFieldCode = new LinkedHashMap<>();
    collectChildControls(schema.path("views"), byFieldCode);
    collectChildControls(schema.path("variants"), byFieldCode);
    byFieldCode.forEach(
        (fieldCode, childEntityCode) -> {
          try {
            out.put(fieldCode, relationService.listChildren(entityCode, recordId, childEntityCode));
          } catch (RuntimeException e) {
            // 关系未声明 / 子表未发布：按「无数据」继续，让依赖该路径的规则以 sum=0 / count=0 得出失败结论，
            // 而不是跳过规则造成静默放行。
            log.warn(
                "[ui-rules] 子表读取失败 {}/{} 的 {}（{}）：{}",
                entityCode,
                recordId,
                fieldCode,
                childEntityCode,
                e.getMessage());
            out.put(fieldCode, List.of());
          }
        });
    return out;
  }

  private void collectChildControls(JsonNode node, Map<String, String> sink) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isArray()) {
      node.forEach(n -> collectChildControls(n, sink));
      return;
    }
    if (!node.isObject()) {
      return;
    }
    // controls 数组
    JsonNode controls = node.path("controls");
    if (controls.isArray()) {
      for (JsonNode c : controls) {
        if ("CHILD_TABLE".equals(text(c, "controlKind"))) {
          String child = text(c.path("props"), "childEntity");
          String field = text(c, "fieldCode");
          if (child != null && !child.isBlank() && field != null && !field.isBlank()) {
            sink.putIfAbsent(field, child);
          }
        }
      }
    }
    node.fields().forEachRemaining(e -> collectChildControls(e.getValue(), sink));
  }

  private static String text(JsonNode node, String field) {
    if (node == null || !node.isObject()) {
      return null;
    }
    JsonNode n = node.path(field);
    return (n.isMissingNode() || n.isNull()) ? null : n.asText();
  }
}
