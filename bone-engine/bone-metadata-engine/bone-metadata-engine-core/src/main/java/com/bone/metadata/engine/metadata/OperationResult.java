package com.bone.metadata.engine.metadata;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Operation execution result
 *
 * <p>Provides standardized operation result encapsulation, supporting success/failure status,
 * messages, data, error codes, and detailed information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationResult implements Serializable {
  private static final long serialVersionUID = 1L;

  private boolean success;
  private String message;
  private Object data;
  private String errorCode;
  private Map<String, Object> details = new HashMap<>();

  /** Creates a success result */
  public static OperationResult success() {
    OperationResult result = new OperationResult();
    result.setSuccess(true);
    return result;
  }

  /** Creates a success result with data and message */
  public static OperationResult success(Object data, String message) {
    OperationResult result = new OperationResult();
    result.setSuccess(true);
    result.setData(data);
    result.setMessage(message);
    return result;
  }

  /** Creates a success result with data only */
  public static OperationResult success(Object data) {
    OperationResult result = new OperationResult();
    result.setSuccess(true);
    result.setData(data);
    return result;
  }

  /** Creates a success result with message only */
  public static OperationResult successWithMessage(String message) {
    OperationResult result = new OperationResult();
    result.setSuccess(true);
    result.setMessage(message);
    return result;
  }

  /** Creates a failure result */
  public static OperationResult failure(String message) {
    OperationResult result = new OperationResult();
    result.setSuccess(false);
    result.setMessage(message);
    return result;
  }

  /** Creates a failure result with error code */
  public static OperationResult failure(String message, String errorCode) {
    OperationResult result = new OperationResult();
    result.setSuccess(false);
    result.setMessage(message);
    result.setErrorCode(errorCode);
    return result;
  }

  /** Adds detailed information */
  public OperationResult addDetail(String key, Object value) {
    this.details.put(key, value);
    return this;
  }

  /** Adds multiple detailed information items */
  public OperationResult addDetails(Map<String, Object> details) {
    if (details != null) {
      this.details.putAll(details);
    }
    return this;
  }

  /** Gets detailed information (returns an unmodifiable Map) */
  public Map<String, Object> getDetails() {
    return Collections.unmodifiableMap(this.details);
  }

  /** Sets detailed information */
  public void setDetails(Map<String, Object> details) {
    this.details = new HashMap<>(details != null ? details : Collections.emptyMap());
  }

  /** Clones the current result */
  public OperationResult clone() {
    OperationResult result = new OperationResult();
    result.setSuccess(this.success);
    result.setMessage(this.message);
    result.setData(this.data);
    result.setErrorCode(this.errorCode);
    result.setDetails(this.details);
    return result;
  }

  /** 设置操作是否成功 */
  public void setSuccess(boolean success) {
    this.success = success;
  }

  /** 设置消息 */
  public void setMessage(String message) {
    this.message = message;
  }

  /** 设置数据 */
  public void setData(Object data) {
    this.data = data;
  }

  /** 设置错误码 */
  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }
}
