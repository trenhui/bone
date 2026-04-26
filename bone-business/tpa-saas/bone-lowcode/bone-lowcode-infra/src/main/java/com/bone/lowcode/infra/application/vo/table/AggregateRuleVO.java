package com.bone.lowcode.infra.application.vo.table;

import com.bone.lowcode.infra.application.vo.field.GroupAggregateField;
import lombok.Data;

import java.util.List;

@Data
public class AggregateRuleVO {

    private String id;

    private String tableId;

    private String name;

    private List<GroupAggregateField> fieldList;
}
