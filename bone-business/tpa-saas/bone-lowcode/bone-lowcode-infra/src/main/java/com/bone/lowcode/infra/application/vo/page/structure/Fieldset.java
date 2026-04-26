package com.bone.lowcode.infra.application.vo.page.structure;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class Fieldset {
    private String level = "fieldset";

    private String id;

    private String name;

    private Integer sequence;

    private List<Model> modelList = new ArrayList<>();
}
