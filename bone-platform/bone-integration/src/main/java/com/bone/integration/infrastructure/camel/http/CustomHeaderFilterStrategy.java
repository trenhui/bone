package com.bone.integration.infrastructure.camel.http;

import org.apache.camel.http.base.HttpHeaderFilterStrategy;

/** Camel HTTP 出站 Header 过滤（自 legacy engine 抽取，无 TPA 依赖）。 */
public class CustomHeaderFilterStrategy extends HttpHeaderFilterStrategy {

    public CustomHeaderFilterStrategy() {
        getOutFilter().add("_randomKey_");
        getOutFilter().add("_mock_");
        setCaseInsensitive(true);
    }
}
