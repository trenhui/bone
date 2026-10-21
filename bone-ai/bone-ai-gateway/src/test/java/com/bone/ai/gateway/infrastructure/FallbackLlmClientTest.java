package com.bone.ai.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FallbackLlmClientTest {

  private final LlmClient primary = mock(LlmClient.class);
  private final LlmClient fallback = mock(LlmClient.class);
  private final FallbackLlmClient client = new FallbackLlmClient(primary, fallback, "qwen-flash");

  private final LlmRequest request = LlmRequest.of("qwen-max", List.of(LlmMessage.user("q")), "t");

  @Test
  void primary_success_skips_fallback() {
    LlmResponse ok = new LlmResponse("primary", "qwen-max", "stop", LlmUsage.empty());
    when(primary.chat(request)).thenReturn(ok);

    assertThat(client.chat(request)).isSameAs(ok);
    verify(fallback, never()).chat(any());
  }

  @Test
  void primary_failure_falls_back_to_backup_model() {
    when(primary.chat(request)).thenThrow(new IllegalStateException("qwen-max down"));
    LlmResponse backupResp = new LlmResponse("backup", "qwen-flash", "stop", LlmUsage.empty());
    when(fallback.chat(any())).thenReturn(backupResp);

    LlmResponse out = client.chat(request);

    assertThat(out).isSameAs(backupResp);
    // 备用调用必须切到备用档位模型
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(fallback).chat(captor.capture());
    assertThat(captor.getValue().model()).isEqualTo("qwen-flash");
  }

  @Test
  void both_fail_rethrows_primary_failure() {
    when(primary.chat(request)).thenThrow(new IllegalStateException("primary root cause"));
    when(fallback.chat(any())).thenThrow(new IllegalStateException("backup also down"));

    assertThatThrownBy(() -> client.chat(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("primary root cause"); // 根因是主档位
  }
}
