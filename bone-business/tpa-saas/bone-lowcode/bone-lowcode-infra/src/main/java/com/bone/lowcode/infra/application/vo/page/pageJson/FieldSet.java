package com.bone.lowcode.infra.application.vo.page.pageJson;

import com.bone.lowcode.infra.application.vo.Sequence;
import lombok.Data;

import java.util.List;

@Data
public class FieldSet implements Sequence {

    private String id;

    private String type;

    private String name;

    private Integer sequence;

    private List<Field> body;

    @Override
    public Integer returnSequence() {
        return sequence;
    }
}
