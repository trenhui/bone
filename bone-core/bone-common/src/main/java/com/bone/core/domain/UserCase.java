package com.bone.core.domain;

import com.bone.core.extension.BizContextUtils;

public abstract class UserCase<R, T> {

    /**
     * 执行用例
     *
     * @return
     */
    public abstract R execute(UseCaseContext<T> useCaseContext);


    public R executeCase(UseCaseContext<T> useCaseContext) {
        try {

            BizContextUtils.setCurrentContext(useCaseContext);
            return execute(useCaseContext);
        } finally {
            BizContextUtils.removeCurrentContext();
        }

    }



}
