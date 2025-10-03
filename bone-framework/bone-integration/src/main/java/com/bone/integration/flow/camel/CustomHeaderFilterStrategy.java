package com.bone.integration.flow.camel;

import org.apache.camel.http.base.HttpHeaderFilterStrategy;

public class CustomHeaderFilterStrategy extends HttpHeaderFilterStrategy {
    public CustomHeaderFilterStrategy() {
        // 屏蔽不想传递的 Header
        getOutFilter().add("_randomKey_");
        getOutFilter().add("_mock_");

        // 需要设置忽略大小写，默认转成小写进行比较，见org.apache.camel.support.DefaultHeaderFilterStrategy.doFiltering
        setCaseInsensitive(true);
    }
}
