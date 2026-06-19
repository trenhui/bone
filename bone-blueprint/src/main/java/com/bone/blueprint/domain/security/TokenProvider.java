package com.bone.blueprint.domain.security;

public interface TokenProvider {
  String createToken(String username);

  String getUsername(String token);

  boolean validateToken(String token);
}
