package com.bone.integration.uitls.customMethod;

import org.apache.camel.Exchange;

public class ScriptContext {
    private Exchange exchange;

    public ScriptContext(Exchange exchange) {
        this.exchange = exchange;
    }

    public void setHeader(String key, Object value) {
        exchange.getIn().setHeader(key, value);
    }

    public void setBody(Object value) {
        exchange.getIn().setBody(value);
    }
}

