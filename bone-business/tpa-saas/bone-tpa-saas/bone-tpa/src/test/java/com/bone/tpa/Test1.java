package com.bone.tpa;

import com.bone.tpa.adjustment.application.LiabilityMappingApplicationService;
import com.bone.tpa.claim.application.CreateCertificateApplicationService;
import com.bone.tpa.claim.domain.service.ClaimImageService;
import com.bone.tpa.core.util.KTFeignUtil;
import com.bone.tpa.core.util.localImageTool.LocalImageToolFeignClient;
import com.bone.tpa.core.util.localImageTool.request.ClassifyParam;
import com.bone.tpa.core.util.localImageTool.response.ImageToolResponse;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.task.impl.AutoInputTrigger;
import com.bone.tpa.task.impl.AutoPreExameTrigger;
import com.bone.tpa.task.service.impl.ImageOcrChangeServiceImpl;
import com.bone.tpa.task.service.vo.ImageOcrChangeResult;
import com.bone.tpa.test.BaseTest;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Test1 extends BaseTest {

    @Autowired
    private KTFeignUtil ktFeignUtil;

    @Autowired
    private ImageOcrChangeServiceImpl imageOcrChangeService;

    @Autowired
    private AutoPreExameTrigger autoPreExameTrigger;

    @Autowired
    private LiabilityMappingApplicationService liabilityMappingApplicationService;

    @Autowired
    private CreateCertificateApplicationService createCertificateApplicationService;

    //    @Test
    public void test1() {
        String token = ktFeignUtil.getToken();
        System.out.println("token = " + token);
    }

    //    @Test
    public void test2() {
        String token = "a62c56ace614a6546191d5af8ca8b1513cfaeaea7ce67d0a37de994ab6c2aa4e2a0b058e0da575ff376dd51dc19c5ad353ab2761cb6d9db4d521b83adeee2979b78f7ae70765b26985165b6266d084b75f2f918008966e72a116d8bca5ec4c7cecc5223f78fa47b4d40aa9cf5277a11b0b967ad06e84ef7c4acbc53ccdef936cd77106e03bf11326969573fa6d315d16f9e2cfed2cfa2ed616f97d31e4f058bc3e5da899025a28c667ee8ef9f775fa4f3d2f486dc06f0689b99abbb78a8ebf4a625f690aa5c747b1c1f87da48603ff6fbb1c3560cc6bcaa47daf4c248a1dc238e4e92211215e35f1e02ae60e8140d060";

        String url = "https://bucket-pktest.oss-cn-hangzhou.aliyuncs.com/dst/20250331_1743389914519_245200004/245200004001/lQDPJxh1uBUHsSHNDbDNCbCwiKyGD8cs52QHGBViEzXPAA_2480_3504.jpg";
        String re = ktFeignUtil.invoiceCheckAllByUrl(url, token);
        System.out.println("re = " + re);

        File file = new File("C:\\Users\\ycy\\Desktop\\p1 (2).jpg");
        String s = ktFeignUtil.invoiceCheckAllByFile(file, token);
        System.out.println("s = " + s);
    }

    //    @Test
    public void test3() {
        String s1 = "https://bucket-pktest.oss-cn-hangzhou.aliyuncs.com/dst/20250331_1743389914519_245200004/245200004001/lQDPJxh1uBUHsSHNDbDNCbCwiKyGD8cs52QHGBViEzXPAA_2480_3504.jpg";//<2M
        String s2 = "https://bucket-pktest.oss-cn-hangzhou.aliyuncs.com/fapiao_image_2994.jpg";//6M

        String s = imageOcrChangeService.callKT(s1);
        System.out.println("s = \n" + s);
    }

    @Autowired
    private ClaimImageService claimImageService;

    //    @Test
    public void test4() {
        Long claimNumber = 245200335001L;
        ClaimImage image = claimImageService.getById(1929745017757413376L);
        ImageOcrChangeResult result = imageOcrChangeService.changeImageOcr(claimNumber, image);
        System.out.println("result = " + result);
    }

    //    @Test
    public void test5() {
        Long claimNumber = 245200335001L;
        ClaimImage image = claimImageService.getById(1929745017639972864L);
        ImageOcrChangeResult result = imageOcrChangeService.changeImageOcr(claimNumber, image);
        System.out.println("result = " + result);
    }

    @Autowired
    private AutoInputTrigger autoInputTrigger;

    //    @Test
    public void test6() {
        Long claimNumber = 245200335001L;
//        SyncTask task = new SyncTask();
//        task.setData(claimNumber.toString());
        autoInputTrigger.doAutoInput(claimNumber);
    }

    @Autowired
    private LocalImageToolFeignClient imageToolFeignClient;

    //    @Test
    public void test7() {
        String url = "https://bucket-tpa.oss-cn-qingdao.aliyuncs.com/tpaImage/20221203/150204195709120029/R16B21244202200009789/cc05a4c2-70c0-41d8-8c69-5be6602a1a04.png";
        ImageToolResponse response = imageToolFeignClient.classify(new ClassifyParam(url));
        System.out.println("response = " + response);
    }

    //    @Test
    public void test8() {
        String url = "https://bucket-tpa.oss-cn-qingdao.aliyuncs.com/tpaImage/20221203/150204195709120029/R16B21244202200009789/cc05a4c2-70c0-41d8-8c69-5be6602a1a04.png";
        ImageToolResponse response = imageToolFeignClient.rotate(new ClassifyParam(url));
        System.out.println("response = " + response);
        boolean success = response.success();
        System.out.println("success = " + success);
    }

    //    @Test
    public void test9() {
        autoPreExameTrigger.doAutoFirstExam(245200335001L);
    }

    //    @Test
    public void test10() {
        liabilityMappingApplicationService.updateLiabilityMapping(1983835158855483392L);
    }

    private static HashMap<String, Object> getDataMapOld() {
        HashMap<String, Object> map1 = new HashMap<>();
        map1.put("invoiceNo", "invoiceNo123");
        map1.put("liveStartDate", "2025-11-20");
        map1.put("compensationAmount", "103.00");
        map1.put("responsibilityName", "责任名1");
        map1.put("formulaValue", "1+1=2");

        HashMap<String, Object> map2 = new HashMap<>();
        map2.put("invoiceNo", "invoiceNo123abc");
        map2.put("liveStartDate", "2025-11-21");
        map2.put("compensationAmount", "1003.00");
        map2.put("responsibilityName", "责任名2");
        map2.put("formulaValue", "2+2=4");

        HashMap<String, Object> dataMap = new HashMap<>();
        dataMap.put("outInsureName", "测试姓名1");
        dataMap.put("invoiceList", new ArrayList<>(Arrays.asList(map1, map2)));
        dataMap.put("gyTaskNo", "测试报案号123");
        dataMap.put("compensationAmount", "1000.57");
        dataMap.put("compensationAmountChinese", "一千元五毛七分");
        return dataMap;
    }

    //原始
    public static void test22() throws Exception {
        byte[] alliyImg = null;
        long l = System.currentTimeMillis();
        try (InputStream pdfStream = new ClassPathResource("certificateTemplate/yc_lpsqs.pdf").getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ByteArrayOutputStream imageOutput = new ByteArrayOutputStream()) {
            PdfReader reader = null;
            PdfStamper stamper = null;

            try {
                reader = new PdfReader(pdfStream);
                stamper = new PdfStamper(reader, bos);
                AcroFields form = stamper.getAcroFields();
                BaseFont baseFont = BaseFont.createFont("STSongStd-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
                form.addSubstitutionFont(baseFont);
                stamper.setFormFlattening(true);
                if (null != alliyImg) {
                    PdfContentByte content = stamper.getOverContent(1);
                    Image img1 = Image.getInstance(alliyImg);
                    img1.scaleToFit(60, 60);
                    Rectangle imageFieldRect1 = form.getFieldPositions("img1").get(0).position;
                    float x1 = imageFieldRect1.getLeft() + (imageFieldRect1.getWidth() - img1.getScaledWidth()) / 2;
                    float y1 = imageFieldRect1.getBottom() + (imageFieldRect1.getHeight() - img1.getScaledHeight()) / 2;
                    img1.setAbsolutePosition(x1, y1);
                    content.addImage(img1); //将图片添加到PDF中
                }

                Map<String, Object> fieldData = new HashMap<>();
                fieldData.put("outInsureName", "张三丰");
                fieldData.put("invoiceBeginDate", "2025-11-17");
                fieldData.forEach((key, value) -> {
                    try {
                        form.setFieldProperty(key, "textsize", 10f, null);
                        form.setField(key, value.toString());
                    } catch (Exception e) {
                        log.error("字段:{}设置失败:", key, e);
                    }
                });
            } finally {
                if (stamper != null) {
                    stamper.close();
                }
                if (reader != null) {
                    reader.close();
                }
            }

            // PDF转图片
            byte[] byteArray = bos.toByteArray();
            try (PDDocument pdDocument = PDDocument.load(byteArray);
                 FileOutputStream fos = new FileOutputStream(l + "_通知书.jpg")) {
                PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
                int pageCount = pdDocument.getNumberOfPages();
                BufferedImage imageWithDPI = pdfRenderer.renderImageWithDPI(0, 300);
                BufferedImage combinedImage = new BufferedImage(imageWithDPI.getWidth(), imageWithDPI.getHeight() * pageCount, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = combinedImage.createGraphics();
                for (int i = 0; i < pageCount; i++) {
                    BufferedImage image = pdfRenderer.renderImageWithDPI(i, 300);
                    graphics.drawImage(image, 0, i * imageWithDPI.getHeight(), null);
                }
                ImageIOUtil.writeImage(combinedImage, "jpg", imageOutput);

                byte[] imageData = imageOutput.toByteArray();
                fos.write(imageData);
            }
        } finally {
            System.out.println("test22()书生成结束:" + l);
        }
    }

    //优化
    public static void test23() throws Exception {
        byte[] alliyImg = null;
        long millis = System.currentTimeMillis();

        try (InputStream pdfStream = new ClassPathResource("certificateTemplate/yc_lpsqs.pdf").getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            // 1、生成pdf
            PdfReader reader = null;
            PdfStamper stamper = null;
            try {
                reader = new PdfReader(pdfStream);
                stamper = new PdfStamper(reader, bos);
                AcroFields form = stamper.getAcroFields();
                BaseFont baseFont = BaseFont.createFont("STSongStd-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
                form.addSubstitutionFont(baseFont);
                stamper.setFormFlattening(true);
                if (null != alliyImg) {
                    PdfContentByte content = stamper.getOverContent(1);
                    Image img1 = Image.getInstance(alliyImg);
                    img1.scaleToFit(60, 60);
                    Rectangle imageFieldRect1 = form.getFieldPositions("img1").get(0).position;
                    float x1 = imageFieldRect1.getLeft() + (imageFieldRect1.getWidth() - img1.getScaledWidth()) / 2;
                    float y1 = imageFieldRect1.getBottom() + (imageFieldRect1.getHeight() - img1.getScaledHeight()) / 2;
                    img1.setAbsolutePosition(x1, y1);
                    content.addImage(img1); //将图片添加到PDF中
                }

                Map<String, Object> fieldData = new HashMap<>();
                fieldData.put("outInsureName", "张三丰");
                fieldData.put("invoiceBeginDate", "2025-11-17");
                fieldData.forEach((key, value) -> {
                    try {
                        form.setFieldProperty(key, "textsize", 10f, null);
                        form.setField(key, value.toString());
                    } catch (Exception e) {
                        log.error("字段:{}设置失败:", key, e);
                    }
                });
            } finally {
                log.info("test23()_pdf生成结束:" + millis);
                if (stamper != null) {
                    stamper.close();
                }
                if (reader != null) {
                    reader.close();
                }
            }

            // 2、PDF转图片
            String relativePath = System.getProperty("user.dir") + File.separator + "temFile";
            File dir = new File(relativePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String absolutePath = relativePath + File.separator + millis + "_永诚_意健险理赔申请书.jpg";
            byte[] byteArray = bos.toByteArray();
            try (PDDocument pdDocument = PDDocument.load(byteArray);
                 FileOutputStream fos = new FileOutputStream(absolutePath)) {
                PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
                int pageCount = pdDocument.getNumberOfPages();

                // 单页PDF直接处理
                if (pageCount == 1) {
                    BufferedImage image = pdfRenderer.renderImageWithDPI(0, 300);
                    ImageIO.write(image, "JPEG", fos);
                    return;
                }

                // 多页PDF垂直拼接
                BufferedImage firstPage = pdfRenderer.renderImageWithDPI(0, 300);
                int totalHeight = firstPage.getHeight() * pageCount;
                BufferedImage combinedImage = new BufferedImage(firstPage.getWidth(), totalHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = combinedImage.createGraphics();

                // 设置渲染质量
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (int i = 0; i < pageCount; i++) {
                    BufferedImage pageImage = pdfRenderer.renderImageWithDPI(i, 300);
                    graphics.drawImage(pageImage, 0, i * firstPage.getHeight(), null);
                }

                graphics.dispose();
                ImageIO.write(combinedImage, "JPEG", fos);
            } finally {
                log.info("test23()_PDF转图片结束,path:{}", absolutePath);
            }
        }
    }


//    @Test
    public void test11() {
//        createCertificateApplicationService.createCertificate(255000064001L, "0", "2", "3");
//        createCertificateApplicationService.createCertificate(254902628001L, "1", "2", "3");
        createCertificateApplicationService.loadConfigAndCreateCertificate(254902628001L);
    }
}
