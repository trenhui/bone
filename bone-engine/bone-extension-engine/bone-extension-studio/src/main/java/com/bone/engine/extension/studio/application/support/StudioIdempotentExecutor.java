package com.bone.engine.extension.studio.application.support;

import com.bone.core.model.ApiResponse;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** 幂等执行器：replay → action → remember。 */
@Component
@RequiredArgsConstructor
public class StudioIdempotentExecutor {

  private final StudioIdempotencySupport idempotencyService;

  public <T> ResponseEntity<ApiResponse<T>> execute(
      String idempotencyKey,
      String method,
      String path,
      String fingerprint,
      Supplier<ResponseEntity<ApiResponse<T>>> action) {
    Optional<ResponseEntity<ApiResponse<T>>> replay =
        idempotencyService.replay(idempotencyKey, method, path, fingerprint);
    if (replay.isPresent()) {
      return replay.get();
    }
    ResponseEntity<ApiResponse<T>> response = action.get();
    idempotencyService.remember(idempotencyKey, method, path, fingerprint, response);
    return response;
  }

  public <T> Optional<ResponseEntity<ApiResponse<T>>> tryReplay(
      String idempotencyKey, String method, String path, String fingerprint) {
    return idempotencyService.replay(idempotencyKey, method, path, fingerprint);
  }
}
