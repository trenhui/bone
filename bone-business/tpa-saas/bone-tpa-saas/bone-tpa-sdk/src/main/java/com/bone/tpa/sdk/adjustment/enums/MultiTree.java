package com.bone.tpa.sdk.adjustment.enums;

import org.springframework.util.CollectionUtils;

import java.util.*;

public class MultiTree {

    public static final MultiTree claimConclusionTree = new MultiTree();

    static {
        MultiTreeNode node1 = new MultiTreeNode("P", "赔付");
        claimConclusionTree.addToRoot(node1);

        MultiTreeNode node2 = new MultiTreeNode("P1", "通融赔付");
        claimConclusionTree.addToRoot(node2);

        MultiTreeNode node3 = new MultiTreeNode("D4", "零赔付");
        MultiTreeNode node31 = new MultiTreeNode("D4:01", "客户主动放弃索赔");
        node3.getChildren().add(node31);
        MultiTreeNode node32 = new MultiTreeNode("D4:03", "事故损失小于免赔额");
        node3.getChildren().add(node32);
        MultiTreeNode node33 = new MultiTreeNode("D4:02", "无事故责任且不需赔付");
        node3.getChildren().add(node33);
        MultiTreeNode node34 = new MultiTreeNode("D4:04", "超过法律规定诉讼时效");
        node3.getChildren().add(node34);
        claimConclusionTree.addToRoot(node3);

        MultiTreeNode node4 = new MultiTreeNode("D", "拒付");
        MultiTreeNode node41 = new MultiTreeNode("D:99", "其他");
        node4.getChildren().add(node41);
        MultiTreeNode node42 = new MultiTreeNode("D:14", "不在有效保障期");
        node4.getChildren().add(node42);
        MultiTreeNode node43 = new MultiTreeNode("D:13", "非被保险人本人出险");
        node4.getChildren().add(node43);
        MultiTreeNode node44 = new MultiTreeNode("D:01", "责任免除");
        node4.getChildren().add(node44);
        MultiTreeNode node45 = new MultiTreeNode("D:04", "非指定医院或不符合医院释义");
        node4.getChildren().add(node45);
        MultiTreeNode node46 = new MultiTreeNode("D:10", "不属于保险责任保障范围");
        node4.getChildren().add(node46);
        MultiTreeNode node47 = new MultiTreeNode("D:03", "既往症");
        node4.getChildren().add(node47);
        MultiTreeNode node48 = new MultiTreeNode("D:02", "非意外伤害");
        node4.getChildren().add(node48);
        MultiTreeNode node49 = new MultiTreeNode("D:05", "不实告知");
        node4.getChildren().add(node49);
        MultiTreeNode node410 = new MultiTreeNode("D:06", "在批注范围内");
        node4.getChildren().add(node410);
        MultiTreeNode node411 = new MultiTreeNode("D:07", "合同解除");
        node4.getChildren().add(node411);
        MultiTreeNode node412 = new MultiTreeNode("D:08", "等待期内病症");
        node4.getChildren().add(node412);
        MultiTreeNode node413 = new MultiTreeNode("D:09", "手术等级不符");
        node4.getChildren().add(node413);
        MultiTreeNode node414 = new MultiTreeNode("D:11", "同一次住院已累计赔付满90天");
        node4.getChildren().add(node414);
        MultiTreeNode node415 = new MultiTreeNode("D:12", "投保单非被保险人亲签");
        node4.getChildren().add(node415);
        claimConclusionTree.addToRoot(node4);

        MultiTreeNode node5 = new MultiTreeNode("D5", "注销");
        MultiTreeNode node51 = new MultiTreeNode("D5:05", "其他");
        node5.getChildren().add(node51);
        MultiTreeNode node52 = new MultiTreeNode("D5:07", "客户主动放弃索赔");
        node5.getChildren().add(node52);
        MultiTreeNode node53 = new MultiTreeNode("D5:09", "事故损失小于免赔额");
        node5.getChildren().add(node53);
        MultiTreeNode node54 = new MultiTreeNode("D5:01", "客户报错案");
        node5.getChildren().add(node54);
        MultiTreeNode node55 = new MultiTreeNode("D5:02", "客户重复报案");
        node5.getChildren().add(node55);
        MultiTreeNode node56 = new MultiTreeNode("D5:03", "不属于投保险别或险种");
        node5.getChildren().add(node56);
        MultiTreeNode node57 = new MultiTreeNode("D5:04", "理赔人员操作失误");
        node5.getChildren().add(node57);
        MultiTreeNode node58 = new MultiTreeNode("D5:06", "医保局撤销");
        node5.getChildren().add(node58);
        MultiTreeNode node59 = new MultiTreeNode("D5:08", "已过索赔时效");
        node5.getChildren().add(node59);
        MultiTreeNode node510 = new MultiTreeNode("D5:10", "客户不提供索赔单证");
        node5.getChildren().add(node510);
        claimConclusionTree.addToRoot(node5);

        MultiTreeNode node6 = new MultiTreeNode("P3", "撤件");
        claimConclusionTree.addToRoot(node6);
    }

    public static final String rootCode = "claimConclusionRootCode";
    public static final String rootName = "赔付结论";

    private final MultiTreeNode root;
    private final Map<String, MultiTreeNode> nodeMap = new HashMap<>();

    public MultiTree() {
        this.root = new MultiTreeNode(rootCode, rootName);
        nodeMap.put(rootCode, this.root);
    }

    /**
     * 添加节点到根节点
     */
    public boolean addToRoot(MultiTreeNode child) {
        MultiTreeNode root = nodeMap.get(rootCode);
        if (root == null) {
            return false;
        }

        root.addChild(child);
        putMap(child);
        return true;
    }

    private void putMap(MultiTreeNode node) {
        if (node == null) {
            return;
        }

        nodeMap.putIfAbsent(node.getCode(), node);
        if (CollectionUtils.isEmpty(node.getChildren())) {
            return;
        }

        for (MultiTreeNode child : node.getChildren()) {
            putMap(child);
        }
    }

    /**
     * 添加节点到指定父节点
     */
    public boolean addNode(String parentCode, String childCode, String childValue) {
        MultiTreeNode parent = nodeMap.get(parentCode);
        if (parent == null) {
            return false;
        }

        MultiTreeNode child = new MultiTreeNode(childCode, childValue);
        parent.addChild(child);
        nodeMap.put(childCode, child);
        return true;
    }

    /**
     * 根据父节点code获取所有下一层子节点
     */
    public List<MultiTreeNode> getChildrenByParentCode(String parentCode) {
        MultiTreeNode parent = nodeMap.get(parentCode);
        return parent != null ? parent.getChildren() : Collections.emptyList();
    }

    /**
     * 获取根节点
     */
    public MultiTreeNode getRoot() {
        return root;
    }

    /**
     * 获取所有节点
     */
    public Collection<MultiTreeNode> getAllNodes() {
        return nodeMap.values();
    }

    /**
     * 获取所有code
     */
    public Collection<String> getAllCode() {
        return nodeMap.keySet();
    }

    /**
     * 根据code获取name
     */
    public static String getNameByCode(String code) {
        if (!MultiTree.claimConclusionTree.nodeMap.containsKey(code)) {
            return null;
        }
        return MultiTree.claimConclusionTree.nodeMap.get(code).getName();
    }
}
