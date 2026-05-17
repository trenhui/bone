package com.bone.engine.extension.studio.controller;

import com.bone.core.model.ApiResponse;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

/** Location / ETag（version）辅助。 */
public final class StudioHttpSupport {

    private StudioHttpSupport() {}

    public static String resourceLocation(String collection, Long id) {
        return "/api/v1/extension/" + collection + "/" + id;
    }

    public static String operationLocation(String operationId) {
        return "/api/v1/extension/operations/" + operationId;
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(String location, String message, T data) {
        ApiResponse<T> body = ApiResponse.success(message, data);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, location)
                .eTag(etag(data))
                .body(body);
    }

    public static <T> ResponseEntity<ApiResponse<T>> accepted(String location, String message, T data) {
        ApiResponse<T> body = ApiResponse.success(message, data);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header(HttpHeaders.LOCATION, location)
                .body(body);
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        ApiResponse<T> body = ApiResponse.success(message, data);
        return ResponseEntity.ok().eTag(etag(data)).body(body);
    }

    public static String etag(Object entity) {
        if (entity == null) {
            return null;
        }
        Integer version = null;
        if (entity instanceof com.bone.engine.extension.studio.domain.model.ExtPoint point) {
            version = point.getVersion();
        } else if (entity instanceof com.bone.engine.extension.studio.domain.model.Extension extension) {
            version = extension.getVersion();
        }
        return version != null ? "\"v" + version + "\"" : null;
    }

    public static Optional<Integer> parseIfMatchVersion(String ifMatch) {
        if (!StringUtils.hasText(ifMatch)) {
            return Optional.empty();
        }
        String token = ifMatch.trim();
        if (token.startsWith("W/")) {
            token = token.substring(2).trim();
        }
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
            token = token.substring(1, token.length() - 1);
        }
        if (token.startsWith("v") || token.startsWith("V")) {
            token = token.substring(1);
        }
        try {
            return Optional.of(Integer.parseInt(token));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
