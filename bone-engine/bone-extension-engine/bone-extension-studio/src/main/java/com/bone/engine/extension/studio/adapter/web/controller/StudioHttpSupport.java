package com.bone.engine.extension.studio.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.engine.extension.studio.application.service.StudioCommandResponses;
import java.util.Optional;
import org.springframework.http.ResponseEntity;

/** 适配层委托至 {@link StudioCommandResponses}。 */
public final class StudioHttpSupport {

  private StudioHttpSupport() {}

  public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
    return StudioCommandResponses.ok(message, data);
  }

  public static Optional<Integer> parseIfMatchVersion(String ifMatch) {
    return StudioCommandResponses.parseIfMatchVersion(ifMatch);
  }
}
