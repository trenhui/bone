package com.bone.ai.gateway.infrastructure.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigDrivenModelRouterTest {

  private final ConfigDrivenModelRouter router =
      new ConfigDrivenModelRouter(
          Map.of("claim-assessor", "qwen-turbo", "policy-qa", "qwen-flash"));

  @Test
  void maps_scenario_to_configured_model() {
    assertThat(router.resolveModel("claim-assessor", "requested")).isEqualTo("qwen-turbo");
    assertThat(router.resolveModel("policy-qa", "requested")).isEqualTo("qwen-flash");
  }

  @Test
  void unmapped_scenario_falls_back_to_requested() {
    assertThat(router.resolveModel("unknown-scene", "requested")).isEqualTo("requested");
  }

  @Test
  void blank_or_null_scenario_falls_back_to_requested() {
    assertThat(router.resolveModel(null, "requested")).isEqualTo("requested");
    assertThat(router.resolveModel("", "requested")).isEqualTo("requested");
    assertThat(router.resolveModel("  ", "requested")).isEqualTo("requested");
  }

  @Test
  void hasRoute_reflects_configuration() {
    assertThat(router.hasRoute("claim-assessor")).isTrue();
    assertThat(router.hasRoute("nope")).isFalse();
    assertThat(router.hasRoute(null)).isFalse();
  }

  @Test
  void null_map_is_treated_as_no_routing() {
    ConfigDrivenModelRouter empty = new ConfigDrivenModelRouter(null);
    assertThat(empty.resolveModel("claim-assessor", "requested")).isEqualTo("requested");
  }
}
