package com.bone.integration.interfaces;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("feignTest")
@Slf4j
public class FeignTestController {

    @Resource
    private ClientAFeign clientAFeign;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DiscoveryClient discoveryClient;

    @GetMapping("/hello")
    @ResponseBody
    public String getHello() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "Hello World");
        map.put("sex", 1);
        map.put("score", 22.12);

        // 创建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);  // 设置 Content-Type 为 application/json

        // 将数据和头封装到 HttpEntity 中
        HttpEntity<Map> entity = new HttpEntity<>(map, headers);

        // 使用 UriComponentsBuilder 构建带查询参数的 URL
        String url = UriComponentsBuilder.fromHttpUrl("http://CLIENT-A/update")
                .queryParam("tenantCode", "PK")
                .build()
                .toUriString();

        try {
            return restTemplate.postForObject(url, entity, String.class);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @GetMapping("/services")
    @ResponseBody
    public List<String> getServices() {
        return discoveryClient.getServices();
    }

    @RequestMapping("test")
    @ResponseBody
    public String test() {
        try {
            String result = clientAFeign.add();
            return result;

        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
