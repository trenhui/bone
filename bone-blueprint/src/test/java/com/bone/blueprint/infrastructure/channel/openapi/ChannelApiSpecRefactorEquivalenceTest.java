package com.bone.blueprint.infrastructure.channel.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 协议规格层的两项重构（P2-1 凭证槽位枚举化、P3-1 公共参数上提）<strong>行为等价性</strong>锁定。
 *
 * <p><b>为什么必须逐字锁死</b>：公共参数直接参与签名。四个平台的签名规则要求「参数名升序拼接 + 密钥」，
 * <strong>多一个参数、少一个参数、大小写不同、顺序不同，都会得到完全不同的签名</strong>， 而渠道侧只回一句 {@code sign error}、本地永远复现不出来。
 * 所以这不是「重构后跑一遍绿就行」，而是必须把每家的参数集合逐项断言。
 *
 * <p>基线（重构前）：
 *
 * <pre>
 *   TAOBAO: method, format=json, v=2.0, sign_method=md5, timestamp(yyyy-MM-dd HH:mm:ss)
 *   JD     : method, format=json, sign_method=md5, timestamp(yyyy-MM-dd HH:mm:ss)
 *   DOUYIN : method, format=json, timestamp(秒级原样)          ← 无 sign_method
 *   PDD    : method, format=JSON, sign_method=md5, timestamp(yyyy-MM-dd HH:mm:ss)
 * </pre>
 */
class ChannelApiSpecRefactorEquivalenceTest {

  private static final long TS = 1767225600L; // 2026-01-01 00:00:00 UTC，固定值便于断言

  private final List<ChannelApiSpec> specs =
      List.of(new TaobaoApiSpec(), new JdApiSpec(), new DouyinApiSpec(), new PddApiSpec());

  @Nested
  @DisplayName("P3-1：公共参数上提骨架后，四家产出与重构前逐项一致")
  class CommonParamsEquivalence {

    @Test
    @DisplayName("淘宝：method/format=json/v=2.0/sign_method=md5/TOP 时间格式")
    void taobaoMatchesBaseline() {
      Map<String, String> p = new TaobaoApiSpec().commonParams("taobao.trade.orders.get", TS);

      assertEquals("taobao.trade.orders.get", p.get("method"));
      assertEquals("json", p.get("format"));
      assertEquals("2.0", p.get("v"), "淘宝独有的接口版本号不能丢");
      assertEquals("md5", p.get("sign_method"));
      assertTrue(
          p.get("timestamp").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
          "淘宝时间格式应为 yyyy-MM-dd HH:mm:ss，实际: " + p.get("timestamp"));
    }

    @Test
    @DisplayName("京东：method/format=json/sign_method=md5/TOP 时间格式，且无 v")
    void jdMatchesBaseline() {
      Map<String, String> p = new JdApiSpec().commonParams("jos.order.search", TS);

      assertEquals("jos.order.search", p.get("method"));
      assertEquals("json", p.get("format"));
      assertEquals("md5", p.get("sign_method"));
      assertTrue(
          p.get("timestamp").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
          "京东时间格式应为 yyyy-MM-dd HH:mm:ss");
      assertFalse(p.containsKey("v"), "京东不带接口版本号");
    }

    @Test
    @DisplayName("抖音：method/format=json/秒级时间戳原样，且无 sign_method（HMAC 不需要）")
    void douyinMatchesBaseline() {
      Map<String, String> p = new DouyinApiSpec().commonParams("order.searchList", TS);

      assertEquals("order.searchList", p.get("method"));
      assertEquals("json", p.get("format"));
      assertEquals(String.valueOf(TS), p.get("timestamp"), "抖音用秒级时间戳原样输出");
      assertFalse(
          p.containsKey("sign_method"), "抖音走 HMAC-SHA256，声明 sign_method=md5 会被渠道拒；这条是行为等价的红线");
      assertFalse(p.containsKey("v"));
    }

    @Test
    @DisplayName("拼多多：format 为大写 JSON（其余三家小写），TOP 时间格式")
    void pddMatchesBaseline() {
      Map<String, String> p = new PddApiSpec().commonParams("order.searchList", TS);

      assertEquals("JSON", p.get("format"), "拼多多的 format 是大写，写成小写会被拒");
      assertEquals("md5", p.get("sign_method"));
      assertTrue(
          p.get("timestamp").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
          "拼多多时间格式应为 yyyy-MM-dd HH:mm:ss");
    }

