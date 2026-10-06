package com.bone.blueprint.infrastructure.extension.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.domain.extension.channel.ChannelShipmentContext;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiRequest;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelApiResult;
import com.bone.blueprint.infrastructure.channel.openapi.ChannelOpenApiClient;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 发货回传（{@code ackOrder}）的守门测试。
 *
 * <p><b>为什么这组测试是必须的</b>：修复前 {@code TaobaoOrderExtension#ackOrder} 把承运商与运单号<b>硬编码</b>成 {@code
 * company_name="SF" / tracking_no="SF0000000000"}，而上游 {@code ackToChannel} 只传渠道订单号。
 * 后果是：<b>无论真实发什么货，回传给渠道的都是同一串假运单号</b>——渠道侧展示的物流轨迹与实际不符、
 * 用户收不到货，而本地日志显示「回传成功」。这类缺陷不抛异常、不影响启动，只在用户收货时暴露， 等发现时通常已被渠道判为虚假发货并罚款。
 *
 * <p><b>契约层已同步收口</b>：入参从 {@code ChannelOrderContext} 改为 {@link ChannelShipmentContext}
 * （它带真实承运商与运单号），从<b>签名上</b>就杜绝伪造。
 */
class ChannelShipmentAckTest {

  private static ChannelShipmentContext ctx(String channelCode, String company, String trackingNo) {
    return new ChannelShipmentContext(1L, channelCode, "ORDER-1", "SF-INT-1", company, trackingNo);
  }

  private static ChannelOpenApiClient acceptingClient() {
    ChannelOpenApiClient client = mock(ChannelOpenApiClient.class);
    when(client.call(any())).thenReturn(ChannelApiResult.ok(Map.of(), "{}"));
    return client;
  }

  @Nested
  @DisplayName("物流公司编码映射（跨渠道收敛到一处）")
  class LogisticsCodeMapping {

    @ParameterizedTest(name = "{0} 的「{1}」→ {2}")
    @CsvSource({
      "TAOBAO, 顺丰速运, SF",
      "TAOBAO, SF, SF",
      "JD, 顺丰速运, JD_007",
      "JD, 中通快递, JD_002",
      "DOUYIN, 顺丰速运, 1",
      "PDD, 顺丰速运, PDD_SF",
    })
    @DisplayName("同一承运商在不同渠道得到不同编码（渠道编码体系互不兼容）")
    void mapsInternalCompanyToChannelCode(String channel, String company, String expected) {
      assertEquals(expected, ChannelLogisticsCodes.codeOf(channel, company));
    }

    @ParameterizedTest(name = "「{0}」未登记 → null（拒绝兜底）")
    @ValueSource(strings = {"德邦", "", "   ", "不存在的公司"})
    @DisplayName("未登记承运商返回 null 而非兜底成顺丰（兜底会让渠道按错误承运商投递）")
    void unknownCompanyYieldsNullInsteadOfFallback(String company) {
      assertNull(ChannelLogisticsCodes.codeOf("TAOBAO", company));
    }

    @Test
    @DisplayName("未知渠道码返回 null（不猜编码）")
    void unknownChannelYieldsNull() {
      assertNull(ChannelLogisticsCodes.codeOf("NEW_CHANNEL", "顺丰速运"));
    }
  }

  @Nested
  @DisplayName("淘宝发货回传（原硬编码假单号）")
  class TaobaoAck {

    @Test
    @DisplayName("承运商与运单号取自入参，回传给渠道的是真实单号")
    void sendsRealTrackingNo() {
      ChannelOpenApiClient client = acceptingClient();
      new TaobaoOrderExtension(client).ackOrder(ctx("TAOBAO", "顺丰速运", "SF1234567890"));

      var captor = org.mockito.ArgumentCaptor.forClass(ChannelApiRequest.class);
      verify(client).call(captor.capture());
      Map<String, String> params = captor.getValue().params();
      assertEquals("SF1234567890", params.get("tracking_no"), "运单号必须来自入参");
      assertEquals("SF", params.get("company_name"), "承运商应映射为淘宝编码");
    }

