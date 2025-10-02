package com.bone.lowcode.integration.core.log;

import lombok.Data;

import java.io.Serializable;

@Data
public class LogEntity implements Serializable {
    private String debugConnId;
    private String content;

    public LogEntity(String debugConnId, String content) {
        this.debugConnId = debugConnId;
        this.content = content;
    }
}
