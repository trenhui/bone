package com.bone.lowcode.infra.application.upload;


import cn.hutool.core.io.FileUtil;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.auth.ServiceSignature;
import com.aliyun.oss.common.comm.Protocol;
import com.aliyun.oss.common.comm.RequestMessage;
import com.aliyun.oss.common.utils.HttpHeaders;
import com.aliyun.oss.common.utils.HttpUtil;
import com.aliyun.oss.internal.SignUtils;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.aliyun.oss.internal.OSSConstants.DEFAULT_CHARSET_NAME;
import static com.aliyun.oss.internal.RequestParameters.*;


@Slf4j
@Service
public class OssUtil {
    @Value("${oss.ENDPOINT}")
    private String ENDPOINT;
    @Value("${oss.ACCESSKEYID}")
    private String ACCESSKEYID;
    @Value("${oss.ACCESSKEYSECRET}")
    private String ACCESSKEYSECRET;
    @Value("${oss.BUCKETNAME}")
    private String BUCKETNAME;
    @Value("${oss.KEY}")
    private String KEY;
    @Value("${oss.VISITURL}")
    private String VISITURL;

    private final static String DEFAULT_CHARSET = "utf-8";
    private final static int DEFAULT_TIME_OUT = 60000;

    private static OSSClient ossClientStatic;


