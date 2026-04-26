package com.bone.lowcode.infra.application.vo.page.structure;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Page {
    private String level = "page";

    private String id;

    private Byte type;

    private String code;

    private String name;

    private List<Form> body = new ArrayList<>();
}
