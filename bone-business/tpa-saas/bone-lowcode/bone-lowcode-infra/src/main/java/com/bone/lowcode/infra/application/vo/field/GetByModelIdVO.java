package com.bone.lowcode.infra.application.vo.field;

import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import lombok.Data;

import java.util.List;

@Data
public class GetByModelIdVO {

    private String modelId;

    private String modelName;

    private List<FieldSimpleInfo> fieldList;
}
