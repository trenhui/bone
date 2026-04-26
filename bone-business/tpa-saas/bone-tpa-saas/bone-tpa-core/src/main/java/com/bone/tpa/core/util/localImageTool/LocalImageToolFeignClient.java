package com.bone.tpa.core.util.localImageTool;

import com.bone.tpa.core.util.localImageTool.request.ClassifyParam;
import com.bone.tpa.core.util.localImageTool.response.ImageToolResponse;
import com.bone.tpa.facade.FeignTpaConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

//调用本地图片处理工具的接口
@FeignClient(url = "${pk.imageTool:http://47.104.17.249:5000}", name = "localImageTool-server", configuration = FeignTpaConfig.class)
public interface LocalImageToolFeignClient {

    //图片分类
    @PostMapping(value = "/api/infer/classifyPredict", consumes = "application/json")
    ImageToolResponse classify(@RequestBody ClassifyParam param);

    //图片旋转
    @PostMapping(value = "/api/infer/clsPredict", consumes = "application/json")
    ImageToolResponse rotate(@RequestBody ClassifyParam param);
}
