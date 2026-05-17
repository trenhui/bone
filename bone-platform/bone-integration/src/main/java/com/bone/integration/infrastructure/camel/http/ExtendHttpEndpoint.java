package com.bone.integration.infrastructure.camel.http;

import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;

import java.net.URI;

/** 扩展 HTTP 端点（使用标准 HttpProducer，不引入 legacy Mock 逻辑）。 */
public class ExtendHttpEndpoint extends HttpEndpoint {

    public ExtendHttpEndpoint(String uri, HttpComponent component, URI httpURI) {
        super(uri, component, httpURI);
    }

    public ExtendHttpEndpoint(
            String endPointURI,
            HttpComponent component,
            HttpClientBuilder clientBuilder,
            HttpClientConnectionManager clientConnectionManager,
            HttpClientConfigurer clientConfigurer) {
        super(endPointURI, component, null, clientBuilder, clientConnectionManager, clientConfigurer);
    }
}
