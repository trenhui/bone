package com.bone.tpa.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Component
public class ALiYunOSSUtil {

    @Value("${oss.ENDPOINT:https://oss-cn-hangzhou.aliyuncs.com}")
    private String endpoint;

    @Value("${oss.ACCESSKEYID:LTAI4G1eXunQDKHkp3E3pxWz}")
    private String accessKeyId;

    @Value("${oss.ACCESSKEYSECRET:Fu4uhBw8suvrPGQ4dQHhhfGNIZIqqK}")
    private String accessKeySecret;

    @Value("${oss.BUCKETNAME:bucket-pktest}")
    private String bucketName;

    /**
     * 文件上传
     */
    public String upload(InputStream inputStream, String fileNameSuffix) {
        OSS ossClient = null;
        try {
            ossClient = new OSSClient(endpoint, accessKeyId, accessKeySecret);
            String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String objectName = "tpaSaas/" + dateStr + "/" + fileNameSuffix;

            PutObjectRequest request = new PutObjectRequest(bucketName, objectName, inputStream);
            ossClient.putObject(request);
            if(endpoint.startsWith("http://")) {
                return endpoint.replaceFirst("http://", "https://" + bucketName + ".") + "/" + objectName;
            } else {
                return endpoint.replaceFirst("https://", "https://" + bucketName + ".") + "/" + objectName;
            }
        } catch (Exception e) {
            log.error("上传文件到oss发生异常,文件名后缀:{}", fileNameSuffix, e);
            return null;
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}
