package com.bone.integration.flow.camel;

import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.http.HttpProducer;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;

import java.net.URI;

public class ExtendHttpEndpoint extends HttpEndpoint {
    public ExtendHttpEndpoint(String uri, HttpComponent component, URI httpURI) {
        super(uri, component, httpURI);
    }

    public ExtendHttpEndpoint(String endPointURI, HttpComponent component, HttpClientBuilder clientBuilder, HttpClientConnectionManager clientConnectionManager, HttpClientConfigurer clientConfigurer) {
        super(endPointURI, component, (URI)null, clientBuilder, clientConnectionManager, clientConfigurer);
    }

    @Override
    public HttpProducer createProducer() throws Exception {
        // 返回自定义的 HttpProducer
        return new ExtendHttpProducer(this);
    }
}
