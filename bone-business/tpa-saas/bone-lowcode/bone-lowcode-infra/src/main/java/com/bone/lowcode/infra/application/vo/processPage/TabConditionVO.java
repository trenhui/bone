package com.bone.lowcode.infra.application.vo.processPage;

import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TabConditionVO {

    private String title;

    private FieldSimpleInfo field;

    private String fieldValue;
}
