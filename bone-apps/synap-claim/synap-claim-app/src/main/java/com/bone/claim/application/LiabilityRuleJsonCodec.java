package com.bone.claim.application;

import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.LiabilityRuleJson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * `liability_rule_json` 的编解码与写入期 Schema（M2 ② Shadow）。
 *
 * <p><b>实测事实，它决定了这里的比对方式</b>（2026-11-09，本地 MySQL 8 临时表探针）：MySQL 的 {@code JSON} 列会把 {@code 90.00}
 * 规范化成 {@code 90.0}，并<b>重排键序</b>。于是：
 *
 * <ul>
 *   <li>「回读后与写入字符串逐字相等」是<b>做不到</b>的判据，只会制造误报；
 *   <li>唯一成立的判据是<b>数值相等</b>（{@link BigDecimal#compareTo}），这正是 {@code assertProjectsTo} 的写法；
 *   <li>编码的确定性顺序只保证"同一份配置两次编码结果一致"，不承诺"落库后仍是这个顺序"。
 * </ul>
 *
 * <p><b>为什么 Schema 是手写判据而不是引一个 JSON Schema 库</b>：这里要判的东西只有四类——版本、键集合、 A 类键的数值形态、B
 * 类维度的边界。引库要多一个依赖、多一份 Schema 文件与代码不同步的风险，而判据本身十几行。 真正的理由不是"省事"：{@code additionalProperties: false}
 * 与"未知版本 fail-closed"必须**同时**落在写入期， 否则拼错的键会被静默忽略——那正是本方案要防的静默失效。
 *
 * <p><b>② 只接受「可映射子集」</b>：{@link LiabilityRuleJson#AMOUNT_KEYS} 三个键必须是 JSON 数字。 分段 / 金额区间 /
 * 累计（数组或对象形态）在 ④ 解锁后才可用，此处显式拒绝并说明何时可用——错误消息里写清"何时"， 运营才不会把"现在不支持"读成"永远不支持"。
 *
 * <p><b>零 Spring 依赖</b>：用类内私有 {@link ObjectMapper} 而不是容器里那份，避免全局配置（命名策略、时间格式、
 * 精度开关）漂移导致同一份数据在不同服务里编出不同字符串——影子要和标量比对，编码必须可复现。
 */
public final class LiabilityRuleJsonCodec {

  /** 单个文档上限：影子是 9 个业务要素的容器，不是附件仓库。 */
  private static final int MAX_RAW_LENGTH = 8 * 1024;

  /** 单个 B 类维度的上限（子键数 / 嵌套深度 / 序列化长度）：挡投毒与误把大对象塞进来。 */
  private static final int MAX_DIMENSION_KEYS = 32;

  private static final int MAX_DIMENSION_DEPTH = 3;
  private static final int MAX_DIMENSION_LENGTH = 4 * 1024;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LiabilityRuleJsonCodec() {}

  /**
   * 编码为确定性字符串（键顺序固定：版本 → 3 个 A 类键 → B 类维度按字典序）。
   *
   * <p>顺序固定不是为了美观：同一份配置两次写入必须产出同一个字符串，否则回读比对会退化成"每次都不一样"， 也让人工核对 {@code JSON_PRETTY} 的结果失去可比性。
   */
  public static String encode(LiabilityRuleJson json) {
    if (json == null) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "待写入的 liability_rule_json 为空");
    }
    try {
      var root = MAPPER.createObjectNode();
      root.put(LiabilityRuleJson.KEY_SCHEMA_VERSION, json.schemaVersion());
      root.put(LiabilityRuleJson.KEY_PAY_PERCENT, json.payPercent());
      root.put(LiabilityRuleJson.KEY_LIABILITY_DEDUCT, json.liabilityDeduct());
      root.put(LiabilityRuleJson.KEY_LIABILITY_LIMIT, json.liabilityLimit());
      // 维度以「原始 JSON 文本」保存，这里原样嵌回而不是再走一次对象转换，
      // 保证不认识的子结构不被改写（opaque 数据的唯一正确做法就是不碰它）。
      for (Map.Entry<String, String> e : json.dimensions().entrySet()) {
        root.set(e.getKey(), MAPPER.readTree(e.getValue()));
      }
      return MAPPER.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT, "liability_rule_json 序列化失败：%s", e.getOriginalMessage());
    }
  }

  /**
   * 严格解码：版本、键集合、A 类键形态、B 类维度边界四项全部通过才返回。
   *
   * @throws RuleGap 任何一项不满足（调用方负责把它翻译成配置面的受控错误码）
   */
  public static LiabilityRuleJson decode(String raw) {
    if (raw == null || raw.isBlank()) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT, "liability_rule_json 为空：历史行可以为 NULL（走旧列），但已写入的行不得为空");
    }
    if (raw.length() > MAX_RAW_LENGTH) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "liability_rule_json 长度 %d 超过上限 %d：它只承载 9 个业务要素",
          raw.length(),
          MAX_RAW_LENGTH);
    }
    JsonNode root;
    try {
      root = MAPPER.readTree(raw);
    } catch (JsonProcessingException e) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "liability_rule_json 不是合法 JSON：%s", raw);
    }
    if (root == null || !root.isObject()) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "liability_rule_json 必须是 JSON 对象：%s", raw);
    }
    int version = requireVersion(root);
    requireNoUnknownKey(root);
    BigDecimal payPercent = requireAmount(root, LiabilityRuleJson.KEY_PAY_PERCENT);
    BigDecimal deduct = requireAmount(root, LiabilityRuleJson.KEY_LIABILITY_DEDUCT);
    BigDecimal limit = requireAmount(root, LiabilityRuleJson.KEY_LIABILITY_LIMIT);
    Map<String, String> dimensions = readDimensions(root);
    return new LiabilityRuleJson(version, payPercent, deduct, limit, dimensions);
  }

  /**
   * 写入后回读比对：JSON 必须真的落库，且 {@code scalar == project(json)}。
   *
   * <p><b>为什么是回读而不是拿内存对象自比</b>：内存对象里 JSON 与标量天然一致（同一段代码算出来的）， 能证明的只有"代码没写错"。真正的风险在链路两侧——SDK 把
   * {@code String} 写进 JSON 列丢了、模板少列读不回来、 实体 save 覆盖——只有从库里再读一遍才看得见。
   */
  public static void assertShadowConsistent(AdjLiability reloaded, long liabilityId) {
    if (reloaded == null) {
      throw new IllegalStateException("责任配置落库后回读不到（id=" + liabilityId + "）：影子无法验证，按失败处理，不写入可疑数据");
    }
    String raw = reloaded.getLiabilityRuleJson();
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException(
          "责任配置 id="
              + liabilityId
              + " 回读的 liability_rule_json 为空：影子未落库"
              + "（检查实体 @Column 与 SQL 模板是否同批）");
    }
    LiabilityRuleJson shadow = decode(raw);
    try {
      shadow.assertProjectsTo(
          reloaded.getPayPercent(), reloaded.getLiabilityDeduct(), reloaded.getLiabilityLimit());
    } catch (RuleGap gap) {
      throw new IllegalStateException(
          "责任配置 id=" + liabilityId + " 的 JSON 影子与标量不一致：" + gap.detail(), gap);
    }
  }

  private static int requireVersion(JsonNode root) {
    JsonNode node = root.get(LiabilityRuleJson.KEY_SCHEMA_VERSION);
    if (node == null) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT, "liability_rule_json 缺少 schemaVersion：缺了它，明年的读代码无法解释今年的数据");
    }
    if (!node.isIntegralNumber()) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "schemaVersion 必须是整数：%s", node.toString());
    }
    int version = node.asInt();
    if (version != LiabilityRuleJson.CURRENT_SCHEMA_VERSION) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "未知的 schemaVersion=%d（当前仅支持 %d）：未来版本的数据不得按旧语义解释",
          version,
          LiabilityRuleJson.CURRENT_SCHEMA_VERSION);
    }
    return version;
  }

  private static void requireNoUnknownKey(JsonNode root) {
    Iterator<String> names = root.fieldNames();
    while (names.hasNext()) {
      String name = names.next();
      if (!LiabilityRuleJson.KNOWN_KEYS.contains(name)) {
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT,
            "未知键「%s」：拼错的键不得被静默忽略（合法键是 §5.2 的 9 个要素 + schemaVersion）",
            name);
      }
    }
  }

  private static BigDecimal requireAmount(JsonNode root, String key) {
    JsonNode node = root.get(key);
    if (node == null || node.isNull()) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT, "缺少必需计算参数 %s：缺省按 0 会静默少赔（历史行请整列留 NULL 走旧列回落，不要写空对象）", key);
    }
    if (!node.isNumber()) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "%s 必须是单一数值，当前是 %s：分段 / 金额区间 / 按累计（数组或对象形态）在 ④ Unlock 解锁后才可用，" + "② 阶段只允许标量可表达的取值",
          key,
          node.getNodeType());
    }
    return node.decimalValue();
  }

  private static Map<String, String> readDimensions(JsonNode root) {
    Map<String, String> dimensions = new LinkedHashMap<>();
    for (String key : LiabilityRuleJson.DIMENSION_KEYS) {
      JsonNode node = root.get(key);
      if (node == null) {
        continue;
      }
      if (!node.isObject()) {
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT,
            "维度 %s 必须是 JSON 对象（当前是 %s）：该键承载的是「附加限制」，标量形态无法表达多子维",
            key,
            node.getNodeType());
      }
      String encoded = node.toString();
      if (encoded.length() > MAX_DIMENSION_LENGTH) {
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT,
            "维度 %s 序列化长度 %d 超过上限 %d：它不是附件仓库",
            key,
            encoded.length(),
            MAX_DIMENSION_LENGTH);
      }
      if (node.size() > MAX_DIMENSION_KEYS) {
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT,
            "维度 %s 的子键数 %d 超过上限 %d",
            key,
            node.size(),
            MAX_DIMENSION_KEYS);
      }
      if (depth(node) > MAX_DIMENSION_DEPTH) {
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT,
            "维度 %s 的嵌套深度超过上限 %d：深层嵌套说明该拆成新的业务要素，而不是塞进现有键",
            key,
            MAX_DIMENSION_DEPTH);
      }
      dimensions.put(key, encoded);
    }
    return dimensions;
  }

  /** 递归深度（对象/数组计入；标量深度 1）。 */
  private static int depth(JsonNode node) {
    if (!node.isContainerNode()) {
      return 1;
    }
    int max = 0;
    for (JsonNode child : node) {
      max = Math.max(max, depth(child));
    }
    return max + 1;
  }
}
