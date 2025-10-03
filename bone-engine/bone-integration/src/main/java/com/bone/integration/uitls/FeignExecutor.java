package com.bone.integration.uitls;

import com.bone.integration.flow.node.FeignNode;
import org.apache.camel.Exchange;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

import static org.apache.camel.builder.Builder.simple;

public class FeignExecutor {


    public static String execute(FeignNode node, Exchange exchange, RestTemplate restTemplate) {

        String serverName = node.getServiceName();
        String path = node.getPath();
        List<Map<String,String>> parameter = node.getParameters();
        String bodyExpression = node.getBody();

        String body = simple(bodyExpression).evaluate(exchange, String.class);
        // 创建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);  // 设置 Content-Type 为 application/json



        // 将数据和头封装到 HttpEntity 中
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        // 使用 UriComponentsBuilder 构建带查询参数的 URL
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl("http://" + serverName +"/"+path);

        // 如果 parameter 不为空，则遍历并添加查询参数
        if (parameter != null && !parameter.isEmpty()) {
            for (Map<String, String> param : parameter) {
                uriBuilder.queryParam(param.get("key"), param.get("value"));
            }
        }

        // 构建最终的 URL
        String url = uriBuilder.build().toUriString();
        System.out.println(url);

        try {
            return restTemplate.postForObject(url, entity, String.class);
        } catch (Exception e) {
            return e.getMessage();
        }

    }
}
