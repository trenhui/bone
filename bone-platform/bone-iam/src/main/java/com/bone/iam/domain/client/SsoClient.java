package com.bone.iam.domain.client;

public interface SsoClient {
  boolean authenticate(String username, String password);

  String getRedirectUrl();

  String processCallback(String code);
}
