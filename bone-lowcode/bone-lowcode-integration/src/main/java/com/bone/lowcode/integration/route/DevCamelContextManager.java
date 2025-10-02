package com.bone.lowcode.integration.route;

import com.bone.lowcode.integration.flow.camel.CustomHeaderFilterStrategy;
import com.bone.lowcode.integration.flow.camel.ExtendHttpComponent;
import lombok.Data;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Data
public class DevCamelContextManager {

   private CamelContext camelContext = new DefaultCamelContext();

   @PostConstruct
   public void init() {
       camelContext.setManagementName("dev-camel-context");
       camelContext.getCamelContextExtension().setName("dev-camel-context");
       ExtendHttpComponent extendHttpComponent = new ExtendHttpComponent();
       camelContext.addComponent("extend-http", extendHttpComponent);
       camelContext.addComponent("extend-https", extendHttpComponent);
       extendHttpComponent.setHeaderFilterStrategy(new CustomHeaderFilterStrategy());

       try {
           camelContext.addRoutes(new RouteBuilder() {
               @Override
               public void configure() {
//                   onException(Exception.class)
//                           .handled(true)
//                           .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
//                           .process(exchange -> {
//                               Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
//                               String failedNode = exchange.getProperty(Exchange.FAILURE_ENDPOINT, String.class);
//
//                               System.out.println("❌ 出错了！错误节点 ID：" + failedNode);
//                               System.out.println("❌ 错误信息：" + exception.getMessage());
//                           });

                   restConfiguration()
                           .component("netty-http")
                           .host("0.0.0.0")
                           .port(20888)
                           .scheme("http")
                           .bindingMode(RestBindingMode.auto)
                           .enableCORS(true)  // Enable CORS if necessary
                           .dataFormatProperty("prettyPrint", "true")  // Pretty print JSON responses
                           .apiContextPath("/api-doc") // Provide API documentation path
                           .apiProperty("api.title", "Integration-Platform API")
                           .apiProperty("api.version", "1.0");


               }
           });
           camelContext.start();
       } catch (Exception e) {
           throw new RuntimeException(e);
       }
   }
}
