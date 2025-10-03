package com.bone.integration.interfaces;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

//Feign指定客户端接口
@FeignClient(name = "CLIENT-A")
public interface ClientAFeign {

    @GetMapping("/add")
    String add();

}

