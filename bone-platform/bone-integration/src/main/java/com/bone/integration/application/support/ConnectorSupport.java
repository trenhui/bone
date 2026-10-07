package com.bone.integration.application.support;

import com.bone.core.exception.BizException;
import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.common.IntegrationErrors;
import com.bone.integration.domain.client.ExternalSystemClient;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("applicationConnectorSupport")
@RequiredArgsConstructor
public class ConnectorSupport {
  private final ConnectorRepository connectorRepository;
  private final Map<String, ExternalSystemClient> externalSystemClients;
  private final ConnectorSecretSupport connectorSecretSupport;

  public boolean testConnector(Connector connector) {
    try {
      return resolveClient(connector).testConnection(realConfig(connector));
    } catch (RuntimeException ex) {
      throw invocationFailed(connector, ex);
    }
  }

  public Object executeConnector(Connector connector, String endpoint, Map<String, Object> params) {
    try {
      return resolveClient(connector).sendRequest(endpoint, params, realConfig(connector));
    } catch (RuntimeException ex) {
      throw invocationFailed(connector, ex);
    }
  }

  /**
   * 把协议客户端抛出的裸异常翻译成带稳定业务码的异常（502 Bad Gateway 语义）。
   *
   * <p><b>为何必须在这里翻译</b>（2026-10-07 实测）：各协议客户端 （{@code S3ClientImpl}/{@code RestClientImpl}/{@code
   * MongoClientImpl}…）统一抛 {@code IllegalStateException("S3 请求失败: " + ex.getMessage())}，若不在此拦住，
   * 它会一路冒泡到 {@code GlobalExceptionHandler} 的 {@code @ExceptionHandler(Exception.class)} 兜底 ⇒ 只剩
   * {@code COMMON_INTERNAL_ERROR}。 <b>这不是泄密</b>（兜底分支已脱敏，返回常量文案），<b>而是排障信息全丢</b>：
   * 客户端只见「系统异常」，监控无法按「哪个连接器的哪类故障」聚合。
   *
   * <p><b>为何 catch {@code RuntimeException} 而非 {@code IllegalStateException}</b>：
   * 不同协议客户端选用的异常类型不一（{@code IllegalStateException}/ {@code RuntimeException}/{@code
   * IllegalArgumentException} 都可能）， 在<b>调用边界</b>统一收口比在每个客户端里对齐异常类型更可靠 —— 新增协议客户端时即使抛别的异常也不会漏。
   *
   * <p><b>保留 cause</b>：原始异常进日志（{@code BizException} 会带上 cause）， 排障按连接器类型 + cause
   * 定位，符合错误码登记「不承载文案、只承载语义」的分工。
   */
  private BizException invocationFailed(Connector connector, RuntimeException ex) {
    return IntegrationErrors.of(
        IntegrationErrorCodes.CONNECTOR_INVOCATION_FAILED, connector.getType(), ex);
  }

  /**
   * 交给外部客户端的必须是**解密后**的真实凭据。
   *
   * <p>解密放在这一层（application）而不是 {@code S3ClientImpl}（infrastructure）：凭据的加解密是应用层
   * 策略，基础设施只该拿到「可用的明文」，不该知道密钥从哪来。
   */
  private Map<String, Object> realConfig(Connector connector) {
    return connectorSecretSupport.decryptForConsume(connector.getConfig());
  }

  private ExternalSystemClient resolveClient(Connector connector) {
    String type = connector.getType().name();
    ExternalSystemClient client = externalSystemClients.get(type);
    if (client == null && isHttpFamily(type)) {
      client = externalSystemClients.get("REST");
    }
    if (client == null) {
      throw IntegrationErrors.of(
          IntegrationErrorCodes.CONNECTOR_TYPE_UNSUPPORTED, connector.getType());
    }
    return client;
  }

  private static boolean isHttpFamily(String type) {
    return "HTTP".equals(type) || "HTTPS".equals(type);
  }

  public void validateConnectorName(String name, Long excludeId) {
    Connector existing = connectorRepository.findByName(name);
    if (existing != null && (excludeId == null || !excludeId.equals(existing.getId()))) {
      throw IntegrationErrors.of(IntegrationErrorCodes.CONNECTOR_NAME_CONFLICT, name);
    }
  }
}
