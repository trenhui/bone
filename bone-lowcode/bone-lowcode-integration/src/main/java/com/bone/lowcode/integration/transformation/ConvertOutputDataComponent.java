//package com.bone.lowcode.integration.transformation;
//
//import com.bone.lowcode.integration.config.InterfaceConfig;
//import com.yomahub.liteflow.annotation.LiteflowComponent;
//import com.yomahub.liteflow.core.NodeComponent;
//import com.bone.lowcode.integration.config.DataFormat;
//
//@LiteflowComponent("convertOutputData")
//public class ConvertOutputDataComponent extends NodeComponent {
//
//    @Override
//    public void process() throws Exception {
//        // 获取目标输出格式和处理后的数据
//        InterfaceConfig interfaceConfig = this.getContextBean(InterfaceConfig.class);
//        Object processedData = this.getSlot().getOutput("processedData");
//        DataFormat outputFormat =  interfaceConfig != null ? interfaceConfig.getInputFormat() : DataFormat.JSON;
//
//        // 根据输出格式转换
//        switch (outputFormat) {
//            case JSON:
//                System.out.println("Converting data to JSON format...");
//                // 模拟转换为 JSON
//                break;
//            case XML:
//                System.out.println("Converting data to XML format...");
//                // 模拟转换为 XML
//                break;
//            case JAVA:
//                System.out.println("Returning processed Java object...");
//                // 返回 Java 对象
//                break;
//            default:
//                throw new IllegalArgumentException("Unsupported output format: " + outputFormat);
//        }
//
//        // 设置转换后的数据为最终输出
//        this.getSlot().setOutput("outputData", processedData);
//        this.getSlot().setResponseData(processedData);
//    }
//}