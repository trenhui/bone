package com.bone.metadata.runtime.application;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 逃逸动作路由：按 {@link RuntimeActionDef#getHandlerBean()} 指定的 Spring bean 名， 将副作用分发到对应的 {@link
 * RuntimeActionHandler}。非逃逸动作无副作用，返回空结果。
 *
 * <p>Spring 自动把同类型 bean 注入为 {@code Map<beanName, bean>}，故 handler_bean 即 bean 名。
 */
@Component
public class RuntimeActionDispatcher {

  private final Map<String, RuntimeActionHandler> handlers;

  public RuntimeActionDispatcher(Map<String, RuntimeActionHandler> handlers) {
    this.handlers = handlers;
  }

  /**
   * @return 逃逸动作的副作用结果；非逃逸动作返回空 Map。
   */
  public Map<String, Object> dispatch(RuntimeActionContext ctx) {
    RuntimeActionDef def = ctx.getDef();
    if (!def.isEscape()) {
      return new LinkedHashMap<>();
    }
    String bean = def.getHandlerBean();
    if (bean == null || bean.isBlank()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS,
          "逃逸动作缺少 handler_bean: " + def.getActionCode());
    }
    RuntimeActionHandler handler = handlers.get(bean);
    if (handler == null) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_NOT_FOUND, "未找到动作处理器 bean: " + bean);
    }
    return handler.execute(ctx);
  }
}
