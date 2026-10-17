package com.bone.ai.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmValueObjectsTest {

  @Test
  void records_are_value_objects() {
    LlmMessage m1 = new LlmMessage("user", "hi");
    LlmMessage m2 = LlmMessage.user("hi");
    assertThat(m1).isEqualTo(m2).hasSameHashCodeAs(m2);
    assertThat(m1.toString()).contains("user").contains("hi");

    LlmUsage u = new LlmUsage(1, 2, 3);
    assertThat(u).isEqualTo(new LlmUsage(1, 2, 3));
    assertThat(u.totalTokens()).isEqualTo(3);
    assertThat(LlmUsage.empty().totalTokens()).isZero();

    LlmResponse r = new LlmResponse("c", "m", "stop", u);
    assertThat(r).isEqualTo(new LlmResponse("c", "m", "stop", u));
    assertThat(r.content()).isEqualTo("c");
  }

  @Test
  void request_defensively_copies_messages_and_marks_short_circuit() {
    List<LlmMessage> src = new ArrayList<>(List.of(LlmMessage.user("y")));
    LlmRequest req = new LlmRequest("m", src, null, null, false, false, "trace-1");
    src.add(LlmMessage.user("z"));
    assertThat(req.messages()).containsExactly(LlmMessage.user("y"));
    assertThat(req.traceId()).isEqualTo("trace-1");

    LlmRequest base = LlmRequest.of("m", List.of(LlmMessage.user("x")));
    assertThat(base.shortCircuited()).isFalse();
    assertThat(base.withRuleShortCircuit().shortCircuited()).isTrue();
    assertThat(base.effectiveTraceId()).isNotBlank(); // 缺省生成，保证单次调用最低可追溯
    assertThat(base.withTraceId("t-9").traceId()).isEqualTo("t-9");
  }
}
