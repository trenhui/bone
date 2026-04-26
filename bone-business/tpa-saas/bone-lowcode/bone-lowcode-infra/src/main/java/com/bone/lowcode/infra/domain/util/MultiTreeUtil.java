package com.bone.lowcode.infra.domain.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bone.lowcode.infra.application.vo.groupData.SelectDropDataVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 多级下拉框数据源
 */
@Slf4j
@Component
public class MultiTreeUtil {

    /**
     * 存放所有根节点
     */
    public static Map<String, MultiTreeNode> rootMap = new HashMap<>();

    /**
     * 存放所有树, key:根节点key, value:对应的树
     */
    public static Map<String, Map<String, MultiTreeNode>> treeMap = new HashMap<>();

    @Autowired
    private Environment environment;

    /**
     * 读取数组配置(multiTree.treeStrList[i])并依次解析
     */
    @PostConstruct
    public void loadConfigAndGetTrees() {
        int index = 0;
        List<String> result = new ArrayList<>();
        while (environment.containsProperty("multiTree.treeStrList[" + index + "]")) {
            String value = environment.getProperty("multiTree.treeStrList[" + index + "]");
            result.add(value);
            index++;
        }
        log.info("初始化multiTree, 树的数量:{}", index);
        if (CollectionUtils.isEmpty(result)) {
            return;
        }

        for (String treeStr : result) {
            try {
                convertToMap(treeStr);
            } catch (Exception e) {
                log.info("解析multiTree发生异常, treeStr:{}...", treeStr.substring(0, Math.min(treeStr.length(), 50)), e);
            }
        }
    }

    /**
     * 解析JSON字符串,转换为以code为key,MultiTreeNode为value的Map
     */
    public static void convertToMap(String jsonStr) {
        if (!StringUtils.hasText(jsonStr)) {
            return;
        }

        try {
            Map<String, MultiTreeNode> temNodeMap = new HashMap<>();
            Map<String, MultiTreeNode> temRootMap = new HashMap<>();
            JSONObject jsonObject = JSON.parseObject(jsonStr);
            parseTreeNode(jsonObject, temNodeMap, temRootMap, true);

            rootMap.putAll(temRootMap);
            String rootKey = new ArrayList<>(temRootMap.keySet()).get(0);
            treeMap.put(rootKey, temNodeMap);
        } catch (Exception e) {
            throw new RuntimeException("解析JSON字符串发生异常,jsonStr前缀:" + jsonStr.substring(0, Math.min(jsonStr.length(), 50)), e);
        }
    }

    /**
     * 递归解析树节点
     */
    private static void parseTreeNode(JSONObject jsonObj, Map<String, MultiTreeNode> temNodeMap, Map<String, MultiTreeNode> temRootMap, boolean isRoot) {
        String code = jsonObj.getString("code");
        String name = jsonObj.getString("name");
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            throw new RuntimeException("任一节点的code或name值不能为空");
        }
        if (temNodeMap.containsKey(code)) {
            throw new RuntimeException("code重复,code:" + code);
        }

        MultiTreeNode node = new MultiTreeNode(code, name);
        temNodeMap.putIfAbsent(code, node);
        if (isRoot) {
            if (temRootMap.containsKey(code)) {
                throw new RuntimeException("rootCode重复,rootCode:" + code);
            }
            temRootMap.putIfAbsent(code, node);
        }

        // 递归处理子节点
        if (jsonObj.containsKey("children")) {
            JSONArray children = jsonObj.getJSONArray("children");
            for (int i = 0; i < children.size(); i++) {
                JSONObject child = children.getJSONObject(i);
                parseTreeNode(child, temNodeMap, temRootMap, false);
                node.getChildren().add(temNodeMap.get(child.getString("code")));
            }
        }
    }

    /**
     * 获取树的数量
     */
    public static int getRootSize() {
        return rootMap.size();
    }

    /**
     * 获取所有树
     */
    public static LinkedList<SelectDropDataVO> getRootList() {
        return rootMap.values().stream().map(root -> {
            SelectDropDataVO vo = new SelectDropDataVO();
            vo.setCode(root.getCode());
            vo.setName(root.getName());
            return vo;
        }).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * 是否存在某根节点的code是入参
     */
    public static boolean containRootCode(String code) {
        return rootMap.containsKey(code);
    }

    /**
     * 根据code获取根节点
     */
    public static MultiTreeNode getRootByCode(String code) {
        return rootMap.get(code);
    }

    /**
     * 是否存在某棵树的某个节点的code是入参
     */
    public static boolean containCode(String code) {
        for (Map.Entry<String, Map<String, MultiTreeNode>> entry : treeMap.entrySet()) {
            if (entry.getValue().containsKey(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据父级code获取子节点
     */
    public static List<MultiTreeNode> getChildrenByParentCode(String parentCode) {
        for (Map.Entry<String, Map<String, MultiTreeNode>> entry : treeMap.entrySet()) {
            if (entry.getValue().containsKey(parentCode)) {
                return entry.getValue().get(parentCode).getChildren();
            }
        }
        return List.of();
    }
}
