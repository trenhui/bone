package com.bone.lowcode.infra.application.vo.upload;

import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import lombok.Data;

import java.util.List;

@Data
public class GroupFieldRuleVO {

    private List<FieldSimpleInfo> fieldList;

    private Boolean unique;

    private List<FieldSimpleInfo> fieldListA;

    private List<FieldSimpleInfo> fieldListB;
}
