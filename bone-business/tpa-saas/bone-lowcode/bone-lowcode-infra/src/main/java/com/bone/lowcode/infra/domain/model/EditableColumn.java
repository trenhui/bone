package com.bone.lowcode.infra.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EditableColumn {

    private String fieldId;

    private Byte singleLineEditable;//可单行编辑,0：否,1：是

    private Byte BatchEditable;//可批量编辑,0：否,1：是
}
