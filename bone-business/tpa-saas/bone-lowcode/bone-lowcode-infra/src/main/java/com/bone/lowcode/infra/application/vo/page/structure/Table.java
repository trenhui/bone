package com.bone.lowcode.infra.application.vo.page.structure;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class Table {
    private String level = "table";

    private String id;

    private String name;

    private Integer sequence;

    private List<Model> modelList = new ArrayList<>();
}
