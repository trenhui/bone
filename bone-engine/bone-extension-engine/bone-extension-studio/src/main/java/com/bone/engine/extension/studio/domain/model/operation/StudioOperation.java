package com.bone.engine.extension.studio.domain.model.operation;

import com.bone.core.model.ProblemDetail;
import java.time.Instant;
import java.util.Map;

/** LRO 操作状态（Bone-API-规范 §7）。 */
public class StudioOperation {

  private String operationId;
  private String type;
  private Long resourceId;
  private boolean done;
  private int progress;
  private Map<String, Object> result;
  private ProblemDetail error;
  private Instant createdAt;
  private Instant completedAt;

  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(String operationId) {
    this.operationId = operationId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Long getResourceId() {
    return resourceId;
  }

  public void setResourceId(Long resourceId) {
    this.resourceId = resourceId;
  }

  public boolean isDone() {
    return done;
  }

  public void setDone(boolean done) {
    this.done = done;
  }

  public int getProgress() {
    return progress;
  }

  public void setProgress(int progress) {
    this.progress = progress;
  }

  public Map<String, Object> getResult() {
    return result;
  }

  public void setResult(Map<String, Object> result) {
    this.result = result;
  }

  public ProblemDetail getError() {
    return error;
  }

  public void setError(ProblemDetail error) {
    this.error = error;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
