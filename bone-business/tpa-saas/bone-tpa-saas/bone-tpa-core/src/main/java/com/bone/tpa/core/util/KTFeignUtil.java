package com.bone.tpa.core.util;

import com.bone.tpa.core.util.kuaitong.KTFeignClient;
import com.bone.tpa.core.util.kuaitong.KTProperties;
import com.bone.tpa.core.util.kuaitong.KTResponseHandle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Slf4j
@Component
public class KTFeignUtil {

    @Autowired
    private KTProperties ktProperties;

    @Autowired
    private KTFeignClient ktFeignClient;


    private static String token = "";

    public String getToken() {
        if (StringUtils.hasText(token)) {
            return token;
        }

        String res = ktFeignClient.getToken(ktProperties.getACCESS_KEY(), ktProperties.getACCESS_SECRET());
        String tokenStr = KTResponseHandle.getToken(res);
        if (!StringUtils.hasText(tokenStr)) {
            throw new RuntimeException("获取token发生异常,快瞳响应:" + res);
        }
        token = tokenStr;
        return tokenStr;
    }

    public String invoiceCheckAllByUrl(String imgUrl, String token) {
        String re = null;
        try {
            re = ktFeignClient.invoiceCheck(imgUrl, null, token, "1", "1");
        } catch (Exception e) {
            log.info("通过url参数调用快瞳接口发生异常:", e);
            if (e.getMessage().contains("[401]")) {
                log.info("token失效,重新获取token并请求, imgUrl:{}", imgUrl);
                String res = ktFeignClient.getToken(ktProperties.getACCESS_KEY(), ktProperties.getACCESS_SECRET());
                String newToken = KTResponseHandle.getToken(res);
                if (!StringUtils.hasText(newToken)) {
                    throw new RuntimeException("重新获取token发生异常,快瞳响应:" + res);
                }
                KTFeignUtil.token = newToken;
                re = ktFeignClient.invoiceCheck(imgUrl, null, KTFeignUtil.token, "1", "1");
            } else {
                throw new RuntimeException(e.getMessage());
            }
        }
        return re;
    }

    public String invoiceCheckAllByFile(File file, String token) {
        String re = null;
        MultipartFile multipartFile = null;
        try {
            multipartFile = ImageUtil.fileToMultipartFile(file);
            re = ktFeignClient.invoiceCheck(null, multipartFile, token, "1", "1");
        } catch (Exception e) {
            log.info("通过文件参数调用快瞳接口发生异常:", e);
            if (e.getMessage().contains("[401]")) {
                log.info("token失效，重新获取token并请求");
                String res = ktFeignClient.getToken(ktProperties.getACCESS_KEY(), ktProperties.getACCESS_SECRET());
                String newToken = KTResponseHandle.getToken(res);
                if (!StringUtils.hasText(newToken)) {
                    throw new RuntimeException("重新获取token发生异常,快瞳响应:" + res);
                }
                KTFeignUtil.token = newToken;
                re = ktFeignClient.invoiceCheck(null, multipartFile, KTFeignUtil.token, "1", "1");
            } else {
                throw new RuntimeException(e.getMessage());
            }
        }
        return re;
    }
}
