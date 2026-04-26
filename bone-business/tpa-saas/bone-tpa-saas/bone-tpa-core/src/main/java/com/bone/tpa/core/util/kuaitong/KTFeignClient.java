package com.bone.tpa.core.util.kuaitong;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

//调用快瞳的接口
@FeignClient(url = "https://ai.inspirvision.cn", name = "kt-server", configuration = KTFeignConfig.class)
public interface KTFeignClient {

    //获取快瞳token
    @GetMapping("/s/api/getAccessToken")
    String getToken(@RequestParam("accessKey") String accessKey, @RequestParam("accessSecret") String accessSecret);


    //快瞳电子发票查验接口
    @PostMapping(value = "/s/api/ocr/invoiceCheckAll", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    String invoiceCheck(@RequestParam("imgUrl") String imgUrl,
                        @RequestPart("file") MultipartFile file,
                        @RequestParam("token") String token,
                        @RequestParam(value = "isPlateFile", defaultValue = "1") String isPlateFile,
                        @RequestParam(value = "needPdf", defaultValue = "1") String needPdf);
}
