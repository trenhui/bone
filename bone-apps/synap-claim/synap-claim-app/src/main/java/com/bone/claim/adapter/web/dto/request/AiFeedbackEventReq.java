package com.bone.claim.adapter.web.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// AI 回流事件入站契约。刻意用 @JsonAnySetter 收集一切未登记字段：
// 「AI 不碰钱」不能只靠前端自律，服务端必须能看见并拒绝 amount / decision 之类的夹带。
@Getter
@Setter
@NoArgsConstructor
public class AiFeedbackEventReq {
  private String eventId;
  private String eventType;
  private String traceId;
  private String involvement;
  private String scene;
  private String disposition;
  private String reasonCode;
  private String comment;
  private List<String> modifiedFields;

  private Map<String, Object> unknownFields = new LinkedHashMap<>();

  @JsonAnySetter
  public void captureUnknown(String key, Object value) {
    unknownFields.put(key, value);
  }

  @JsonIgnore
  public Map<String, Object> getUnknownFields() {
    return unknownFields;
  }
}
