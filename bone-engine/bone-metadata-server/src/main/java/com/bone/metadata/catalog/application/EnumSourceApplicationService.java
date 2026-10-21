package com.bone.metadata.catalog.application;

import com.bone.metadata.catalog.application.query.dto.EntityValidationIssue;
import com.bone.metadata.catalog.common.CatalogErrorCodes;
import com.bone.metadata.catalog.common.CatalogErrors;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.meta.OptionItem;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.service.EnumSourceResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 枚举选项源应用服务（doc/design/modules/11《枚举选项源统一设计方案》§4.3/§6.3/§9）： 声明解析与校验、真源物化、发布期闸门问题项、手动刷新与差异对比。
 *
 * <p><b>核心机制（snapshot 模式）</b>：{@code meta_field.enum_source} 声明真源（dict 类型，P0 范围）， 物化动作把真源选项覆写进
 * {@code meta_field.enum_values}——发布态运行时读 enum_values， {@code RuntimeWriteValidator} 写校验与前端 {@code
 * parseEnumValues} 渲染<b>消费口径零变更</b>。
 *
 * <p><b>三态口径</b>（界面设计器字典打通 v1.1 §4.5）：物化写入 ACTIVE + DEPRECATED（DEPRECATED 项带 {@code state}
 * 标记），DISABLED 不进快照——写校验按快照口径即「DEPRECATED 放行、DISABLED 拒绝」，渲染侧前端按 ACTIVE 过滤即「新表单不出现弃用项」。
 * 同一快照两种口径，无需字典服务在线。
 *
 * <p><b>fail-closed 边界</b>：写路径（保存物化 / 发布物化 / 手动刷新）解析失败抛 {@link
 * CatalogErrorCodes#ENUM_SOURCE_RESOLVE_FAILED}（502），绝不静默用旧快照——运营以为改了字典生效、
 * 实际跑的是上个月的码表，是比报错严重得多的正确性债务。读路径（发布预览 / 差异对比）走 {@link EntityValidationIssue}（ENUM_SOURCE_UNRESOLVED
 * ERROR / ENUM_SOURCE_DRIFT WARNING），不抛异常。
 *
 * <p><b>分层纪律</b>：只依赖领域端口 {@link EnumSourceResolver}（HC-001：domain 零框架依赖）； 跨服务 HTTP 细节全部在 {@code
 * infrastructure.enumsource}，本类不感知 bone-system 的存在。
 */
@Slf4j
@Service
public class EnumSourceApplicationService {

  private static final String TYPE_INLINE = "inline";
  private static final String TYPE_DICT = "dict";
  private static final String MODE_SNAPSHOT = "snapshot";
  private static final String MODE_LIVE = "live";

  private final MetaFieldRepository metaFieldRepository;
  private final MetaEntityRepository metaEntityRepository;
  private final EnumSourceResolver enumSourceResolver;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public EnumSourceApplicationService(
      MetaFieldRepository metaFieldRepository,
      MetaEntityRepository metaEntityRepository,
      EnumSourceResolver enumSourceResolver) {
    this.metaFieldRepository = metaFieldRepository;
    this.metaEntityRepository = metaEntityRepository;
    this.enumSourceResolver = enumSourceResolver;
  }

  // ===================== 声明解析与校验（写入口守卫） =====================

  /**
   * 解析并校验 {@code enum_source} 声明；非法声明抛 {@link CatalogErrorCodes#ENUM_SOURCE_INVALID}（400）。
   *
   * <p>与 {@code rowPolicies} 同姿态（MetaEntityApplicationService 既有先例）：JSON 校验放应用层， 在保存时挡住，不等发布物化时才炸。
   *
   * @return 空 = inline（缺省，兼容存量，行为逐字不变）
   */
  public Optional<Declaration> parseDeclaration(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    final JsonNode root;
    try {
      root = objectMapper.readTree(raw);
    } catch (Exception e) {
      throw CatalogErrors.of(
          CatalogErrorCodes.ENUM_SOURCE_INVALID, "enum_source 不是合法 JSON: " + e.getMessage());
    }
    if (root == null || !root.isObject()) {
      throw CatalogErrors.of(CatalogErrorCodes.ENUM_SOURCE_INVALID, "enum_source 必须是 JSON 对象");
    }
    String type = textOf(root.path("type"), TYPE_INLINE);
    if (!TYPE_INLINE.equals(type) && !TYPE_DICT.equals(type)) {
      throw CatalogErrors.of(
          CatalogErrorCodes.ENUM_SOURCE_INVALID,
          "enum_source.type 越界（P0 仅支持 inline/dict，reference/external 见分期）: " + type);
    }
    String mode = textOf(root.path("mode"), MODE_SNAPSHOT);
    if (!MODE_SNAPSHOT.equals(mode) && !MODE_LIVE.equals(mode)) {
      throw CatalogErrors.of(
          CatalogErrorCodes.ENUM_SOURCE_INVALID, "enum_source.mode 越界（应为 snapshot/live）: " + mode);
    }
    JsonNode ref = root.path("ref");
    if (TYPE_DICT.equals(type)) {
      String typeCode = ref.isObject() ? textOf(ref.path("typeCode"), null) : null;
      if (typeCode == null || typeCode.isBlank()) {
        throw CatalogErrors.of(
            CatalogErrorCodes.ENUM_SOURCE_INVALID, "dict 选项源缺 ref.typeCode（真源定位不能为空）");
      }
      return Optional.of(new Declaration(type, mode, typeCode, root));
    }
    return Optional.empty();
  }

  /** 字段是否绑定了非 inline 真源（决定是否走物化链路）。 */
  public boolean isBound(MetaField field) {
    try {
      return parseDeclaration(field.getEnumSource()).isPresent();
    } catch (RuntimeException e) {
      // 非法声明在写入口已被拦；这里（读路径）不重复抛，按「未绑定」跳过，
      // 由发布预览的 ENUM_SOURCE_* 问题项与保存入口各自表达。
      return false;
    }
  }

  // ===================== 物化（写路径，fail-closed） =====================

  /**
   * 字段保存后物化（doc 11 §4.3「字段保存（enum_source 变更时）」）。
   *
   * <p>绑定 non-inline 真源时立即解析并覆写 enum_values，让建模者在字段向导里立刻看到真源选项； 解析失败抛 {@link
   * CatalogErrorCodes#ENUM_SOURCE_RESOLVE_FAILED}，保存整体回滚—— 不接受「绑定成功但选项为空」的半吊子状态。
   */
  @Transactional
  public void materializeOnSave(MetaField field) {
    Optional<Declaration> decl = parseDeclaration(field.getEnumSource());
    if (decl.isEmpty()) {
      return;
    }
    List<OptionItem> options = resolveOrThrow(decl.get(), field);
    applyMaterialization(field, decl.get(), options, "save");
    metaFieldRepository.update(field);
  }

  /**
   * 实体发布时物化（doc 11 §4.3「实体发布（闸门之后、落库之前）」）。
   *
   * <p>发布即冻结：把此刻的真源状态「编译」进 enum_values，与 uiSchema 发布快照同哲学。 任一绑定字段解析失败即抛 {@link
   * CatalogErrorCodes#ENUM_SOURCE_RESOLVE_FAILED}，发布整体回滚。
   */
  @Transactional
  public void materializeForPublish(MetaEntity entity) {
    List<MetaField> fields = metaFieldRepository.findByEntityId(entity.getId());
    for (MetaField field : fields) {
      Optional<Declaration> decl = parseDeclaration(field.getEnumSource());
      if (decl.isEmpty()) {
        continue;
      }
      List<OptionItem> options = resolveOrThrow(decl.get(), field);
      applyMaterialization(field, decl.get(), options, "publish");
      metaFieldRepository.update(field);
    }
  }

  /**
   * 手动刷新快照（doc 11 §9：{@code POST /fields/{fieldId}/options/refresh}）。
   *
   * <p>与发布物化的差别仅在失败姿态：刷新失败<b>保留旧快照</b>并抛错（doc 11 §4.3）—— 刷新是运维动作，不能因为字典服务抖动把字段的既有选项清掉。
   */
  @Transactional
  public MaterializeResult refresh(Long fieldId) {
    MetaField field = requireField(fieldId);
    Optional<Declaration> decl = parseDeclaration(field.getEnumSource());
    if (decl.isEmpty()) {
      throw CatalogErrors.of(
          CatalogErrorCodes.ENUM_SOURCE_INVALID,
          "字段「" + field.getDisplayName() + "」未绑定选项源（inline 无需刷新）");
    }
    List<OptionItem> options = resolveOrThrow(decl.get(), field);
    int before = countSnapshot(field);
    applyMaterialization(field, decl.get(), options, "refresh");
    metaFieldRepository.update(field);
    return new MaterializeResult(field.getId(), before, options.size());
  }

  // ===================== 发布预览问题项（读路径，fail-soft） =====================

  /**
   * 汇总字段级选项源问题（doc 11 §6.3），供 {@code collectIssues} 调用：
   *
   * <ul>
   *   <li>{@code ENUM_SOURCE_UNRESOLVED}（ERROR）：dict 真源不可达——发布将被 materializeForPublish 拒绝
   *   <li>{@code ENUM_SOURCE_DRIFT}（WARNING）：物化快照与真源有差异（added/removed/labelChanged）
   * </ul>
   *
   * <p>与物理漂移同构：ERROR 项从发布闸门的通用过滤中<b>排除</b>（见 publishEntity 注释）， 由 materializeForPublish 抛更精确的
   * {@code META_ENUM_SOURCE_RESOLVE_FAILED}（502）—— 预览说「不可发布」与服务端抛「为什么不可发布」各司其职，不互相覆盖。
   */
  public void collectFieldIssues(MetaField field, List<EntityValidationIssue> issues) {
    Optional<Declaration> decl;
    try {
      decl = parseDeclaration(field.getEnumSource());
    } catch (RuntimeException e) {
      issues.add(
          EntityValidationIssue.fieldError(
              "ENUM_SOURCE_INVALID",
              "字段「" + field.getDisplayName() + "」选项源声明非法：" + e.getMessage(),
              field.getId(),
              field.getDisplayName()));
      return;
    }
    if (decl.isEmpty()) {
      return;
    }
    Optional<List<OptionItem>> live =
        enumSourceResolver.resolveDictOptions(decl.get().typeCode(), null);
    if (live.isEmpty()) {
      issues.add(
          EntityValidationIssue.fieldError(
              "ENUM_SOURCE_UNRESOLVED",
              "字段「"
                  + field.getDisplayName()
                  + "」绑定的字典 "
                  + decl.get().typeCode()
                  + " 不可达，发布将被拒绝（请检查字典服务或改绑其他值域）",
              field.getId(),
              field.getDisplayName()));
      return;
    }
    DriftDiff diff = diffAgainst(field, live.get());
    if (diff.hasDrift()) {
      issues.add(
          EntityValidationIssue.fieldWarning(
              "ENUM_SOURCE_DRIFT",
              "字段「"
                  + field.getDisplayName()
                  + "」的码表快照与真源 "
                  + decl.get().typeCode()
                  + " 有差异："
                  + diff.summary()
                  + "（removed 的历史数据旧值不受影响，重新发布后生效）",
              field.getId(),
              field.getDisplayName()));
    }
  }

  // ===================== 查询（设计器预览 / 差异对比） =====================

  /** 实时解析选项（doc 11 §9：{@code GET /fields/{fieldId}/options}，设计器预览用）。 */
  public List<OptionItem> liveOptions(Long fieldId) {
    MetaField field = requireField(fieldId);
    Optional<Declaration> decl = parseDeclaration(field.getEnumSource());
    if (decl.isEmpty()) {
      return parseSnapshot(field);
    }
    return enumSourceResolver
        .resolveDictOptions(decl.get().typeCode(), null)
        .orElseGet(() -> parseSnapshot(field));
  }

  /**
   * 绑定值域版本协商（v1.1 §4.3）：返回真源当前版本与字段快照版本，供前端判定「快照是否落后于真源、是否需要刷新」。
   *
   * <p>未绑定真源（inline / 无 enum_source）两版本均返回 null；真源不可达时 sourceVersion 为 null（前端按「未知」处理，不误判）。
   */
  public OptionsVersion optionsVersion(Long fieldId) {
    MetaField field = requireField(fieldId);
    Optional<Declaration> decl = parseDeclaration(field.getEnumSource());
    if (decl.isEmpty()) {
      return new OptionsVersion(null, null);
    }
    Long snapshotVersion = parseSnapshotVersion(field);
    Long sourceVersion = enumSourceResolver.resolveDictVersion(decl.get().typeCode()).orElse(null);
    return new OptionsVersion(sourceVersion, snapshotVersion);
  }

  /** 快照 vs 真源差异（doc 11 §9：{@code GET /fields/{fieldId}/option-diff}）。 */
  public DriftDiff diff(Long fieldId) {
    MetaField field = requireField(fieldId);
    Optional<Declaration> decl = parseDeclaration(field.getEnumSource());
    if (decl.isEmpty()) {
      return DriftDiff.empty();
    }
    Optional<List<OptionItem>> live =
        enumSourceResolver.resolveDictOptions(decl.get().typeCode(), null);
    return live.map(options -> diffAgainst(field, options)).orElseGet(DriftDiff::unresolved);
  }

  /**
   * 字典引用方清单（界面设计器字典打通 v1.1 §5.4：字典管理页「被引用情况 + 漂移告警入口」）。
   *
   * <p>同一 typeCode 的绑定字段共享一份真源，故<b>只解析一次</b>真源、在本地与各字段快照比对—— 避免「N 个绑定字段发 N 次字典服务请求」的放大；真源不可达时逐字段标
   * {@code resolved=false} （与单字段 diff 的不可达语义一致，不静默当成无漂移）。
   *
   * <p><b>为何不按租户过滤</b>：平台字典（tenantId=0）的治理视图本就该看到全部引用方， 「改一个值域炸出哪些表单」的影响分析与租户边界无关；调用侧由字典管理页的 {@code
   * sys:dict:write} 权限码把门。
   */
  public List<DictBinding> listBindings(String typeCode) {
    Optional<List<OptionItem>> live = enumSourceResolver.resolveDictOptions(typeCode, null);
    List<DictBinding> result = new ArrayList<>();
    for (MetaField field : metaFieldRepository.listDictBoundFields()) {
      Optional<Declaration> decl;
      try {
        decl = parseDeclaration(field.getEnumSource());
      } catch (RuntimeException e) {
        continue; // 非法声明在读路径按未绑定跳过（与 isBound 同姿态）
      }
      if (decl.isEmpty() || !decl.get().typeCode().equals(typeCode)) {
        continue;
      }
      MetaEntity entity = metaEntityRepository.findById(field.getEntityId());
      String entityName = entity != null ? entity.getName() : null;
      String entityCode = entity != null ? entity.getCode() : null;
      DriftDiff drift =
          live.map(options -> diffAgainst(field, options)).orElseGet(DriftDiff::unresolved);
      result.add(
          new DictBinding(
              field.getId(),
              field.getCode(),
              field.getDisplayName(),
              field.getEntityId(),
              entityCode,
              entityName,
              parseSnapshot(field).size(),
              drift.resolved(),
              drift.hasDrift(),
              drift.summary()));
    }
    return result;
  }

  // ===================== 内部实现 =====================

  /** SDK {@code findById} 返回可空实体（非 Optional），统一收敛 404 姿态。 */
  private MetaField requireField(Long fieldId) {
    MetaField field = metaFieldRepository.findById(fieldId);
    if (field == null) {
      throw CatalogErrors.of(CatalogErrorCodes.FIELD_NOT_FOUND, fieldId);
    }
    return field;
  }

  private List<OptionItem> resolveOrThrow(Declaration decl, MetaField field) {
    // 强制档：物化是写路径（保存/发布/刷新），必须拿真源此刻的状态——
    // TTL 缓存只服务被动读（liveOptions/diff），否则「发布即冻结」会冻结成旧口径（E2E 实证修正）
    Optional<List<OptionItem>> options =
        enumSourceResolver.resolveDictOptionsFresh(decl.typeCode(), null);
    if (options.isEmpty()) {
      throw CatalogErrors.of(
          CatalogErrorCodes.ENUM_SOURCE_RESOLVE_FAILED,
          "字段「" + field.getDisplayName() + "」绑定的字典 " + decl.typeCode() + " 不可达（物化失败）");
    }
    return options.get();
  }

  /**
   * 覆写 enum_values 并回写物化元数据（resolvedAt/resolvedCount/resolvedBy）。
   *
   * <p>enum_values 输出严格保持存量形状 {@code [{"code":..,"label":..}]}——parseEnumValues 与
   * RuntimeWriteValidator 的消费口径不能因物化而漂移；<b>仅 DEPRECATED 项追加 {@code state} 字段</b>（三态生命周期，v1.1
   * §4.5）：ACTIVE 项不写（字节级兼容存量快照），DEPRECATED 项写全（存量写放行的凭证 + 渲染侧过滤的依据）。
   */
  private void applyMaterialization(
      MetaField field, Declaration decl, List<OptionItem> options, String resolvedBy) {
    ArrayNode arr = objectMapper.createArrayNode();
    for (OptionItem item : options) {
      ObjectNode node = arr.addObject();
      node.put("code", item.code());
      node.put("label", item.label());
      if (item.isDeprecated()) {
        node.put("state", "DEPRECATED");
      }
    }
    String enumValuesJson;
    try {
      enumValuesJson = objectMapper.writeValueAsString(arr);
    } catch (Exception e) {
      // ArrayNode 序列化不失败；兜底仅为满足检查器
      throw CatalogErrors.of(
          CatalogErrorCodes.ENUM_SOURCE_RESOLVE_FAILED, "选项序列化失败: " + e.getMessage());
    }
    // 记录物化时刻的真源版本号（v1.1 §4.3 版本协商）：供前端比对「快照是否落后于真源」。
    long version = enumSourceResolver.resolveDictVersion(decl.typeCode()).orElse(0L);
    // 经领域行为方法变更（MetaField 无 setter，与 assignPhysicalColumn 同纪律）
    field.applyEnumMaterialization(
        enumValuesJson, withResolvedMeta(decl, options.size(), resolvedBy, version));
  }

  /** 在声明 JSON 上回写 resolved* 元数据（建模者只填 type/mode/ref，服务端写 resolved*）。 */
  private String withResolvedMeta(Declaration decl, int count, String resolvedBy, long version) {
    ObjectNode root = decl.root().deepCopy();
    root.put("resolvedAt", Instant.now().toString());
    root.put("resolvedCount", count);
    root.put("resolvedBy", resolvedBy);
    root.put("resolvedVersion", version);
    try {
      return objectMapper.writeValueAsString(root);
    } catch (Exception e) {
      // writeValueAsString 对 ObjectNode 不失败；兜底仅为满足检查器，保留原声明
      log.warn("enum_source 物化元数据回写失败（保留原声明）: field={}", decl.typeCode(), e);
      return decl.root().toString();
    }
  }

  private int countSnapshot(MetaField field) {
    return parseSnapshot(field).size();
  }

  private List<OptionItem> parseSnapshot(MetaField field) {
    List<OptionItem> result = new ArrayList<>();
    try {
      JsonNode arr =
          objectMapper.readTree(field.getEnumValues() == null ? "[]" : field.getEnumValues());
      if (arr.isArray()) {
        for (JsonNode node : arr) {
          String code = node.path("code").asText(null);
          if (code != null && !code.isBlank()) {
            // 三态口径：DEPRECATED 项物化时带 state；缺省（inline/存量快照）按 ACTIVE
            String state = node.path("state").asText("ACTIVE");
            result.add(new OptionItem(code, node.path("label").asText(code), state));
          }
        }
      }
    } catch (Exception e) {
      log.warn("enum_values 快照解析失败（按空快照处理）: fieldId={}", field.getId(), e);
    }
    return result;
  }

  /** 从 enum_source 声明的 resolvedVersion 读取上次物化的真源版本号（前端版本协商比对用）。 */
  private Long parseSnapshotVersion(MetaField field) {
    try {
      JsonNode root =
          objectMapper.readTree(field.getEnumSource() == null ? "{}" : field.getEnumSource());
      JsonNode v = root.path("resolvedVersion");
      return v != null && v.isNumber() ? v.asLong() : null;
    } catch (Exception e) {
      log.warn("enum_source.resolvedVersion 解析失败（按 null 处理）: fieldId={}", field.getId(), e);
      return null;
    }
  }

  private DriftDiff diffAgainst(MetaField field, List<OptionItem> live) {
    Map<String, String> snapshot = new LinkedHashMap<>();
    Map<String, String> snapshotState = new LinkedHashMap<>();
    for (OptionItem item : parseSnapshot(field)) {
      snapshot.put(item.code(), item.label());
      snapshotState.put(item.code(), item.state());
    }
    Map<String, String> current = new LinkedHashMap<>();
    Map<String, String> currentState = new LinkedHashMap<>();
    for (OptionItem item : live) {
      current.put(item.code(), item.label());
      currentState.put(item.code(), item.state());
    }
    List<String> added = new ArrayList<>();
    List<String> removed = new ArrayList<>();
    List<String> labelChanged = new ArrayList<>();
    List<String> stateChanged = new ArrayList<>();
    for (String code : current.keySet()) {
      if (!snapshot.containsKey(code)) {
        added.add(code);
      } else if (!String.valueOf(snapshot.get(code)).equals(current.get(code))) {
        labelChanged.add(code);
      }
      // 状态变迁（如 ACTIVE→DEPRECATED）同样是要重新发布的信号：渲染集会少一项
      if (snapshotState.containsKey(code)
          && !String.valueOf(snapshotState.get(code)).equals(currentState.get(code))) {
        stateChanged.add(code);
      }
    }
    for (String code : snapshot.keySet()) {
      if (!current.containsKey(code)) {
        removed.add(code);
      }
    }
    return new DriftDiff(added, removed, labelChanged, stateChanged, true);
  }

  private static String textOf(JsonNode node, String dft) {
    if (node == null || node.isNull()) {
      return dft;
    }
    String v = node.asText(null);
    return v == null || v.isBlank() ? dft : v;
  }

  // ===================== 值对象 =====================

  /** 解析后的选项源声明（P0：dict 一种 non-inline 类型；ref 定位 = typeCode）。 */
  public record Declaration(String type, String mode, String typeCode, JsonNode root) {}

  /** 物化结果（手动刷新的返回值：刷新前后项数）。 */
  public record MaterializeResult(Long fieldId, int beforeCount, int afterCount) {}

  /**
   * 绑定值域版本协商（v1.1 §4.3）：真源当前版本 vs 字段快照版本。
   *
   * @param sourceVersion 真源（bone-system）值域业务版本号；未绑定/不可达为 null
   * @param snapshotVersion 字段上次物化时记录的真源版本号（enum_source.resolvedVersion）；从未物化为 null
   */
  public record OptionsVersion(Long sourceVersion, Long snapshotVersion) {}

  /**
   * 字典引用方清单项（v1.1 §5.4）：某字段绑定了该值域 + 快照新鲜度。
   *
   * @param resolved 真源是否可达（false = 字典服务不可达，漂移状态无从判定）
   * @param drift 快照与真源是否有差异（added/removed/labelChanged/stateChanged 任一非空）
   * @param driftSummary 人类可读摘要（「新增 1 项，状态变迁 1 项」/「无差异」/「真源不可达」）
   */
  public record DictBinding(
      Long fieldId,
      String fieldCode,
      String fieldName,
      Long entityId,
      String entityCode,
      String entityName,
      int snapshotCount,
      boolean resolved,
      boolean drift,
      String driftSummary) {}

  /**
   * 快照 vs 真源差异（doc 11 §6.3 ENUM_SOURCE_DRIFT 的结构化载荷）。
   *
   * @param resolved false = 真源不可达（调用方按 ENUM_SOURCE_UNRESOLVED 处理，不出现在 diff 里）
   */
  public record DriftDiff(
      List<String> added,
      List<String> removed,
      List<String> labelChanged,
      List<String> stateChanged,
      boolean resolved) {

    public static DriftDiff empty() {
      return new DriftDiff(List.of(), List.of(), List.of(), List.of(), true);
    }

    public static DriftDiff unresolved() {
      return new DriftDiff(List.of(), List.of(), List.of(), List.of(), false);
    }

    public boolean hasDrift() {
      return resolved
          && !(added.isEmpty()
              && removed.isEmpty()
              && labelChanged.isEmpty()
              && stateChanged.isEmpty());
    }

    public String summary() {
      if (!resolved) {
        return "真源不可达";
      }
      StringBuilder sb = new StringBuilder();
      if (!added.isEmpty()) {
        sb.append("新增 ").append(added.size()).append(" 项");
      }
      if (!removed.isEmpty()) {
        if (sb.length() > 0) {
          sb.append("，");
        }
        sb.append("移除 ").append(removed.size()).append(" 项");
      }
      if (!labelChanged.isEmpty()) {
        if (sb.length() > 0) {
          sb.append("，");
        }
        sb.append("改标签 ").append(labelChanged.size()).append(" 项");
      }
      if (!stateChanged.isEmpty()) {
        if (sb.length() > 0) {
          sb.append("，");
        }
        sb.append("状态变迁 ").append(stateChanged.size()).append(" 项");
      }
      return sb.length() == 0 ? "无差异" : sb.toString();
    }
  }
}