    /**
     * 生成授权url
     *
     * @param ossUrl          oss的绝对路径
     * @param accessKeyId
     * @param accessKeySecret
     * @param securityToken
     * @return
     */
    public static String getSignatureUrl(String ossUrl, String accessKeyId, String accessKeySecret, String securityToken) {

        if (StringUtils.isBlank(ossUrl) || !ossUrl.startsWith(Protocol.HTTP.toString())) {
            throw new OSSException("请输入oss绝对路径获取授权");
        }

        try {
            URI uri = new URI(ossUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            String queryString = getQueryString(host, path, accessKeyId, accessKeySecret, securityToken);
            return ossUrl += "?" + queryString;
        } catch (URISyntaxException e) {
            log.error("ossUrl[{}]生成授权url失败 --> {}", ossUrl, e.getMessage(), e);
            throw new OSSException("oss生成授权url失败");
        }
    }


    /**
     * 获取授权url
     *
     * @param path            oss相对路径
     * @param endpoint
     * @param accessKeyId
     * @param accessKeySecret
     * @param bucketName      Bucket名称
     * @return
     */
    public static String getUrl(String path, String bucketName, String endpoint, String accessKeyId, String accessKeySecret, String securityToken) {
        if (StringUtils.isBlank(path)) {
            log.error("oss相对路径不能为空");
            return null;
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        StringBuilder sb = new StringBuilder(bucketName)
                .append(".")
                .append(endpoint);
        String queryString = null;
        try {
            queryString = getQueryString(sb.toString(), path, accessKeyId, accessKeySecret, securityToken);
        } catch (Exception e) {
            log.error("相对路径[{}]生成授权url失败 --> {}", path, e.getMessage(), e);
            throw new OSSException("oss生成授权url失败");
        }

        return new StringBuilder(Protocol.HTTPS.toString())
                .append("://")
                .append(sb.toString())
                .append(path)
                .append("?")
                .append(queryString)
                .toString();
    }


    /**
     * 生成签名
     *
     * @param
     * @return
     */
    private static String getQueryString(String host, String path, String accessKeyId, String accessKeySecret, String securityToken) throws URISyntaxException {

        String bucketName = StringUtils.substringBefore(host, ".");

        String expires = String.valueOf((System.currentTimeMillis() + 3600 * 1000L) / 1000L);
        RequestMessage requestMessage = new RequestMessage();
        //requestMessage.setAbsoluteUrl(bucketName);
        requestMessage.setResourcePath(path);
        requestMessage.setMethod(HttpMethod.GET);
        requestMessage.addHeader(HttpHeaders.DATE, expires);
        requestMessage.addParameter(SECURITY_TOKEN, securityToken);

        requestMessage.setEndpoint(new URI(Protocol.HTTPS.toString() + "//" + host));

        String canonicalString = SignUtils.buildCanonicalString(HttpMethod.GET.toString(),
                "/" + StringUtils.substringBefore(host, ".") + path,
                requestMessage, expires);
        String signature = ServiceSignature.create().computeSignature(accessKeySecret, canonicalString);

        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put(HttpHeaders.EXPIRES, expires);
        params.put(OSS_ACCESS_KEY_ID, accessKeyId);
        params.put(SIGNATURE, signature);
        params.putAll(requestMessage.getParameters());
        return HttpUtil.paramToQueryString(params, DEFAULT_CHARSET_NAME);
    }

    /**
     * 上传文件
     *
     * @param path            oss相对路径
     * @param inputStream     文件流
     * @param bucketName
     * @param endpoint
     * @param accessKeyId
     * @param accessKeySecret
     * @param securityToken
     * @return
     */
    public static String upload(String path, InputStream inputStream, String bucketName, String endpoint, String accessKeyId, String accessKeySecret, String securityToken) {
        if (StringUtils.isBlank(path)) {
            log.error("oss上传路径path不能为空");
            return null;
        }
        try {
            // 创建PutObject请求。
            ossClientStatic.putObject(bucketName, path, inputStream);
        } catch (Exception e) {
            log.error("oss文件上传为[{}]文件失败 ---> {}", e.getMessage(), e);
            return null;
        }
        return new StringBuilder(Protocol.HTTPS.toString())
                .append("://")
                .append(bucketName)
                .append(".")
                .append(endpoint)
                .append("/")
                .append(path)
                .toString();
    }


    /**
     * 下载文件到的io流
     *
     * @param fileUrl 内网url
     * @return
     */
    public static InputStream downloadForIs(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        URLConnection urlConnection = url.openConnection();
        HttpURLConnection connection = (HttpURLConnection) urlConnection;
        connection.setRequestProperty("Charset", "UTF-8");
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (HttpStatus.SC_OK != responseCode) {
            log.error("url错误,文件不存在, url为 --> {}", fileUrl);
            throw new IOException("url错误,文件不存在");
        }
        int fileLength = connection.getContentLength();
        log.info("下载文件[{}]: 大小[{}]", fileUrl, fileLength);
        return connection.getInputStream();
    }

    /**
     * 下载文件到本地
     *
     * @param fileUrl  内网url
     * @param filePath
     * @return
     */
    public static File download(String fileUrl, String filePath) {
        BufferedInputStream is = null;
        FileOutputStream fos = null;
        try {
            URL url = new URL(fileUrl);
            File file = new File(filePath);
            URLConnection urlConnection = url.openConnection();
            HttpURLConnection connection = (HttpURLConnection) urlConnection;
            connection.setRequestProperty("Charset", "UTF-8");
            connection.connect();
            int fileLength = connection.getContentLength();
            log.info("下载文件[{}]: 大小[{}]", fileUrl, fileLength);
            is = new BufferedInputStream(connection.getInputStream());
            byte[] buffer = new byte[1024];
            int len;
            int size = 0;
            fos = new FileOutputStream(file);
            while ((len = is.read(buffer)) != -1) {
                len += size;
                fos.write(buffer, 0, len);
            }
            return file;
        } catch (Exception e) {
            log.error("下载文件[{}]失败: {}", fileUrl, e.getMessage(), e);
            throw new OSSException("下载文件失败");
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }


    @PostConstruct
    public void buildOssClient() {
        ossClientStatic = new OSSClient(ENDPOINT, ACCESSKEYID, ACCESSKEYSECRET);
    }




    public String uploadImageToOSS(String filePath, String claimNumber) {
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }
        String name = file.getName();
        String[] split = name.split(".");
        //上传文件路径及文件名称  KEY + 年月日 + 赔案号 + 文件名称
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Date date = new Date();
        String format = sdf.format(date);
        String uploadFilePath = KEY + format + "/" + claimNumber + "/" + name;
        // OSSClient ossClient = new OSSClient(ENDPOINT, ACCESSKEYID, ACCESSKEYSECRET);
        try {
            ossClientStatic.putObject(BUCKETNAME, uploadFilePath, new FileInputStream(filePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        // ossClient.shutdown();
        return ENDPOINT + "/" + uploadFilePath;
    }


    public String uploadImageToOSS(String fileName, InputStream is, Long claimNumber) {

        String name = fileName;
        String[] split = name.split(".");
        //上传文件路径及文件名称  KEY + 年月日 + 赔案号 + 文件名称
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Date date = new Date();
        String format = sdf.format(date);
        String uploadFilePath = KEY + format + "/" + claimNumber + "/" + name;
        //创建上传Object的Metadata
        ObjectMetadata metadata = new ObjectMetadata();
        //上传的文件的长度
        try {
            metadata.setContentLength(is.available());
        } catch (IOException e) {
            e.printStackTrace();
        }
        // OSSClient ossClient = new OSSClient(ENDPOINT, ACCESSKEYID, ACCESSKEYSECRET);
        ossClientStatic.putObject(BUCKETNAME, uploadFilePath, is, metadata);
        // ossClientStatic.shutdown();
        return VISITURL + uploadFilePath;
    }

    public String upload(String fileName, InputStream is, String filePath) {
        //上传文件路径及文件名称  KEY + 年月日 + 赔案号 + 文件名称
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Date date = new Date();
        String format = sdf.format(date);
        String uploadFilePath = KEY + format + "/" + filePath + "/" + fileName;
        try {
            // 创建上传Object的Metadata
            ObjectMetadata objectMetadata = new ObjectMetadata();
            // 上传文件 (上传文件流的形式)
            PutObjectResult putResult = ossClientStatic.putObject(BUCKETNAME, uploadFilePath, is, objectMetadata);
        } catch (Exception e) {
            throw new SecurityException(e.getMessage());
        }
        return VISITURL + uploadFilePath;
    }

    //下载文件
    public void download(String objectName,String file,String files){
        try {
            File file1 = new File(files);
            if (!file1.exists()) {
                file1.mkdirs();
            }
            ossClientStatic.getObject(new GetObjectRequest(BUCKETNAME,file),new File(files + "/" + objectName));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean delete(String key) {
        // OSSClient ossClient = new OSSClient(ENDPOINT, ACCESSKEYID, ACCESSKEYSECRET);
        ossClientStatic.deleteObject(BUCKETNAME, key);
        return true;
    }

    /**
     * 根据文件url获取输入流
     * @param fileUrl
     * @return
     */
    public InputStream getInputStream(String fileUrl) {
        InputStream input = null;
        try {
            byte[] content = readFileByUrlNo(fileUrl);
            input = new ByteArrayInputStream(content);
        }catch (Exception e) {

        }
        return input;
    }

    public static byte[] readFileByUrlNo(String urlStr) {
        InputStream is = null;
        ByteArrayOutputStream os = null;
        byte[] buff = new byte[1024];
        int len = 0;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Content-Type", "plain/text;charset=" + DEFAULT_CHARSET);
            conn.setRequestProperty("charset", DEFAULT_CHARSET);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.setReadTimeout(DEFAULT_TIME_OUT);
            conn.connect();
            is = conn.getInputStream();
            os = new ByteArrayOutputStream();
            while ((len = is.read(buff)) != -1) {
                os.write(buff, 0, len);
            }
            return os.toByteArray();
        } catch (IOException e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public String uploadFile(String path, String fileName,  InputStream is) {
        //上传文件路径及文件名称  KEY + 年月日 + 赔案号 + 文件名称
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Date date = new Date();
        String format = sdf.format(date);
        String uploadFilePath = path + format + "/"  + fileName;
        try {
            // 创建上传Object的Metadata
            ObjectMetadata objectMetadata = new ObjectMetadata();
            // 上传文件 (上传文件流的形式)
            OSSClient ossClientStatic = new OSSClient(ENDPOINT, ACCESSKEYID, ACCESSKEYSECRET);
            PutObjectResult putResult = ossClientStatic.putObject(BUCKETNAME, uploadFilePath, is, objectMetadata);
        } catch (Exception e) {
            throw new SecurityException(e.getMessage());
        }
        return VISITURL + uploadFilePath;
    }

    /**
     * 压缩文件夹
     *
     * @param zipFileName  打包后文件的名称，含路径
     * @param sourceFolder 需要打包的文件夹或者文件的路径
     * @param zipPathName  打包目的文件夹名,为空则表示直接打包到根
     */
    public static void zip(String zipFileName, String sourceFolder, String zipPathName) throws Exception {
        ZipOutputStream out = null;
        try {
            File zipFile = new File(zipFileName);
            FileUtil.mkdir(zipFile.getParent());
            FileOutputStream outputStream = new FileOutputStream(zipFile);
            out = new ZipOutputStream(outputStream);
            if (StringUtils.isNotBlank(zipPathName)) {
                zipPathName = FilenameUtils.normalizeNoEndSeparator(zipPathName, true) + "/";
            } else {
                zipPathName = "";
            }
            zip(out, sourceFolder, zipPathName);
        } catch (IOException e) {
            e.printStackTrace();
            throw new Exception(e);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    private static void zip(ZipOutputStream zos, String file, String pathName) throws IOException {
        File file2zip = new File(file);
        if (file2zip.isFile()) {
            zos.putNextEntry(new ZipEntry(pathName + file2zip.getName()));
            IOUtils.copy(new FileInputStream(file2zip.getAbsolutePath()), zos);
            zos.flush();
            zos.closeEntry();
        } else {
            File[] files = file2zip.listFiles();
            if (org.apache.commons.lang3.ArrayUtils.isNotEmpty(files)) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        zip(zos, FilenameUtils.normalizeNoEndSeparator(f.getAbsolutePath(), true),
                                FilenameUtils.normalizeNoEndSeparator(pathName + f.getName(), true) + "/");
                    } else {
                        FileInputStream inputStream = new FileInputStream(f.getAbsolutePath());
                        zos.putNextEntry(new ZipEntry(pathName + f.getName()));
                        IOUtils.copy(inputStream, zos);
                        inputStream.close();
                        zos.flush();
                        zos.closeEntry();
                    }
                }
            }else{
                // 空文件夹的处理
                zos.putNextEntry(new ZipEntry(pathName + "/"));
                // 没有文件，不需要文件的copy
                zos.closeEntry();
            }
        }
    }


    public static void main(String[] args) {
        String s = "https://bucket-pktest.oss-cn-hangzhou.aliyuncs.com/dst/1671696209311_1/225200380001/身份证.jpg";
        int j = s.indexOf("com/");
        String coverCopyFile = s.substring(j + 4);
        System.out.println(coverCopyFile);
        String a = "19980720";
        String b = a.substring(0,4);
        String c = a.substring(4,6);
        String d = a.substring(6,8);
        System.out.println(b);
        System.out.println(c);
        System.out.println(d);
    }


    /**
     * 创建zip文件
     * @param sourceFilePath
     * @return byte[]
     * @throws Exception
     */
    public static byte[] createZip(String sourceFilePath) throws Exception{
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(outputStream);
        // 将目标文件打包成zip导出
        File file = new File(sourceFilePath);
        handlerFile(zip, file,"");
        // 无异常关闭流，它将无条件的关闭一个可被关闭的对象而不抛出任何异常。
        IOUtils.closeQuietly(zip);
        return outputStream.toByteArray();
    }



    /**
     * 打包处理
     * @param zip
     * @param file
     * @param dir
     * @throws Exception
     */
    private static void handlerFile(ZipOutputStream zip, File file, String dir) throws Exception {
        // 如果当前的是文件夹，则循环里面的内容继续处理
        if (file.isDirectory()) {
            //得到文件列表信息
            File[] fileArray = file.listFiles();
            if (fileArray == null) {
                return;
            }
            //将文件夹添加到下一级打包目录
            zip.putNextEntry(new ZipEntry(dir + "/"));
            dir = dir.length() == 0 ? "" : dir + "/";
            // 递归将文件夹中的文件打包
            for (File f : fileArray) {
                handlerFile(zip, f, dir + f.getName());
            }
        } else {
            // 如果当前的是文件，打包处理
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            ZipEntry entry = new ZipEntry(dir);
            zip.putNextEntry(entry);
            zip.write(FileUtils.readFileToByteArray(file));
            IOUtils.closeQuietly(bis);
            zip.flush();
            zip.closeEntry();
        }
    }
}
