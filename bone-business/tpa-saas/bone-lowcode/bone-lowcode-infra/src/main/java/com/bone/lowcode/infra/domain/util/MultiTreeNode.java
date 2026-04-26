package com.bone.lowcode.infra.domain.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiTreeNode {
    private String code;
    private String name;
    private List<MultiTreeNode> children = new ArrayList<>();

    public MultiTreeNode(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
