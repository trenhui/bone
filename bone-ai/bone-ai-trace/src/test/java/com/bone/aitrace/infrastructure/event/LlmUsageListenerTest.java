package com.bone.aitrace.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.event.LlmUsageRecorded;
import com.bone.aitrace.domain.gateway.LlmUsageSink;
import com.bone.aitrace.domain.gateway.LlmUsageSink.LlmUsageRecord;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 计量消费（spend tracking 消费端）：字段映射 + traceId 关联 + 失败不阻断。 */
class LlmUsageListenerTest {

  private final LlmUsageSink sink = mock(LlmUsageSink.class);
  private final LlmUsageListener listener = new LlmUsageListener(sink);

  @Test
  void maps_event_fields_including_trace_id() {
    listener.onLlmUsageRecorded(
        new LlmUsageRecorded("42", "m", new LlmUsage(10, 20, 30), "trace-1", LocalDateTime.now()));

    ArgumentCaptor<LlmUsageRecord> captor = ArgumentCaptor.forClass(LlmUsageRecord.class);
    verify(sink).record(captor.capture());
    LlmUsageRecord record = captor.getValue();
    assertThat(record.tenantId()).isEqualTo("42");
    assertThat(record.model()).isEqualTo("m");
    assertThat(record.promptTokens()).isEqualTo(10);
    assertThat(record.completionTokens()).isEqualTo(20);
    assertThat(record.totalTokens()).isEqualTo(30);
    // 计量与证据链同一 traceId：「这笔 token 属于哪次业务动作」的对账依据
    assertThat(record.traceId()).isEqualTo("trace-1");
  }

  @Test
  void null_usage_maps_to_zero_tokens() {
    listener.onLlmUsageRecorded(new LlmUsageRecorded("42", "m", null, null, LocalDateTime.now()));

    ArgumentCaptor<LlmUsageRecord> captor = ArgumentCaptor.forClass(LlmUsageRecord.class);
    verify(sink).record(captor.capture());
    LlmUsageRecord record = captor.getValue();
    assertThat(record.promptTokens()).isZero();
    assertThat(record.completionTokens()).isZero();
    assertThat(record.totalTokens()).isZero();
    assertThat(record.traceId()).isNull();
  }

  @Test
  void sink_failure_does_not_break_event_flow() {
    org.mockito.Mockito.doThrow(new IllegalStateException("sink down"))
        .when(sink)
        .record(org.mockito.ArgumentMatchers.any());

    // 计量是记账不是合规留痕：消费失败不得让事件流（业务调用链）抛错
    assertThatCode(
            () ->
                listener.onLlmUsageRecorded(
                    new LlmUsageRecorded(
                        "42", "m", new LlmUsage(1, 1, 2), "t", LocalDateTime.now())))
        .doesNotThrowAnyException();
  }
}
