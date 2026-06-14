package com.bone.engine.extension.support.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtensionCodeResolverTest {

  @Test
  void resolvesFromNameThenClass() {
    assertEquals("CUSTOM", ExtensionCodeResolver.resolve("CUSTOM", "com.foo.Bar", 1L));
    assertEquals("EXT_Bar", ExtensionCodeResolver.resolve(null, "com.foo.Bar", 1L));
  }

  @Test
  void alignsWithRuntimeCode() {
    assertTrue(ExtensionCodeResolver.alignsWithRuntime("MY", "com.foo.X", "MY"));
    assertTrue(
        ExtensionCodeResolver.alignsWithRuntime(
            null, "com.foo.OutpatientClaim", "EXT_OutpatientClaim"));
  }
}
