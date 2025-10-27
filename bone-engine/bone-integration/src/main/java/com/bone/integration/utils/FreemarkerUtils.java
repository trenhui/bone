package com.bone.integration.utils;

import com.bone.integration.application.dto.ParamDTO;
import com.bone.integration.common.Constants;
import com.bone.integration.flow.convertor.FreemarkerTransformer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FreemarkerUtils {

    public static String process(Object content, String template, List<ParamDTO> headerList) throws Exception {
        Map<String, Object> headerParams = new HashMap<>();
        if (!CollectionUtils.isEmpty(headerList)) {
            for (ParamDTO header: headerList) {
                ObjectMapper objectMapper = new ObjectMapper();
                if (!StringUtils.hasText(header.getKey())) {
                    continue;
                }
                if (isJsonFormat(header.getValue())) {
                    Map<String, Object> map = objectMapper.readValue(header.getValue(), Map.class);
                    headerParams.put(header.getKey(), map);
                } else {
                    headerParams.put(header.getKey(), header.getValue());
                }
            }
        }

        return process(content, template, headerParams);
    }

    public static  String process(Object content, String template, Map<String, Object> headerParams) throws Exception {
        Assert.hasText(template, "FreemarkerProcessor: template is empty");
        String convertedValue;
        if (content instanceof Map) {
            convertedValue = processMap((Map) content, null, template, headerParams);
        } else if (content instanceof String) {
            Assert.hasText((String) content, "FreemarkerProcessor: content is empty");
            convertedValue = processString((String) content, null, template, headerParams);
        } else {
            throw new UnsupportedOperationException("FreemarkerProcessor: Unsupported input data type: " + content.getClass());
        }

        return convertedValue;
    }

    private static  String processString(String inputData, String ftlPath, String templateContent, Map<String, Object> extParams) {
        String convertedValue;
        String type = getInputFormat(inputData);
        if (type.equalsIgnoreCase(Constants.XML)) {
            convertedValue = processXml(inputData, ftlPath, templateContent, extParams);
        } else if (type.equalsIgnoreCase(Constants.JSON)) {
            convertedValue = processJson(inputData, ftlPath, templateContent, extParams);
        } else {
            throw new IllegalArgumentException("Unsupported input format: " + type);
        }

        return convertedValue;
    }

    private static  String getInputFormat(Object body) {
        String content = ((String) body).trim();
        String type;
        if (isJsonFormat(content)) {
            type = Constants.JSON;
        } else if (isXmlFormat(content)) {
            type = Constants.XML;
        } else {
            throw new IllegalArgumentException("FreemarkerProcessor body is not a supported, require type is json or xml.");
        }

        return type;
    }

    private static  boolean isJsonFormat(String body) {
        return body.startsWith("{") && body.endsWith("}");
    }

    private static  boolean isXmlFormat(String body) {
        return body.startsWith("<") && body.endsWith(">");
    }

    private static  String processXml(String inputData, String ftlPath, String templateContent, Map<String, Object> extParams) {
        return FreemarkerTransformer.xml2Other(inputData, ftlPath, templateContent, extParams);
    }

    private static  String processJson(String inputData, String ftlPath, String templateContent, Map<String, Object> extParams) {
        return FreemarkerTransformer.json2Other(inputData, ftlPath, templateContent, extParams);
    }

    private static String processMap(Map<String, Object> paramMap, String ftlPath, String templateContent, Map<String, Object> extParams) {
        return FreemarkerTransformer.process(paramMap, ftlPath, templateContent, extParams);
    }
}
