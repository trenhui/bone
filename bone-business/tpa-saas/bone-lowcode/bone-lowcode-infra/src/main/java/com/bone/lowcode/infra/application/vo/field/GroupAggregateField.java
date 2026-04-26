package com.bone.lowcode.infra.application.vo.field;

import lombok.Data;

@Data
public class GroupAggregateField {

    private String id;

    private String bizCode;

    private String bizName;

    private Integer sort;//分组聚合结果中的排序

    private Integer type;//分组聚合时的字段类型

    private String groupAggregateType;//聚合字段的聚合方式
}
