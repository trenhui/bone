package com.bone.lowcode.infra.application.vo.page;

import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EditableColumnVO {

    private FieldSimpleInfo field;

    private Byte singleLineEditable;//可单行编辑,0：否,1：是

    private Byte BatchEditable;//可批量编辑,0：否,1：是
}
