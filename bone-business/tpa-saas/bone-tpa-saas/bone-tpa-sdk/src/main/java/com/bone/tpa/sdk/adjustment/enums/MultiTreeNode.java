package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * 多叉树节点类
 */
@Getter
@ToString
public class MultiTreeNode {
    private final String code;
    private final String name;
    private final List<MultiTreeNode> children = new ArrayList<>();

    public MultiTreeNode(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 添加子节点
     */
    public void addChild(MultiTreeNode child) {
        children.add(child);
    }

    /**
     * 获取所有直接子节点
     */
    public List<MultiTreeNode> children() {
        return new ArrayList<>(children);
    }

    /**
     * 获取所有直接子节点（兼容getChildren方法）
     */
    public List<MultiTreeNode> getChildren() {
        return children();
    }

    /**
     * 获取节点代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取节点名称
     */
    public String getName() {
        return name;
    }
}
