//package com.bone.lowcode.integration.processor;
//
//import com.bone.lowcode.integration.config.InterfaceConfig;
//import com.bone.lowcode.integration.transformation.output.OutputConverter;
//import org.apache.camel.Exchange;
//import org.apache.camel.Processor;
//import org.springframework.stereotype.Component;
//
//@Component
//public class OutputResponseProcessor implements Processor {
//
//    private final OutputConverter outputConverter;
//
//    public OutputResponseProcessor(OutputConverter outputConverter) {
//        this.outputConverter = outputConverter;
//    }
//
//    @Override
//    public void process(Exchange exchange) throws Exception {
//        // 获取请求头中的动态数据
//        String partnerName = exchange.getIn().getHeader("partnerName", String.class);
//        Integer responseCode = exchange.getIn().getHeader("CamelHttpResponseCode", Integer.class);
//
//        InterfaceConfig interfaceConfig = exchange.getIn().getHeader("interfaceConfig", InterfaceConfig.class);
//        Object inputData = exchange.getIn().getBody(String.class);
//        Object responseResult = outputConverter.parse(interfaceConfig, inputData);
//        //todo
//        // 设置响应体（Camel 自动将 Map 转为 JSON 格式）
//        exchange.getIn().setBody(responseResult);
//    }
//}
