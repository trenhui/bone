package com.bone.lowcode.infra.application.vo.page.structure;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Form {
    private String level = "form";

    private String id;

    private String name;

    private List<Block> body = new ArrayList<>();
}
