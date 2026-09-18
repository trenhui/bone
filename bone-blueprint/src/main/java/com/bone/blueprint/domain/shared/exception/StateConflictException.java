package com.bone.blueprint.domain.shared.exception;

import com.bone.core.exception.DomainException;

/**
 * 聚合状态冲突异常：业务操作与聚合当前生命周期状态不兼容（如已终态的支付单再次确认成功/关闭）。
 *
 * <p><b>与「输入校验异常」区分</b>：状态冲突是「当前状态不允许该操作」，输入校验是「入参本身非法」。 应用层据此分类—— 状态冲突映射 409（CONFLICT），校验异常通常映射
 * 400。用<b>类型</b>而非<b>消息文本</b>区分，避免 {@code msg.contains("状态")} 式的脆弱匹配（国际化 / 重构即失效，且无法静态发现）。
 *
 * <p>本类继承 {@link DomainException}，故既有的 {@code catch (DomainException)} 仍可兼容捕获；仅新增的 {@code catch
 * (StateConflictException)} 能精准命中状态冲突分支。
 */
public class StateConflictException extends DomainException {

  public StateConflictException(String message) {
    super(message);
  }

  public StateConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