    @ParameterizedTest
    @EnumSource(
        value = ChannelSignatureAlgorithm.class,
        names = {"MD5"})
    @DisplayName("MD5 系平台都必须带 sign_method=md5（由签名算法自动判定）")
    void md5PlatformsAlwaysDeclareSignMethod(ChannelSignatureAlgorithm algorithm) {
      assertEquals("md5", algorithm.name().toLowerCase());
      for (ChannelApiSpec spec : specs) {
        if (spec.signature() != ChannelSignatureAlgorithm.MD5) {
          continue;
        }
        assertEquals(
            "md5",
            spec.commonParams("x", TS).get("sign_method"),
            spec.channelCode() + " 用 MD5 却未声明 sign_method");
      }
    }

    @Test
    @DisplayName("HMAC 平台不得声明 sign_method（声明了会与 HMAC 算法矛盾）")
    void hmacPlatformOmitsSignMethod() {
      for (ChannelApiSpec spec : specs) {
        if (spec.signature() != ChannelSignatureAlgorithm.HMAC_SHA256) {
          continue;
        }
        assertFalse(
            spec.commonParams("x", TS).containsKey("sign_method"),
            spec.channelCode() + " 用 HMAC 却声明了 sign_method=md5");
      }
    }

    @Test
    @DisplayName("公共参数不含凭证与签名（凭证与 sign 由客户端补，否则签名会自相矛盾）")
    void commonParamsCarryNoCredentialNorSign() {
      for (ChannelApiSpec spec : specs) {
        Map<String, String> p = spec.commonParams("api.method", TS);
        assertFalse(p.containsKey("sign"), spec.channelCode() + " 公共参数不应含 sign");
        assertFalse(p.containsKey("app_key"), spec.channelCode() + " 公共参数不应含 app_key");
        assertFalse(p.containsKey("client_id"), spec.channelCode() + " 公共参数不应含 client_id");
        assertFalse(p.containsKey("access_token"), spec.channelCode() + " 公共参数不应含令牌");
        assertFalse(p.containsKey("token"), spec.channelCode() + " 公共参数不应含令牌");
      }
    }

    @Test
    @DisplayName("公共参数保持插入序（排障时能与渠道文档逐项比对）")
    void keepsInsertionOrder() {
      for (ChannelApiSpec spec : specs) {
        var keys = new java.util.ArrayList<>(spec.commonParams("m", TS).keySet());
        assertEquals("method", keys.get(0), spec.channelCode() + " 首个参数应为 method");
        assertTrue(keys.contains("timestamp"), spec.channelCode() + " 必须带timestamp（否则签名与渠道校验不一致）");
      }
    }

    /**
     * 平台特有参数（淘宝的 {@code v}）放在公共参数末尾是否影响签名？
     *
     * <p><b>结论：不影响</b>。签名走 {@code ChannelSignatureAlgorithm#concat}，内部用 {@code TreeMap}
     * 按参数名升序重排后才拼接，因此插入顺序与签名结果无关。
     *
     * <p>本用例把这条认知钉死：若将来有人把 {@code concat} 改成直接遍历 {@code LinkedHashMap}， 淘宝的 {@code v}
     * 会因位置变化而得到完全不同的签名 —— 而渠道只回 {@code sign error}。
     */
    @Test
    @DisplayName("平台特有参数放在末尾不影响签名（concat 内部按参数名排序）")
    void extraParamsAtEndDoNotAffectSignature() {
      Map<String, String> p = new TaobaoApiSpec().commonParams("taobao.trade.orders.get", TS);

      // 无论 v 在哪个位置，按参数名升序拼接的结果必须一致（此处刻意打乱插入顺序）
      Map<String, String> shuffled = new java.util.LinkedHashMap<>();
      shuffled.put("timestamp", p.get("timestamp"));
      shuffled.put("v", p.get("v"));
      shuffled.put("method", p.get("method"));
      shuffled.put("format", p.get("format"));
      shuffled.put("sign_method", p.get("sign_method"));

      assertEquals(
          ChannelSignatureAlgorithm.concat(p),
          ChannelSignatureAlgorithm.concat(shuffled),
          "签名必须只取决于参数名升序结果，与插入顺序无关；否则淘宝多一个 v 就会签名错误");
    }
  }

  @Nested
  @DisplayName("P2-1：凭证槽位枚举化")
  class CredentialSlotEnum {

    @Test
    @DisplayName("槽位取值覆盖 appKey/appSecret/accessToken 三项")
    void slotResolvesEachCredentialField() {
      ChannelCredentials c = new ChannelCredentials("AK", "AS", "AT", null);
      assertEquals("AK", CredentialSlot.APP_KEY.valueOf(c));
      assertEquals("AS", CredentialSlot.APP_SECRET.valueOf(c));
      assertEquals("AT", CredentialSlot.ACCESS_TOKEN.valueOf(c));
    }

