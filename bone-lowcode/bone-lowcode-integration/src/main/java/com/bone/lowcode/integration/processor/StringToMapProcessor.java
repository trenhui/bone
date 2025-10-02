package com.bone.lowcode.integration.processor;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.yomahub.liteflow.util.JsonUtil;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@SuppressWarnings("unchecked")
@Component
public class StringToMapProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body instanceof Map) {
            return;
        }

        if (!(body instanceof String)) {
            throw new IllegalArgumentException("StringToMapProcessor: body is not a String");
        }

        if (StringUtils.isEmpty((String) body)) {
            throw new IllegalArgumentException("StringToMapProcessor: body is empty");
        }

        // json格式
        String content = ((String) body).trim();
        if (isJsonFormat(content)) {
            Map<String, Object> value = JsonUtil.parseObject(content, Map.class);
            exchange.getIn().setBody(value);
        } else if (isXmlFormat(content)) {
            XmlMapper xmlMapper = XmlMapper.xmlBuilder().build();
            Map<String, Object> value = xmlMapper.readValue(content, Map.class);
            exchange.getIn().setBody(value);
        } else {
            throw new IllegalArgumentException("StringToMapProcessor: body is not a supported, require type is json or xml.");
        }
    }

    private boolean isJsonFormat(String body) {
        return body.startsWith("{") && body.endsWith("}");
    }

    private boolean isXmlFormat(String body) {
        return body.startsWith("<") && body.endsWith(">");
    }
}
