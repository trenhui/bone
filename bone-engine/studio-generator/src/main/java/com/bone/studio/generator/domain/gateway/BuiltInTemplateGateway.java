package com.bone.studio.generator.domain.gateway;

import java.util.Optional;

/**
 * 内置模板正文出口（模板 code → classpath {@code templates/{code}.ftl} 的原文）。
 *
 * <p><b>为何需要这个端口</b>：生成链路原本把 {@code gen_code_template.content} 当作唯一真源，但库里预置的正文从未与 classpath
 * 模板同步过——已漂移成引用 {@code domain.entity} 包、{@code column.isPrimaryKey} 这类不存在的属性， 只要 content 非空，生成任务必定抛
 * {@code InvalidReferenceException} 失败。
 *
 * <p>因此收敛为「<b>classpath 模板是唯一真源</b>：{@code content} 为空即回落到内置模板；{@code content}
 * 非空才作为用户自定义覆盖」。预览与校验必须走同一回落口径，否则会出现「预览看到的是旧正文、实际生成的是新模板」。
 */
public interface BuiltInTemplateGateway {

  /** 读取内置模板正文；不存在返回 {@link Optional#empty()}。 */
  Optional<String> contentOf(String code);

  /** 是否为内置模板（classpath 下存在对应 {@code .ftl}）。 */
  default boolean exists(String code) {
    return contentOf(code).isPresent();
  }
}
