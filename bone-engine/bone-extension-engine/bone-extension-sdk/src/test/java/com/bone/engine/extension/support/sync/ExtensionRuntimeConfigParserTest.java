package com.bone.engine.extension.support.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtensionRuntimeConfigParserTest {

  @Test
  void parsesRoutingFieldsFromJson() {
    String json =
        """
                {"code":"MY_EXT","condition":"#x>1","traffic":50,"weight":80,"defaultImpl":true,"env":"prod"}
                """;
    ExtensionRuntimeConfig config = ExtensionRuntimeConfigParser.parse(json);
    assertEquals("MY_EXT", config.getCode());
    assertEquals("#x>1", config.getCondition());
    assertEquals(50, config.getTraffic());
    assertEquals(80, config.getWeight());
    assertTrue(config.getDefaultImpl());
    assertEquals("prod", config.getEnv());
  }
}
