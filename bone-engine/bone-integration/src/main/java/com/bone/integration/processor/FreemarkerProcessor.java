package com.bone.integration.processor;

import com.bone.integration.utils.FreemarkerUtils;
import lombok.Data;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.HashMap;
import java.util.Map;


@Data
public class FreemarkerProcessor implements Processor {
    private String ftlPath;
    private String templateContent;
    private String inputFormat;

    public FreemarkerProcessor(String ftlPath, String templateContent) {
        this.ftlPath = ftlPath;
        this.templateContent = templateContent;
    }

    public FreemarkerProcessor(String ftlPath, String templateContent, String inputFormat) {
        this(ftlPath, templateContent);
        this.inputFormat = inputFormat;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object content = exchange.getIn().getBody();
        Map<String, Object> extParams = new HashMap<>(exchange.getIn().getHeaders());

        String convertedValue = FreemarkerUtils.process(content, templateContent, extParams);
        exchange.getIn().setBody(convertedValue);
    }
}
