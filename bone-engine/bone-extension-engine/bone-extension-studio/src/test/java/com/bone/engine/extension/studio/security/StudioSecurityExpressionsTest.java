package com.bone.engine.extension.studio.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.studio.config.ExtensionStudioProperties;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** 5a G3：hasAnyScope 过渡期回退与切流（legacy-scope-fallback）语义。 */
class StudioSecurityExpressionsTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private StudioSecurityExpressions expressions(boolean fallback) {
    ExtensionStudioProperties props = new ExtensionStudioProperties();
    props.getSecurity().setPermitUnauthenticated(false);
    props.getSecurity().setLegacyScopeFallback(fallback);
    return new StudioSecurityExpressions(props);
  }

  private boolean check(boolean fallback, String[] scopes, String... authorities) {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "user", "n/a", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    SecurityContextHolder.getContext().setAuthentication(auth);
    return expressions(fallback).hasAnyScope(scopes);
  }

  @Test
  void fallbackTrue_acceptsLegacyScope() {
    assertTrue(
        check(
            true,
            new String[] {ExtensionScopes.RUNTIME_PUBLISH, ExtensionScopes.PLUGINS_DEPLOY},
            "extension:plugins:deploy"));
  }

  @Test
  void fallbackFalse_rejectsLegacyScope() {
    assertFalse(
        check(
            false,
            new String[] {ExtensionScopes.RUNTIME_PUBLISH, ExtensionScopes.PLUGINS_DEPLOY},
            "extension:plugins:deploy"));
  }

  @Test
  void fallbackFalse_acceptsPrimaryScope() {
    assertTrue(
        check(
            false,
            new String[] {ExtensionScopes.RUNTIME_PUBLISH, ExtensionScopes.PLUGINS_DEPLOY},
            "extension:runtime:publish"));
  }

  @Test
  void wildcardAuthorityAlwaysAccepted() {
    assertTrue(
        check(
            false,
            new String[] {ExtensionScopes.RUNTIME_PUBLISH, ExtensionScopes.PLUGINS_DEPLOY},
            "*"));
  }

  @Test
  void unauthenticatedRejectedWhenNotPermitted() {
    SecurityContextHolder.clearContext();
    assertFalse(
        expressions(true).hasAnyScope(ExtensionScopes.OBSERVE_READ, ExtensionScopes.POINTS_READ));
  }
}
