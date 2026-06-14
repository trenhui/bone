package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import java.util.Map;
import org.springframework.stereotype.Component;

/** FTP 连接器：未实现前显式失败（INT-02）。 */
@Component("FTP")
public class FtpClientImpl implements ExternalSystemClient {

  private static final String DETAIL = "连接器尚未实现";

  @Override
  public boolean testConnection(Map<String, Object> config) {
    String host = (String) config.get("host");
    if (host == null || host.isBlank()) {
      return false;
    }
    throw ConnectorClientSupport.notImplemented("FTP", DETAIL);
  }

  @Override
  public Object sendRequest(
      String endpoint, Map<String, Object> params, Map<String, Object> config) {
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("操作类型不能为空");
    }
    throw ConnectorClientSupport.notImplemented("FTP", DETAIL);
  }

  @Override
  public String getType() {
    return "FTP";
  }
}
