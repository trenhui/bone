package com.bone.engine.extension.config;

import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationContextAware;

/**
 * 默认扩展点事件发布器实现
 * <p>
 * 基于Spring的事件机制实现扩展点事件的发布
 * <strong>主要特性：</strong>
 * <ul>
 *   <li>与Spring事件机制集成</li>
 *   <li>支持多种事件类型的发布</li>
 *   <li>内置日志记录</li>
 *   <li>异步事件支持</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class DefaultExtensionEventPublisher implements ExtensionEventPublisher, ApplicationEventPublisherAware {

    private static final Logger logger = LoggerFactory.getLogger(DefaultExtensionEventPublisher.class);

    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishBeforeEvent(Class<?> extPointInterface, Object extensionImpl, BizContext<?> context) {
        if (logger.isDebugEnabled()) {
            logger.debug("Extension before execute: extPoint={}, impl={}, context={}",
                    extPointInterface.getName(),
                    extensionImpl.getClass().getName(),
                    context);
        }
        
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new ExtensionBeforeEvent(extPointInterface, extensionImpl, context));
        }
    }

    @Override
    public void publishAfterEvent(Class<?> extPointInterface, Object extensionImpl, BizContext<?> context, Object result) {
        if (logger.isDebugEnabled()) {
            logger.debug("Extension after execute: extPoint={}, impl={}, context={}, result={}",
                    extPointInterface.getName(),
                    extensionImpl.getClass().getName(),
                    context,
                    result);
        }
        
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new ExtensionAfterEvent(extPointInterface, extensionImpl, context, result));
        }
    }

    @Override
    public void publishExceptionEvent(Class<?> extPointInterface, Object extensionImpl, BizContext<?> context, Exception exception) {
        if (logger.isErrorEnabled()) {
            logger.error("Extension exception: extPoint={}, impl={}, context={}",
                    extPointInterface.getName(),
                    extensionImpl.getClass().getName(),
                    context, exception);
        }
        
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new ExtensionExceptionEvent(extPointInterface, extensionImpl, context, exception));
        }
    }

    @Override
    public void publishRouteEvent(Class<?> extPointInterface, Object selectedImpl, BizContext<?> context) {
        if (logger.isDebugEnabled()) {
            logger.debug("Extension route selected: extPoint={}, impl={}, context={}",
                    extPointInterface.getName(),
                    selectedImpl != null ? selectedImpl.getClass().getName() : "null",
                    context);
        }
        
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new ExtensionRouteEvent(extPointInterface, selectedImpl, context));
        }
    }

    @Override
    public void publishRegisterEvent(Class<?> extPointInterface, Object extensionImpl) {
        if (logger.isInfoEnabled()) {
            logger.info("Extension registered: extPoint={}, impl={}",
                    extPointInterface.getName(),
                    extensionImpl.getClass().getName());
        }
        
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new ExtensionRegisterEvent(extPointInterface, extensionImpl));
        }
    }

    // 扩展点事件基类
    public abstract static class ExtensionEvent extends org.springframework.context.ApplicationEvent {
        private final Class<?> extPointInterface;

        protected ExtensionEvent(Object source, Class<?> extPointInterface) {
            super(source);
            this.extPointInterface = extPointInterface;
        }

        public Class<?> getExtPointInterface() {
            return extPointInterface;
        }
    }

    // 扩展点执行前事件
    public static class ExtensionBeforeEvent extends ExtensionEvent {
        private final BizContext<?> context;

        public ExtensionBeforeEvent(Class<?> extPointInterface, Object extensionImpl, BizContext<?> context) {
            super(extensionImpl, extPointInterface);
            this.context = context;
        }

        public Object getExtensionImpl() {
            return getSource();
        }

        public BizContext<?> getContext() {
            return context;
        }
    }

    // 扩展点执行后事件
    public static class ExtensionAfterEvent extends ExtensionBeforeEvent {
        private final Object result;

        public ExtensionAfterEvent(Class<?> extPointInterface, Object extensionImpl, BizContext<?> context, Object result) {
            super(extPointInterface, extensionImpl, context);
            this.result = result;
        }

        public Object getResult() {
            return result;
        }
    }

    // 扩展点异常事件
    public static class ExtensionExceptionEvent extends ExtensionBeforeEvent {
        private final Exception exception;

        public ExtensionExceptionEvent(Class<?> extPointInterface, Object extensionImpl, BizContext<?> context, Exception exception) {
            super(extPointInterface, extensionImpl, context);
            this.exception = exception;
        }

        public Exception getException() {
            return exception;
        }
    }

    // 扩展点路由选择事件
    public static class ExtensionRouteEvent extends ExtensionEvent {
        private final BizContext<?> context;

        public ExtensionRouteEvent(Class<?> extPointInterface, Object selectedImpl, BizContext<?> context) {
            super(selectedImpl, extPointInterface);
            this.context = context;
        }

        public Object getSelectedImpl() {
            return getSource();
        }

        public BizContext<?> getContext() {
            return context;
        }
    }

    // 扩展点注册事件
    public static class ExtensionRegisterEvent extends ExtensionEvent {
        public ExtensionRegisterEvent(Class<?> extPointInterface, Object extensionImpl) {
            super(extensionImpl, extPointInterface);
        }

        public Object getExtensionImpl() {
            return getSource();
        }
    }
}