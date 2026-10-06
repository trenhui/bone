package com.bone.blueprint.infrastructure.channel.openapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道协议规格公共骨架 —— 承接「错误响应查找 + 业务体定位 + 签名」这三件四个平台都在做、但字段名与位置各异的事。
 *
 * <p><b>错误体为什么要在嵌套里找</b>：拼多多的错误是 {@code {"logistics_xxx_response":{"error_response":{...}}}}——
 * 错误藏在业务体<strong>里面</strong>；淘宝/京东/抖音则放在根层。只查根层会漏掉拼多多的全部错误， 把「渠道拒绝」误判成「调用成功」，
 * 于是运营看到的是「同步库存永远成功但渠道没变」。因此这里做有界深度（3 层）查找。
 *
 * <p><b>签名为什么也在骨架里</b>：{@link #sign} 把「密钥参与位置」这一渠道差异（TOP 系前后各一份密钥、宙斯系尾部一份）收在规格实现里，
 * 客户端因此不必为四个渠道各写一遍拼串。
 *
 * @param channelCode 渠道码
 * @param gateway 网关
 * @param signature 签名算法
 * @param tokenParamName 令牌参数名
 * @param tokenAsHeader 令牌是否走 Header
 * @param tokenHeaderName 令牌 Header 名
 * @param payloadCandidates 业务体候选键（按优先级，最后一项是兜底）
 * @param resultWrappedAsJson 业务体是否用 JSON 字符串再包一层（京东 {@code jd_response_content.result}）
 */
public abstract class AbstractChannelApiSpec implements ChannelApiSpec {

  /** 错误体关键字：四个平台都用它表达「这次调用被拒绝了」。 */
  private static final String ERROR_NODE = "error_response";

  /** 错误码候选字段名（顺序即优先级）。 */
  private static final List<String> ERROR_CODE_KEYS = List.of("code", "error_code", "sub_code");

  /** 错误文案候选字段名（顺序即优先级）。 */
  private static final List<String> ERROR_MSG_KEYS =
      List.of("msg", "message", "error_msg", "sub_msg", "error_description");

  private final String channelCode;
  private final String gateway;
  private final ChannelSignatureAlgorithm signature;
  private final String tokenParamName;
  private final boolean tokenAsHeader;
  private final String tokenHeaderName;
  private final List<String> payloadCandidates;
  private final boolean resultWrappedAsJson;

  protected AbstractChannelApiSpec(
      String channelCode,
      String gateway,
      ChannelSignatureAlgorithm signature,
      String tokenParamName,
      boolean tokenAsHeader,
      String tokenHeaderName,
      List<String> payloadCandidates,
      boolean resultWrappedAsJson) {
    this.channelCode = channelCode;
    this.gateway = gateway;
    this.signature = signature;
    this.tokenParamName = tokenParamName;
    this.tokenAsHeader = tokenAsHeader;
    this.tokenHeaderName = tokenHeaderName;
    this.payloadCandidates = payloadCandidates;
    this.resultWrappedAsJson = resultWrappedAsJson;
  }

  @Override
  public String channelCode() {
    return channelCode;
  }

  @Override
  public String gateway() {
    return gateway;
  }

  @Override
  public ChannelSignatureAlgorithm signature() {
    return signature;
  }

  @Override
  public String tokenParamName() {
    return tokenParamName;
  }

  @Override
  public boolean tokenAsHeader() {
    return tokenAsHeader;
  }

  @Override
  public String tokenHeaderName() {
    return tokenHeaderName;
  }

  @Override
  public String sign(Map<String, String> signedParams, String secret) {
    return signature.sign(secret, signInput(signedParams, secret));
  }

  /**
   * 生成待签串（密钥参与位置：TOP 系与拼多多「前后各一份密钥」，宙斯系「尾部一份」）。
   *
   * <p>各平台对「密钥放在串头 / 串尾 / 前后」的要求并不一致，签错一律表现为渠道侧 {@code sign error}，
   * 且<strong>本地没有任何渠道能校验出来</strong>——所以这条必须跟着渠道写在规格里，不能由客户端替所有渠道假设同一种。
   */
  protected String signInput(Map<String, String> signedParams, String secret) {
    String joined = ChannelSignatureAlgorithm.concat(signedParams);
    return secret == null ? joined : secret + joined + secret;
  }

  @Override
  public ChannelApiResult parse(String rawBody) {
    Map<String, Object> root = ChannelJson.parse(rawBody);
    if (root == null) {
      return ChannelApiResult.rejected("PARSE_FAILED", "渠道响应无法解析为 JSON", rawBody);
    }

    Map<String, Object> error = findError(root, 0);
    if (error != null) {
      return ChannelApiResult.rejected(errorCode(error), errorMessage(error), rawBody);
    }

    // 顶层通用信封（抖音 {"code":0,"data":{...}}）：code 非 0 即业务失败。
    Object topCode = root.get("code");
    if (topCode != null) {
      Long code = toLong(topCode);
      if (code != null && code != 0L) {
        String message =
            firstNotBlank(ChannelJson.str(root, "message"), ChannelJson.str(root, "msg"));
        return ChannelApiResult.rejected(String.valueOf(code), message, rawBody);
      }
    }

    return ChannelApiResult.ok(resolvePayload(root), rawBody);
  }

  /** 定位业务体：命中任一候选键即解析；都没有则返回 {@code null}（成功但没有业务体）。 */
  private Map<String, Object> resolvePayload(Map<String, Object> root) {
    for (String key : payloadCandidates) {
      Map<String, Object> payload = asPayload(root.get(key));
      if (payload != null) {
        return unwrap(payload);
      }
    }
    // 兜底：路由网关类渠道把业务体放在以接口名派生的节点里（…_response）。
    // 不按接口名现算，是因为解析发生在「只有响应体、没有请求上下文」的位置，
    // 而节点名是响应格式的一部分——扫描比猜规则更稳，且响应里多一个同后缀节点也不会误命中（取首个）。
    for (Map.Entry<String, Object> entry : root.entrySet()) {
      if (entry.getKey().endsWith("_response") || "data".equals(entry.getKey())) {
        Map<String, Object> payload = asPayload(entry.getValue());
        if (payload != null) {
          return unwrap(payload);
        }
      }
    }
    return null;
  }

  private Map<String, Object> asPayload(Object node) {
    return node instanceof Map<?, ?> map ? cast(map) : null;
  }

  /** 京东把业务体用 JSON 字符串再包一层（{@code jd_response_content.result}），按需二次解析。 */
  private Map<String, Object> unwrap(Map<String, Object> payload) {
    if (!resultWrappedAsJson) {
      return payload;
    }
    String wrapped = ChannelJson.str(payload, "result");
    return wrapped == null ? payload : ChannelJson.parse(wrapped);
  }

  /**
   * 有界深度查找错误体。
   *
   * <p>深度上限 3：够覆盖「根 error_response」与「业务体内 error_response」两种形态； 再深说明响应格式异常，宁可走「成功但无业务体」让上层报错，
   * 也不无限递归。
   */
  private Map<String, Object> findError(Map<String, Object> node, int depth) {
    if (node == null || depth > 3) {
      return null;
    }
    for (Map.Entry<String, Object> entry : node.entrySet()) {
      if (ERROR_NODE.equals(entry.getKey()) && entry.getValue() instanceof Map<?, ?> found) {
        return cast(found);
      }
    }
    for (Object child : new ArrayList<>(node.values())) {
      if (child instanceof Map<?, ?> map) {
        Map<String, Object> found = findError(cast(map), depth + 1);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static String errorCode(Map<String, Object> error) {
    for (String key : ERROR_CODE_KEYS) {
      String value = ChannelJson.str(error, key);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "UNKNOWN";
  }

  private static String errorMessage(Map<String, Object> error) {
    for (String key : ERROR_MSG_KEYS) {
      String value = ChannelJson.str(error, key);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "渠道未返回错误详情";
  }

  private static String firstNotBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return second;
  }

  /** 顶级 code 字段：抖音返回数字、TOP 系返回字符串，统一按数字解析。 */
  private static Long toLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> cast(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }

  /** 渠道公共参数容器（保持插入序，便于与渠道文档逐项比对）。 */
  protected static Map<String, String> params() {
    return new LinkedHashMap<>();
  }
}
