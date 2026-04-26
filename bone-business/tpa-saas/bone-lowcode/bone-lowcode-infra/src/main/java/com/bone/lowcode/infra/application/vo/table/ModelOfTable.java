package com.bone.lowcode.infra.application.vo.table;

import com.bone.lowcode.infra.application.vo.table.FieldOfTable;
import lombok.Data;

import java.util.List;

@Data
public class ModelOfTable {

    private String id;

    private String title;

    private Byte used;

    private List<FieldOfTable> tableFields;
}
