package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import java.util.Map;
import org.springframework.stereotype.Component;

/** JDBC 连接器：未实现前显式失败（INT-03）。 */
@Component("JDBC")
public class JdbcClientImpl implements ExternalSystemClient {

  private static final String DETAIL = "连接器尚未实现";

  @Override
  public boolean testConnection(Map<String, Object> config) {
    String url = (String) config.get("url");
    if (url == null || url.isBlank()) {
      return false;
    }
    throw ConnectorClientSupport.notImplemented("JDBC", DETAIL);
  }

  @Override
  public Object sendRequest(
      String endpoint, Map<String, Object> params, Map<String, Object> config) {
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("SQL语句不能为空");
    }
    throw ConnectorClientSupport.notImplemented("JDBC", DETAIL);
  }

  @Override
  public String getType() {
    return "JDBC";
  }
}
