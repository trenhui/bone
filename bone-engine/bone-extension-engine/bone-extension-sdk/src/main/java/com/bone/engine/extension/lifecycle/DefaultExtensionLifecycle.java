package com.bone.engine.extension.lifecycle;

import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扩展点生命周期默认实现
 * <p>
 * 为扩展点生命周期接口提供默认实现，扩展点实现类可以选择性地覆盖需要的方法
 * <strong>默认行为：</strong>
 * <ul>
 *   <li>初始化：记录日志，无特殊操作</li>
 *   <li>前置处理：记录调试日志</li>
 *   <li>后置处理：记录调试日志</li>
 *   <li>异常处理：记录错误日志</li>
 *   <li>销毁：记录日志，无特殊操作</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class DefaultExtensionLifecycle implements ExtensionLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(DefaultExtensionLifecycle.class);

    @Override
    public void initialize() {
        if (logger.isInfoEnabled()) {
            logger.info("Extension initialized: {}", this.getClass().getName());
        }
    }

    @Override
    public void beforeInvoke(BizContext<?> context, String methodName, Object[] args) {
        if (logger.isDebugEnabled()) {
            logger.debug("Extension before invoke: method={}, context={}, args={}", 
                    methodName, context, args);
        }
    }

    @Override
    public void afterInvoke(BizContext<?> context, String methodName, Object result) {
        if (logger.isDebugEnabled()) {
            logger.debug("Extension after invoke: method={}, context={}, result={}", 
                    methodName, context, result);
        }
    }

    @Override
    public void onException(BizContext<?> context, String methodName, Exception exception) {
        if (logger.isErrorEnabled()) {
            logger.error("Extension invoke error: method={}, context={}", 
                    methodName, context, exception);
        }
    }

    @Override
    public void destroy() {
        if (logger.isInfoEnabled()) {
            logger.info("Extension destroyed: {}", this.getClass().getName());
        }
    }
}