package com.bone.studio.generator.application.dto;

import java.util.Map;

/** LRO 操作视图（对齐 Bone-API-规范 §7；operationId = taskId）。 */
public class GeneratorOperationView {

  private String operationId;
  private String type;
  private boolean done;
  private int progress;
  private Map<String, Object> result;
  private Map<String, Object> error;

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

  public Map<String, Object> getError() {
    return error;
  }

  public void setError(Map<String, Object> error) {
    this.error = error;
  }
}
