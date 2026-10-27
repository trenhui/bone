package com.bone.workflow.starter.designer;

/**
 * 设计态请求本身不合法（JSON 不可解析、key 不一致、缺操作人、版本不存在）。宿主映射建议 <b>400</b>（版本不存在可映射 404）。
 *
 * <p>与 {@link WorkflowDefinitionInvalidException} 的区别：本类是「请求 / 数据形态有问题」，后者是「定义本身配错了」。
 * 两者在界面上要分开呈现——前者通常是前端 bug 或调用方拼错，后者是业务方要回去改流程。
 */
public class WorkflowDefinitionRequestException extends RuntimeException {

  public WorkflowDefinitionRequestException(String message) {
    super(message);
  }

  public WorkflowDefinitionRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
