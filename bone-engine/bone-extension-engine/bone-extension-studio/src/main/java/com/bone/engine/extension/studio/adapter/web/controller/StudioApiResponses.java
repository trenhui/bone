package com.bone.engine.extension.studio.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.engine.extension.studio.application.support.StudioCommandResponses;
import org.springframework.http.ResponseEntity;

/** 适配层委托至 {@link StudioCommandResponses}。 */
public final class StudioApiResponses {

  private StudioApiResponses() {}

  public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
    return StudioCommandResponses.badRequest(message);
  }

  public static <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
    return StudioCommandResponses.notFound(message);
  }
}
