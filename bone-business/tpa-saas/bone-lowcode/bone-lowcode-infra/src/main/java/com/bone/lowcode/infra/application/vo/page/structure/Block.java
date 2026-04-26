package com.bone.lowcode.infra.application.vo.page.structure;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class Block {
    private String level = "block";

    private String id;

    private String name;

    private Byte sequenceNumber;

    List<Fieldset> fieldsetList = new ArrayList<>();

    List<Table> tableList = new ArrayList<>();
}
