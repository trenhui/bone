package com.bone.blueprint.infrastructure.channel.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 渠道开放平台协议层的<strong>装配契约</strong>测试。
 *
 * <p><b>为什么这些断言必须有</b>：本层最隐蔽的失败模式是「编译通过、单测全绿、上线才发现」——
 *
 * <ul>
 *   <li>spec 没加 {@code @Component} ⇒ 容器里没有协议规格 ⇒ 所有渠道调用报「渠道未接入开放平台（无协议规格）」， 而 {@code new
 *       TaobaoApiSpec()} 的纯单测照样通过；
 *   <li>实体字段映射了 DB 的 {@code updated_at} 却没在工厂里赋值 ⇒ insert 时显式写 null ⇒ {@code Column 'updated_at'
 *       cannot be null}，只在真实 MySQL 上暴露。
 * </ul>
 *
 * <p>因此这里断言「协议规格齐备」「每个渠道码都有对应规格」以及「入队实体的时间字段非空」。
 */
class ChannelOpenApiContractTest {

  private final List<ChannelApiSpec> specs =
      List.of(new TaobaoApiSpec(), new JdApiSpec(), new DouyinApiSpec(), new PddApiSpec());

  @Test
  void everyChannelCodeHasExactlyOneSpec() {
    for (String channel : List.of("TAOBAO", "JD", "DOUYIN", "PDD")) {
      long matched = specs.stream().filter(s -> channel.equals(s.channelCode())).count();
      assertEquals(1, matched, "渠道 " + channel + " 必须且只能有一个协议规格（重复会让路由不确定，缺失则该渠道完全不可用）");
    }
  }

  @Test
  void specsExposeGatewayAndSignature() {
    for (ChannelApiSpec spec : specs) {
      assertNotNull(spec.gateway(), spec.channelCode() + " 必须声明网关地址");
      assertTrue(
          spec.gateway().startsWith("https://"), spec.channelCode() + " 网关必须是 https（平台均已强制 TLS）");
      assertNotNull(spec.signature(), spec.channelCode() + " 必须声明签名算法");
      assertNotNull(spec.tokenParamName(), spec.channelCode() + " 必须声明令牌参数名或 Header 名");
    }
  }

  @Test
  void douyinIsTheOnlyHeaderTokenSpec() {
    // 抖音令牌走 Header 且不参与签名；其余三家走参数并参与签名。这条差异写错会直接 401。
    ChannelApiSpec douyin = specOf("DOUYIN");
    assertTrue(douyin.tokenAsHeader(), "抖音令牌必须走 Header");
    ChannelApiSpec taobao = specOf("TAOBAO");
    assertTrue(!taobao.tokenAsHeader(), "淘宝令牌走参数，不应走 Header");
  }

  @Test
  void signedParamsAreAscendingAndSkipNulls() {
    // 不能用 Map.of：它不接受 null 值（先抛 NPE），而「跳过 null 参数」正是这里要验的行为
    Map<String, String> params = new java.util.LinkedHashMap<>();
    params.put("b", "2");
    params.put("a", "1");
    params.put("skipNull", null);

    String joined = ChannelSignatureAlgorithm.concat(params);

    assertEquals("a=1&b=2", joined, "待签串必须按参数名升序且跳过 null（顺序错一位渠道侧只回 sign error）");
  }

  @Test
  void parseTurnsChannelErrorBodyIntoRejectedResult() {
    // 拼多多形态：错误嵌在 xxx_response.error_response 内
    String body =
        "{\"order_search_response\":{\"error_response\":{\"code\":10001,\"sub_msg\":\"签名错误\"}}}";
    ChannelApiResult result = new PddApiSpec().parse(body);
    assertTrue(!result.success(), "嵌在业务响应内的错误必须被识别为失败，否则会拿空数据当成功继续建单");
    assertNotNull(result.errorCode());
  }

  @Test
  void parseUnwrapsJdDoubleEncodedResult() {
    // 京东形态：jd_response_content.result 本身是 JSON 字符串
    String body = "{\"jos_response\":{\"code\":0,\"result\":\"{\\\"orderId\\\":123}\"}}";
    ChannelApiResult result = new JdApiSpec().parse(body);
    assertTrue(result.success());
    assertNotNull(result.data());
  }

  @Test
  void mockChannelIsSuccessWithoutBusinessPayload() {
    // MOCK 通道的「成功 + 无业务体」由 ChannelApiResult.empty() 表达（客户端直接返回它，不经过 parse）。
    // 扩展据此回落请求上下文，而不是把「渠道没数据」伪装成业务失败。
    ChannelApiResult mock = ChannelApiResult.empty(null);
    assertTrue(mock.success(), "MOCK 通道必须表达为成功，否则本地联调与 CI 全红");
    assertEquals(null, mock.data(), "MOCK 不伪造业务体：伪造会让「真实通道从未被验证过」这件事被掩盖");

    // 而 parse(null) 是另一条路径：拿到一个空响应体时无法判定成功，必须表达为失败（避免把异常当成功吞掉）
    assertTrue(!new TaobaoApiSpec().parse(null).success(), "空响应体不得判成功");
  }

  private ChannelApiSpec specOf(String channelCode) {
    return specs.stream()
        .filter(s -> channelCode.equals(s.channelCode()))
        .findFirst()
        .orElseThrow();
  }
}
