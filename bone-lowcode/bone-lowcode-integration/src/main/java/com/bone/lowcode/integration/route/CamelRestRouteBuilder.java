package com.bone.lowcode.integration.route;

import com.bone.lowcode.integration.flow.camel.CustomHeaderFilterStrategy;
import com.bone.lowcode.integration.flow.camel.ExtendHttpComponent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CamelRestRouteBuilder extends RouteBuilder {
    @Resource
    private CamelContext camelContext;

    @Override
    public void configure() throws Exception {
        camelContext.getCamelContextExtension().setName("prod-camel-context");
//        onException(Exception.class)
//                .handled(true)
//                .log("Error encountered while processing request: ${exception}, ${exception}")
//                .setBody(simple("{\"error\":\"${exception.message}\"}"))
//                .setHeader("CamelHttpResponseCode", constant(HttpStatus.INTERNAL_SERVER_ERROR.value()));

//        onException(Exception.class)
//                .handled(true)
//                .process(exchange -> {
//                    Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
//                    String failedNode = exchange.getProperty(Exchange.FAILURE_ENDPOINT, String.class);
//
//                    System.out.println("❌ 出错了！错误节点 ID：" + failedNode);
//                    System.out.println("❌ 错误信息：" + exception.getMessage());
//                });

        ExtendHttpComponent component = new ExtendHttpComponent();
        camelContext.addComponent("extend-http", component);
        camelContext.addComponent("extend-https", component);
        component.setHeaderFilterStrategy(new CustomHeaderFilterStrategy());

        // Configure REST to use JSON binding
        restConfiguration()
                .component("netty-http")
                .host("0.0.0.0")
                .port(10888)
                .scheme("http")
                .bindingMode(RestBindingMode.auto)
                .enableCORS(true)  // Enable CORS if necessary
                .dataFormatProperty("prettyPrint", "true")  // Pretty print JSON responses
                .apiContextPath("/api-doc") // Provide API documentation path
                .apiProperty("api.title", "Integration-Platform API")
                .apiProperty("api.version", "1.0");

        log.info("configure camel rest done.");
    }


}
