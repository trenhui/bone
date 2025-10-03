package com.bone.integration.flow.convertor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.lang3.StringUtils;

import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class FreemarkerTransformer {
    private final static Configuration configuration;
    static {
        configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setClassForTemplateLoading(FreemarkerTransformer.class, "/");
        configuration.setDefaultEncoding("UTF-8");
        // 处理数字，禁止自动增加金额分隔符
        configuration.setNumberFormat("#0.###");
        // 处理null值，输出为空
        configuration.setClassicCompatible(true);
    }

    public static void main(String[] args) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "coco");
        data.put("date", null);
        String content = "name: ${name}, date: ${date}";

        String str = process(data, content);
        System.out.println(str);
    }

    public static String json2Other(String json, String ftlPath, String templateContent, Map<String, Object> extParams) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return process(map, ftlPath, templateContent, extParams);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String xml2Other(String xml, String ftlPath, String templateContent, Map<String, Object> extParams) {
        try {
            XmlMapper xmlMapper = XmlMapper.xmlBuilder().build();
            Map<String, Object> map = xmlMapper.readValue(xml, Map.class);
            return process(map, ftlPath, templateContent, extParams);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String process(Map<String, Object> paramContext, String templateContent) {
        return process(paramContext, null, templateContent, null);
    }

    public static String process(Map<String, Object> paramContext, String ftlPath, String templateContent, Map<String, Object> extParams) {
        Map<String, Object> context = new HashMap<>(paramContext);
        if (extParams != null) {
            context.putAll(extParams);
        }

        try {
            Template template;
            if (StringUtils.isNotBlank(ftlPath)) {
                template = configuration.getTemplate(ftlPath);
            } else {
                template = new Template("templateName", templateContent, configuration);
            }

            Writer out = new StringWriter();
            template.process(context, out);

            return out.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
