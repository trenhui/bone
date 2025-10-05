package com.bone.engine.extension.route;

import com.bone.engine.extension.BizContext;

/**
 * ExtPointRouter
 *
 * @author renhui.trh 2023-11-1
 */
public interface ExtPointRouter {
    <C> C locateExtProvider(Class<C> targetClz, BizContext bizContext);
}
