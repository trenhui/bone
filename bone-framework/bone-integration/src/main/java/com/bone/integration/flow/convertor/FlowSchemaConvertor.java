package com.bone.integration.flow.convertor;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bone.integration.flow.node.*;
import lombok.Data;

import java.util.*;

/**
 * 将schema转成Node
 */
public class FlowSchemaConvertor {

    public static StartNode buildGraphNode(String schema) {
        List<GraphNode> nodeList = parser(schema);
        Map<String, GraphNode> nodeMap = new HashMap<String, GraphNode>();
        for (GraphNode graphNode : nodeList) {
            nodeMap.put(graphNode.getId(), graphNode);
        }

        StartNode startNode = findStartNode(nodeList);
        Map<String, Pair> logicEndNodeMap = new HashMap<>();
        process(startNode, nodeMap, logicEndNodeMap);

        return startNode;
    }

    private static StartNode findStartNode(List<GraphNode> nodeList) {
        return (StartNode) nodeList.stream().filter(r -> r instanceof StartNode).findFirst().orElse(null);
    }

    private static void process(GraphNode graphNode, Map<String, GraphNode> nodeMap, Map<String, Pair> logicEndNodeMap) {
        if (graphNode == null) {
            return;
        }

        if (graphNode instanceof ChoiceNode) {
            ChoiceNode choiceNode = (ChoiceNode) graphNode;
            if (CollectionUtil.isNotEmpty(choiceNode.getOutgoings())) {
                String outgoingId = choiceNode.getOutgoings().get(0);
                if (!logicEndNodeMap.containsKey(outgoingId)) {
                    logicEndNodeMap.put(outgoingId, new Pair(choiceNode.getId(), outgoingId));
                }
            }

            List<WhenNode> whenNodeList = choiceNode.getWhenNodeList();
            if (whenNodeList != null) {
                for (WhenNode whenNode : whenNodeList) {
                    process(whenNode, nodeMap, logicEndNodeMap);
                }
            }
            process(choiceNode.getElseNode(), nodeMap, logicEndNodeMap);
        }

        List<String> outgoings = graphNode.getOutgoings();
        if (outgoings == null) {
            return;
        }

        for (String outgoing : outgoings) {
            Pair pair = logicEndNodeMap.get(outgoing);
            if (pair != null && !pair.getStartId().equals(graphNode.getId())) {
                continue;
            }

            GraphNode childNode = nodeMap.get(outgoing);
            graphNode.addOutgoingNode(childNode);
            process(childNode, nodeMap, logicEndNodeMap);
        }
    }

    public static List<GraphNode> parser(String nodeSchema) {
        List<GraphNode> nodes = new ArrayList<GraphNode>();

        JSONArray jsonArray = JSON.parseArray(nodeSchema);

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            parseSingle(jsonObject, nodes);
        }

        return nodes;
    }

    private static void parseSingle(JSONObject jsonObject, List<GraphNode> nodes) {
        String type = jsonObject.getString("elementType");
        switch (type) {
            case "START":
                nodes.add(parseNode(jsonObject, StartNode.class));
                break;
            case "CONDITION":
                parseConditionNode(jsonObject, nodes);
                break;
            case "NETTY_HTTP":
                nodes.add(parseNode(jsonObject, NettyHttpNode.class));
                break;
            case "HTTP":
                nodes.add(parseNode(jsonObject, HttpNode.class));
                break;
            case "DIRECT":
                nodes.add(parseNode(jsonObject, DirectNode.class));
                break;
            case "CONVERT_BODY_TO":
                nodes.add(parseNode(jsonObject, ConvertBodyToNode.class));
                break;
            case "DYNAMIC_ROUTE":
                nodes.add(parseNode(jsonObject, DynamicRouteNode.class));
                break;
            case "RECIPIENT_LIST":
                nodes.add(parseNode(jsonObject, RecipientListNode.class));
                break;
            case "MARSHAL":
                nodes.add(parseNode(jsonObject, MarshalNode.class));
                break;
            case "UNMARSHAL":
                nodes.add(parseNode(jsonObject, UnmarshalNode.class));
                break;
            case "SET_HEADER":
                nodes.add(parseNode(jsonObject, SetHeaderNode.class));
                break;
            case "TRANSFORM":
                nodes.add(parseNode(jsonObject, TransformNode.class));
                break;
            case "LOG":
                nodes.add(parseNode(jsonObject, LogNode.class));
                break;
            case "END":
                nodes.add(parseNode(jsonObject, EndNode.class));
                break;
            case "WEBSERVICE":
                nodes.add(parseNode(jsonObject, WebServiceNode.class));
                break;
                //加解密
            case "EN_AND_DE":
                nodes.add(parseNode(jsonObject, EnAndDeNode.class));
                break;
            case "YDA_SM4":
                nodes.add(parseNode(jsonObject, YingDaEncryptNode.class));
                break;
            case "FEIGN":
                nodes.add(parseNode(jsonObject, FeignNode.class));
                break;
            case "SCRIPT":
                nodes.add(parseNode(jsonObject, ScriptNode.class));
                break;
            case "SET_HEADERS":
                nodes.add(parseNode(jsonObject, SetHeadersNode.class));
                break;
            case "SET_BODY":
                nodes.add(parseNode(jsonObject, SetBodyNode.class));
                break;
            default:
                throw new RuntimeException("unknown element type: " + type);
        }
    }
    private static GraphNode parseNode(JSONObject jsonObject, Class<? extends GraphNode> clazz) {
        return JSON.parseObject(jsonObject.toJSONString(), clazz);
    }

    private static void parseConditionNode(JSONObject jsonObject, List<GraphNode> nodes) {
        JSONArray conditions = jsonObject.getJSONArray("conditions");
        List<WhenNode> whenNodeList = new ArrayList<>();
        ElseNode elseNode = new ElseNode();

        for (int i = 0; i < conditions.size(); i++) {
            JSONObject condition = conditions.getJSONObject(i);
            if (condition.getString("conditionType").equals("CUSTOM")) {
                WhenNode whenNode = new WhenNode();
                whenNode.setId(condition.getString("key"));
                whenNode.setCondition(condition.getString("expression"));
                whenNode.setOutgoings(Arrays.asList(condition.getString("outgoing")));
                whenNodeList.add(whenNode);
                nodes.add(whenNode);
            } else {
                elseNode.setId(condition.getString("key"));
                elseNode.setOutgoings(Arrays.asList(condition.getString("outgoing")));
            }
        }
        ChoiceNode choiceNode = new ChoiceNode();
        choiceNode.setWhenNodeList(whenNodeList);
        choiceNode.setElseNode(elseNode);
        choiceNode.setOutgoings(jsonObject.getObject("outgoings", List.class));
        choiceNode.setId(jsonObject.getString("key"));
        nodes.add(elseNode);
        nodes.add(choiceNode);
    }

    @Data
    public static class Pair {
        private String startId;
        private String endId;

        public Pair(String startId, String endId) {
            this.startId = startId;
            this.endId = endId;
        }
    }
}