    @Test
    @DisplayName("每家规格的凭证参数名与槽位映射正确（拼多多是 client_*，其余是 app_*）")
    void specsDeclareCorrectParamNames() {
      assertEquals(
          Map.of("app_key", CredentialSlot.APP_KEY, "app_secret", CredentialSlot.APP_SECRET),
          new TaobaoApiSpec().credentialKeys());
      assertEquals(
          Map.of("app_key", CredentialSlot.APP_KEY, "app_secret", CredentialSlot.APP_SECRET),
          new JdApiSpec().credentialKeys());
      assertEquals(
          Map.of("app_key", CredentialSlot.APP_KEY, "app_secret", CredentialSlot.APP_SECRET),
          new DouyinApiSpec().credentialKeys());
      assertEquals(
          Map.of("client_id", CredentialSlot.APP_KEY, "client_secret", CredentialSlot.APP_SECRET),
          new PddApiSpec().credentialKeys());
    }

    @Test
    @DisplayName("槽位是枚举而非字符串（拼错应在编译期被拦下）")
    void slotIsEnumNotString() {
      assertTrue(CredentialSlot.class.isEnum(), "槽位必须是枚举，字符串拼错会静默跳过填参");
      assertEquals(3, CredentialSlot.values().length);
      for (CredentialSlot slot : CredentialSlot.values()) {
        assertNotNull(slot.name());
      }
    }
  }

  @Nested
  @DisplayName("P2-2：鉴权错误判定不再误判业务码")
  class AuthErrorDetection {

    @Test
    @DisplayName("真实鉴权错误码能被识别")
    void detectsRealAuthCodes() {
      assertTrue(ChannelApiResult.rejected("401", "未授权", null).isAuthError());
      assertTrue(ChannelApiResult.rejected("INVALID_TOKEN", "", null).isAuthError());
      assertTrue(ChannelApiResult.rejected("SIGN_ERROR", "", null).isAuthError());
      assertTrue(ChannelApiResult.rejected("UNAUTHORIZED", "", null).isAuthError());
    }

    @Test
    @DisplayName("中文文案无需「签名」与「失败」相邻即可识别（原实现要求两词同时出现）")
    void detectsChineseMessageWithoutAdjacency() {
      assertTrue(ChannelApiResult.rejected("X", "签名不正确", null).isAuthError());
      assertTrue(ChannelApiResult.rejected("X", "用户未授权", null).isAuthError());
      assertTrue(ChannelApiResult.rejected("X", "access token 已过期", null).isAuthError());
    }

    @Test
    @DisplayName("★ 子串误判回归：含 401 但属业务语义的错误码不得判为鉴权失败")
    void doesNotMisjudgeBusinessCodesContaining401() {
      // 原实现 code.contains("401") 会把下面这些全部误判为鉴权失效
      assertFalse(ChannelApiResult.rejected("401001", "库存不足", null).isAuthError());
      assertFalse(ChannelApiResult.rejected("1401", "订单状态不允许", null).isAuthError());
      assertFalse(ChannelApiResult.rejected("4010", "余额不足", null).isAuthError());
      assertFalse(ChannelApiResult.rejected("A401B", "业务编码恰好含 401", null).isAuthError());
    }

    @Test
    @DisplayName("普通业务拒绝不得被误判（否则运维会去刷令牌，真问题被掩盖）")
    void doesNotMisjudgePlainBusinessRejection() {
      assertFalse(ChannelApiResult.rejected("50008", "渠道内部错误", null).isAuthError());
      assertFalse(ChannelApiResult.rejected("30001", "商品已下架", null).isAuthError());
      assertFalse(ChannelApiResult.rejected("", "", null).isAuthError());
    }

    @Test
    @DisplayName("成功结果永不判为鉴权错误（success 前置短路）")
    void successIsNeverAuthError() {
      assertFalse(ChannelApiResult.ok(Map.of(), null).isAuthError());
      assertFalse(ChannelApiResult.empty(null).isAuthError());
    }

    @Test
    @DisplayName("英文文案也能识别（原实现只匹配大写英文，中文平台文案会漏判）")
    void detectsEnglishMessages() {
      assertTrue(ChannelApiResult.rejected("X", "invalid signature", null).isAuthError());
      assertTrue(ChannelApiResult.rejected("X", "token expired", null).isAuthError());
      assertTrue(ChannelApiResult.rejected("X", "unauthorized access", null).isAuthError());
    }
  }
}
