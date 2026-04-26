package com.bone.lowcode.infra.application.vo.table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldSimpleInfo {

    private String id;

    private String bizCode;

    private String bizName;

    private String dataBinding;

    private String componentType;

    public FieldSimpleInfo(String id, String bizCode, String bizName, String dataBinding) {
        this.id = id;
        this.bizCode = bizCode;
        this.bizName = bizName;
        this.dataBinding = dataBinding;
    }
}
