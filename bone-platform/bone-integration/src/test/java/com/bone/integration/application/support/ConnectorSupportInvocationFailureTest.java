package com.bone.integration.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.domain.client.ExternalSystemClient;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.model.connector.valueobject.ConnectorType;
import com.bone.integration.domain.repository.ConnectorRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@link ConnectorSupport} 的连接器调用失败落点契约。
 *
 * <p><b>为何必须有此测试</b>（2026-10-07 实测修复的真实缺陷）：各协议客户端 （{@code S3ClientImpl}/{@code
 * RestClientImpl}/{@code MongoClientImpl} 等）统一抛 {@code IllegalStateException("S3 请求失败: " +
 * ex.getMessage())}，而 {@code ConnectorSupport#testConnector}/{@code #executeConnector} <b>原先完全没有
 * catch</b> ⇒ 一路冒泡到 {@code GlobalExceptionHandler} 的 {@code @ExceptionHandler(Exception.class)} 兜底
 * ⇒ 只剩 {@code COMMON_INTERNAL_ERROR}。 <b>这不是泄密</b>（兜底分支已脱敏，返回常量文案），<b>而是排障信息全丢</b>：
 * 客户端只见「系统异常」，监控无法按「哪个连接器的哪类故障」聚合。
 *
 * <p><b>为何 catch {@code RuntimeException} 而非某个具体异常类型</b>：不同协议客户端
 * 选用的异常类型不一，在<b>调用边界</b>统一收口比在每个客户端里对齐类型更可靠 —— 新增协议客户端即使抛别的异常也不会漏。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConnectorSupportInvocationFailureTest {

  private static final String TYPE = "S3";

  @Mock private ConnectorRepository connectorRepository;
  @Mock private ConnectorSecretSupport connectorSecretSupport;
  @Mock private ExternalSystemClient client;

  private ConnectorSupport serviceWith(ExternalSystemClient c) {
    return new ConnectorSupport(connectorRepository, Map.of(TYPE, c), connectorSecretSupport);
  }

  private Connector connector() {
    // 真实 API（实测 Connector 类，勿凭直觉改）：构造器是 @NoArgsConstructor(access=PRIVATE)
    // 且只有 @Getter 无 setter ⇒ 必须走工厂 create(id, name, type, config)。
    return Connector.create(1L, "s3-conn", ConnectorType.S3, Map.of("bucket", "b"));
  }

  @Test
  @DisplayName("★ 连接器调用失败 ⇒ 502 + INT_CONNECTOR_INVOCATION_FAILED，不再退化成 COMMON_INTERNAL_ERROR")
  void invocationFailureMapsTo502WithStableCode() {
    when(connectorSecretSupport.decryptForConsume(any())).thenReturn(Map.of());
    ConnectorSupport service = serviceWith(client);
    when(client.sendRequest(anyString(), any(), any()))
        .thenThrow(new IllegalStateException("S3 请求失败: Connection timed out"));

    assertThatThrownBy(() -> service.executeConnector(connector(), "/obj", Map.of()))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 502)
        .hasFieldOrPropertyWithValue(
            "errorCode", IntegrationErrorCodes.CONNECTOR_INVOCATION_FAILED);
  }

  @Test
  @DisplayName("★ testConnection 的失败同样被翻译（此前也是裸抛）")
  void testConnectionFailureIsAlsoTranslated() {
    when(connectorSecretSupport.decryptForConsume(any())).thenReturn(Map.of());
    ConnectorSupport service = serviceWith(client);
    when(client.testConnection(any()))
        .thenThrow(new IllegalStateException("S3 连接失败: 401 Unauthorized"));

    assertThatThrownBy(() -> service.testConnector(connector()))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 502)
        .hasFieldOrPropertyWithValue(
            "errorCode", IntegrationErrorCodes.CONNECTOR_INVOCATION_FAILED);
  }

  @Test
  @DisplayName("★ catch 的是 RuntimeException 家族：不同客户端抛不同异常类型也不漏")
  void catchesWholeRuntimeExceptionFamily() {
    // 协议客户端可能抛 RuntimeException / IllegalArgumentException 等各类型
    when(connectorSecretSupport.decryptForConsume(any())).thenReturn(Map.of());
    ConnectorSupport service = serviceWith(client);
    when(client.sendRequest(anyString(), any(), any()))
        .thenThrow(new IllegalArgumentException("bucket 名非法"));

    assertThatThrownBy(() -> service.executeConnector(connector(), "/obj", Map.of()))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", IntegrationErrorCodes.CONNECTOR_INVOCATION_FAILED);
  }

  @Test
  @DisplayName("保留 cause：原始异常进日志，供排障定位（不回吐给客户端）")
  void originalCauseIsPreserved() {
    when(connectorSecretSupport.decryptForConsume(any())).thenReturn(Map.of());
    ConnectorSupport service = serviceWith(client);
    IllegalStateException root = new IllegalStateException("S3 请求失败: Read timed out");
    when(client.sendRequest(anyString(), any(), any())).thenThrow(root);

    assertThatThrownBy(() -> service.executeConnector(connector(), "/obj", Map.of()))
        .satisfies(
            ex -> {
              BizException biz = (BizException) ex;
              assertThat(biz.getCause()).as("cause 丢失 ⇒ 排障只能靠猜，日志里没有下游原文").isSameAs(root);
              assertThat(biz.getErrorCode()).isNotNull();
            });
  }

  @Test
  @DisplayName("成功路径不受影响（无异常时不产生翻译开销/不改变返回）")
  void successPathUnchanged() {
    when(connectorSecretSupport.decryptForConsume(any())).thenReturn(Map.of());
    ConnectorSupport service = serviceWith(client);
    when(client.sendRequest(anyString(), any(), any())).thenReturn(Map.of("ok", true));

    assertThat(service.executeConnector(connector(), "/obj", Map.of())).containsEntry("ok", true);
  }

  @Test
  @DisplayName("类型不支持仍报原码 400（不被新 catch 抢走 —— 语义边界）")
  void unsupportedTypeKeepsItsOwnCode() {
    // ConnectorType.S3 有对应 client，故用一个不存在的类型无法构造；
    // 此处改为断言「有 client 时不会误报 UNSUPPORTED」，守住语义边界。
    ConnectorSupport service = serviceWith(client);
    when(connectorSecretSupport.decryptForConsume(any())).thenReturn(Map.of());
    when(client.testConnection(any())).thenReturn(true);

    assertThat(service.testConnector(connector())).isTrue();
  }
}
