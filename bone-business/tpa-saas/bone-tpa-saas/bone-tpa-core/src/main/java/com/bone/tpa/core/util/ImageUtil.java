package com.bone.tpa.core.util;

import com.bone.core.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.resizers.configurations.Antialiasing;
import org.apache.commons.io.FileUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

@Slf4j
public class ImageUtil {

    /**
     * 压缩图片,固定缩放比例、压缩质量
     */
    public static File compressImage(String imageUrl) {
        return compressImage(imageUrl, 0.7, 0.8);
    }

    /**
     * 压缩图片
     *
     * @param imageUrl 输入图片路径
     * @param scale    缩放比例（0.0-1.0，1为原尺寸）
     * @param quality  压缩质量（0.0-1.0，1为最高质量）
     */
    public static File compressImage(String imageUrl, double scale, double quality) {
        int index = imageUrl.lastIndexOf("/");
        if (index < 0 || index == imageUrl.length() - 1) {
            throw new ServiceException(500, "图片路径异常:" + imageUrl);
        }

        String fileName = imageUrl.substring(index + 1);
        String targetPath = System.getProperty("user.dir") + File.separator + "temFile" + File.separator + fileName;
        File targetFile = new File(targetPath);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             InputStream inputStream = getFileInputStream(imageUrl)) {
            Thumbnails.of(inputStream)
                    .scale(scale)
                    .antialiasing(Antialiasing.ON)
                    .outputQuality(quality)
                    .toOutputStream(outputStream);
            FileUtils.writeByteArrayToFile(targetFile, outputStream.toByteArray());
        } catch (Exception e) {
            log.info("压缩图片发生异常, 图片url:" + imageUrl, e);
            throw new RuntimeException("压缩图片发生异常");
        }
        return targetFile;
    }

    /**
     * 从网络文件获取输入流
     */
    public static InputStream getFileInputStream(String path) throws IOException {
        URL url = new URL(path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        //设置超时间为3秒
        conn.setConnectTimeout(3 * 1000);
        //防止屏蔽程序抓取而返回403错误
        conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
        //得到输入流
        return conn.getInputStream();
    }

    /**
     * 获取文件大小,单位:byte
     */
    public static long getFileLength(String fileUrl) throws IOException {
        if (!StringUtils.hasText(fileUrl)) {
            return 0L;
        }

        URL url = new URL(fileUrl);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows 7; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.73 Safari/537.36 YNoteCef/5.8.0.1 (Windows)");
            return conn.getContentLength();
        } catch (IOException e) {
            log.info("获取文件大小发生异常", e);
            return 0L;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * File对象转MultipartFile对象
     */
    public static MultipartFile fileToMultipartFile(File file) throws IOException {
        return new CustomMultipartFile(file);
    }

    private static class CustomMultipartFile implements MultipartFile {
        private final File file;
        private final byte[] content;
        private final String contentType;

        public CustomMultipartFile(File file) throws IOException {
            this.file = file;
            this.content = Files.readAllBytes(file.toPath());
            this.contentType = Files.probeContentType(file.toPath());
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return file.getName();
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
