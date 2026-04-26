package com.bone.lowcode.infra.application.dto.table;

import com.bone.lowcode.infra.application.vo.field.GroupAggregateField;
import lombok.Data;

import java.util.List;

@Data
public class UpdateAggregateRuleDTO {
    private Long id;

    private String aggregateRuleName;

    private List<GroupAggregateField> groupAggregateFieldList;
}
