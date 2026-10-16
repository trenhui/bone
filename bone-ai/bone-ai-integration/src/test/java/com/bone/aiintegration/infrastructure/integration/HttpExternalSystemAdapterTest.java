package com.bone.aiintegration.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.aiintegration.domain.model.ExternalCall;
import com.bone.aiintegration.domain.model.ExternalCallOutcome;
import com.bone.aiintegration.domain.model.IntegrationMode;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class HttpExternalSystemAdapterTest {

  private final HttpClient client = mock(HttpClient.class);
  private final HttpExternalSystemAdapter adapter = new HttpExternalSystemAdapter(client);

  private ExternalCall call(IntegrationMode mode, String endpoint) {
    return new ExternalCall("c1", 42L, "SYS", endpoint, "{\"a\":1}", mode, "corr-1");
  }

  @Test
  void shadow_appends_dry_run_flag() {
    ExternalCall shadow = call(IntegrationMode.SHADOW, "https://ext/api");
    ExternalCall normal = call(IntegrationMode.AUTOMATED, "https://ext/api?x=1");
    assertThat(adapter.resolveUri(shadow)).isEqualTo("https://ext/api?dryRun=true");
    assertThat(adapter.resolveUri(normal)).isEqualTo("https://ext/api?x=1");
  }

  @Test
  void builds_post_request_with_payload() {
    HttpRequest req =
        adapter.buildRequest(call(IntegrationMode.AUTOMATED, "https://ext/api"), "https://ext/api");
    assertThat(req.method()).isEqualTo("POST");
    assertThat(req.uri().toString()).isEqualTo("https://ext/api");
    assertThat(req.headers().firstValue("Content-Type").orElse("")).contains("application/json");
  }

  @Test
  void call_returns_outcome_on_success() throws Exception {
    HttpResponse<String> resp = mock(HttpResponse.class);
    when(resp.statusCode()).thenReturn(200);
    when(resp.body()).thenReturn("{\"ok\":true}");
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

    ExternalCallOutcome o = adapter.call(call(IntegrationMode.AUTOMATED, "https://ext/api"));
    assertThat(o.httpStatus()).isEqualTo(200);
    assertThat(o.body()).isEqualTo("{\"ok\":true}");
    assertThat(o.error()).isNull();
  }

  @Test
  void call_records_error_on_failure() throws Exception {
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new java.io.IOException("boom"));
    ExternalCallOutcome o = adapter.call(call(IntegrationMode.AUTOMATED, "https://ext/api"));
    assertThat(o.httpStatus()).isEqualTo(0);
    assertThat(o.error()).contains("boom");
  }
}