    @Test
    @DisplayName("缺运单号 ⇒ 拒绝回传且不出网（不伪造单号）")
    void refusesWhenTrackingNoMissing() {
      ChannelOpenApiClient client = acceptingClient();
      boolean ok = new TaobaoOrderExtension(client).ackOrder(ctx("TAOBAO", "顺丰速运", null));

      assertFalse(ok, "没有真实运单号时必须拒绝回传");
      verify(client, never()).call(any());
    }

    @Test
    @DisplayName("承运商未登记 ⇒ 拒绝回传（不把未知承运商兜底成顺丰）")
    void refusesWhenCompanyUnregistered() {
      ChannelOpenApiClient client = acceptingClient();
      boolean ok = new TaobaoOrderExtension(client).ackOrder(ctx("TAOBAO", "德邦", "DB123"));

      assertFalse(ok, "未登记承运商会让渠道按错误承运商投递，必须拒绝");
      verify(client, never()).call(any());
    }
  }

  @Nested
  @DisplayName("京东 / 抖音发货回传")
  class JdAndDouyinAck {

    @Test
    @DisplayName("京东回传携带京东侧物流编码与真实运单号")
    void jdSendsChannelCodeAndTrackingNo() {
      ChannelOpenApiClient client = acceptingClient();
      new JdOrderExtension(client).ackOrder(ctx("JD", "顺丰速运", "JD99001"));

      var captor = org.mockito.ArgumentCaptor.forClass(ChannelApiRequest.class);
      verify(client).call(captor.capture());
      assertEquals("JD_007", captor.getValue().params().get("companyCode"));
      assertEquals("JD99001", captor.getValue().params().get("waybillCode"));
    }

    @Test
    @DisplayName("抖音回传不再硬编码 DouyinExpress，改为按承运商映射")
    void douyinMapsLogisticsCode() {
      ChannelOpenApiClient client = acceptingClient();
      new DouyinOrderExtension(client).ackOrder(ctx("DOUYIN", "顺丰速运", "DY7788"));

      var captor = org.mockito.ArgumentCaptor.forClass(ChannelApiRequest.class);
      verify(client).call(captor.capture());
      Map<String, String> params = captor.getValue().params();
      assertEquals("1", params.get("logistics_id"), "顺丰在抖音的编码是 1");
      assertEquals("DY7788", params.get("tracking_no"));
    }

    @Test
    @DisplayName("京东缺运单号时拒绝出网")
    void jdRefusesWithoutTrackingNo() {
      ChannelOpenApiClient client = acceptingClient();
      assertFalse(new JdOrderExtension(client).ackOrder(ctx("JD", "顺丰速运", " ")));
      verify(client, never()).call(any());
    }
  }

  @Nested
  @DisplayName("拼多多（原恒成功）")
  class PddAck {

    @Test
    @DisplayName("拼多多无独立回传接口 ⇒ 显式返回 false（原实现恒返回 true）")
    void pddReportsFailureInsteadOfFakeSuccess() {
      ChannelOpenApiClient client = acceptingClient();
      boolean ok = new PddOrderExtension(client).ackOrder(ctx("PDD", "顺丰速运", "PDD1"));

      assertFalse(ok, "拼多多发货状态由添加物流驱动，此处恒 true 会让上游误认为渠道已受理");
      verify(client, never()).call(any());
    }
  }

  @Nested
  @DisplayName("渠道被拒绝时的失败语义")
  class RejectionSemantics {

    @Test
    @DisplayName("渠道显式拒绝 ⇒ 返回 false（本地能感知失败，不静默推进）")
    void returnsFalseWhenChannelRejects() {
      ChannelOpenApiClient client = mock(ChannelOpenApiClient.class);
      when(client.call(any())).thenReturn(ChannelApiResult.rejected("50008", "物流单号不存在", "{}"));

      assertFalse(new TaobaoOrderExtension(client).ackOrder(ctx("TAOBAO", "顺丰速运", "SF1")));
    }

    @Test
    @DisplayName("回传上下文本身应可读出承运商与运单号（契约收口的前提）")
    void shipmentContextCarvesLogisticsFields() {
      ChannelShipmentContext c = ctx("TAOBAO", "顺丰速运", "SF1234567890");
      assertEquals("顺丰速运", c.logisticsCompany());
      assertEquals("SF1234567890", c.trackingNo());
      assertNotNull(c.channelOrderNo());
    }
  }
}
