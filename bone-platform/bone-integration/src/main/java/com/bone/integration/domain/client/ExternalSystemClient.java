package com.bone.integration.domain.client;

import java.util.Map;

public interface ExternalSystemClient {
  boolean testConnection(Map<String, Object> config);

  Object sendRequest(String endpoint, Map<String, Object> params, Map<String, Object> config);

  String getType();
}
