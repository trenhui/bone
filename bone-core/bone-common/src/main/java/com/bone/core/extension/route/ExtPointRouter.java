package com.bone.core.extension.route;

import com.bone.core.extension.BizContext;

/**
 * ExtPointRouter
 *
 * @author renhui.trh 2023-11-1
 */
public interface ExtPointRouter {
    <C> C locateExtProvider(Class<C> targetClz, BizContext bizContext);
}
