package com.bone.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * 条件求值器（可替换）。
 *
 * <p><b>为什么是接口</b>：条件语义有两处可能来源——① 引擎自带的结构化求值（{@link StructuredConditionEvaluator}）； ② 平台 UI
 * 协议规则的求值器（前端先拦、后端强制的同源纪律要求「同一个条件，前后端结论必须一致」）。二者若语义 分叉，同一张单据会出现「界面显示走 A 分支、实际走 B
 * 分支」。故抽象为端口，由装配层决定用哪种，并要求两种实现 在同一组条件上结论一致（由装配层的 parity 测试锁定）。
 *
 * <p><b>禁止用 SpEL / JS 表达式实现</b>：流程条件是业务人员可编辑的元数据，表达式求值器若具备调用方法 / 构造对象的 能力，即等于把平台变成远程代码执行面。结构化 JSON
 * 条件（{@code all/any/not} + {@code fieldCode/operator/value}） 才是正确形态。
 */
public interface WorkflowConditionEvaluator {

  /**
   * 求值一个结构化条件。
   *
   * @param condition 条件（结构化 JSON）
   * @param data 业务数据（按 fieldCode 键；支持 {@code a.b} 路径穿透子表）
   * @param ctx 主体上下文（{@code $me} 等）
   * @return 条件是否成立
   */
  boolean evaluate(JsonNode condition, Map<String, Object> data, ConditionContext ctx);
}
