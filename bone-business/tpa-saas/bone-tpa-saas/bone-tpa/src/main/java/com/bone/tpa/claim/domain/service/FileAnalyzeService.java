package com.bone.tpa.claim.domain.service;

import com.bone.core.result.QueryParam;
import com.bone.core.util.JsonUtil;
import com.bone.tpa.claim.application.request.*;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.FileUploadRecordRepository;
import com.bone.tpa.sdk.dao.SignRecordRepository;
import com.bone.tpa.claim.util.OssUtil;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.sdk.claim.enums.*;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.sdk.claim.model.FileUploadRecord;
import com.bone.tpa.sdk.claim.model.SignRecord;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 工作表相关服务
 */
@Service
@Slf4j
public class FileAnalyzeService {
    @Autowired
    private SignRecordRepository signRecordRepository;

    @Autowired
    private FileUploadRecordRepository fileUploadRecordRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private OssUtil ossUtil;

    @Autowired
    private ClaimImageService claimImageService;


    private static int MAX_SIZE = 1024 * 1024; // 1MB


    // 常见的候选编码列表（按优先级排序）
    private static final List<Charset> ENCODING_CANDIDATES = Arrays.asList(
            Charset.forName("GBK"),      // 中文 Windows 常用
            Charset.forName("GB18030"),  // 兼容性更广的中文编码
            StandardCharsets.UTF_8,    // 标准 Unicode 编码
            StandardCharsets.ISO_8859_1 // 西欧编码，某些旧系统可能使用
    );


    /**
     * 分析excel并返回其中数据
     * <p>
     * 返回结构为
     * [{
     * "测试列2" : "22.0",
     * "测试列1" : "11.0",
     * "测试列4" : "44.0",
     * "测试列3" : "33.0"
     * }, {
     * "测试列2" : "",
     * "测试列1" : "101.0",
     * "测试列4" : "404.0",
     * "测试列3" : "303.0"
     * }]
     *
     * @param file
     * @return
     */
    protected List<String> analyzeExcel(MultipartFile file, UploadDataVO2 importConfig) throws TpaBizException {
        if (file == null) {
            return null;
        }

        try {
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0); // 获取第一个工作表

            //1. 首先要获取第一列字段，检查和规则设置的是否一致
            Row title = sheet.getRow(0);
            //获取列名称
            List<String> titleList = getRowValue(title, null);
            //此处要检测是否和规则是同样的标题类型
            if (!titleList.equals(importConfig.getFieldNameList())) {
                throw new TpaBizException(BizErrorCode.IMPORT_CONFIG_ERROR, "表头字段错误");
            }
            //将列移除方便之后遍历
            sheet.removeRow(title);
            int length = titleList.size();

            //进行字段规则的检测
            sheet = singleRuleChecker(sheet, titleList, importConfig.getSingleFieldRuleList(), importConfig.getCheckType());
            sheet = groupRuleChecker(sheet, titleList, importConfig.getGroupFieldRuleList(), importConfig.getCheckType());


            //2. 开始读取第二行开始的数据。和输入需要一一对应，字段多则忽略。与此同时需要检测规则设置中的重复情况。
            List<String> sheetValueList = new ArrayList<>();
            for (Row row : sheet) {
                List<String> rowValueList = getRowValue(row, length);

                //遍历并将数据组装成map后转换为json
                Map<String, String> valueMap = new HashMap<>();
                for (int i = 0; i < length; i++) {
                    valueMap.put(titleList.get(i), rowValueList.get(i));
                }
                sheetValueList.add(JsonUtil.toJson(valueMap));
            }

            return sheetValueList;
        } catch (TpaBizException e) {
            log.info(e.getMessage());
            throw e;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * 获取每一行的数据并转化为字符串数组
     *
     * @param row
     * @param length
     * @return
     */
    protected List<String> getRowValue(Row row, Integer length) {
        List<String> valueList = new ArrayList<>();
        //有长度的是数据行
        if (length != null) {
            for (int i = 0; i < length; i++) {
                Cell cell = row.getCell(i);
                if (cell == null) {
                    valueList.add("");
                } else {
                    valueList.add(getCellValue(cell));
                }
            }
            return valueList;
        }

        //没有长度的是标题行
        for (Cell cell : row) {
            valueList.add(getCellValue(cell));
        }

        return valueList;
    }

    /**
     * 获取单个单元格的数据
     * 转换为string，仅限简单类型
     *
     * @param cell
     * @return
     */
    protected String getCellValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }


    /**
     * 单字段规则检测方法
     * 先检测必填，后检测唯一性
     */
    private Sheet singleRuleChecker(Sheet sheet, List<String> titleList, List<SingleFieldRuleVO2> singleFieldRuleList, byte checkType) {
        Map<String, SingleFieldRuleVO2> singleFieldRuleMap = singleFieldRuleList.stream().collect(Collectors.toMap(SingleFieldRuleVO2::getBizName, t -> t));
        int length = titleList.size();

        //先检测必填
        List<Row> removeRow = new ArrayList<>();

        for (Row row : sheet) {
            List<String> rowValueList = getRowValue(row, length);

            for (int i = 0; i < length; i++) {
                SingleFieldRuleVO2 singleFieldRule = singleFieldRuleMap.get(titleList.get(i));

                if (singleFieldRule == null) {
                    continue;
                }
                if (singleFieldRule.getRequired()) {
                    if (rowValueList.get(i).isEmpty()) {
                        //如果是必须一次性检查完，立刻报错
                        if (checkType == 1) {
                            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, titleList.get(i) + " is required!");
                        } else {
                            //否则的话，将该行记录下来，最后删除
                            if (!removeRow.contains(row)) {
                                removeRow.add(row);
                            }
                            break;
                        }
                    }
                }
            }
        }

        //将所有不符合必填的行数删除
        for (Row row : removeRow) {
            sheet.removeRow(row);
        }

        //然后检测唯一性
        for (int i = 0; i < length; i++) {
            SingleFieldRuleVO2 singleFieldRule = singleFieldRuleMap.get(titleList.get(i));

            if (singleFieldRule == null) {
                continue;
            }

            if (singleFieldRule.getUnique()) {
                sheet = deleteDuplicate(sheet, Collections.singletonList(i), checkType);
            }
        }

        return sheet;
    }

    private Sheet groupRuleChecker(Sheet sheet, List<String> titleList, List<GroupFieldRuleVO2> groupFieldRuleList, byte checkType) {
        //先检查联合独特条件
        for (GroupFieldRuleVO2 groupFieldRule : groupFieldRuleList) {
            if (groupFieldRule.getUnique()) {
                List<Integer> colList = new ArrayList<>();

                for (String fieldName : groupFieldRule.getFieldNameList()) {
                    colList.add(titleList.indexOf(fieldName));
                }

                sheet = deleteDuplicate(sheet, colList, checkType);
            }
        }

        //最后检查联合相同条件
        //当第一组字段相同时第二组字段的值也需要相同
        for (GroupFieldRuleVO2 groupFieldRule : groupFieldRuleList) {
            List<Integer> colListA = new ArrayList<>();
            for (String fieldName : groupFieldRule.getFieldNameListA()) {
                colListA.add(titleList.indexOf(fieldName));
            }
            List<Integer> colListB = new ArrayList<>();
            for (String fieldName : groupFieldRule.getFieldNameListA()) {
                colListB.add(titleList.indexOf(fieldName));
            }


        }


        return sheet;
    }


    /**
     * 删除重复元素的行
     *
     * @param sheet
     * @param colList
     * @return
     */
    private Sheet deleteDuplicate(Sheet sheet, List<Integer> colList, byte checkType) {

        Set<String> seenElements = new HashSet<>();
        List<Row> rowsToRemove = new ArrayList<>();

        for (Row row : sheet) {
            StringBuilder combinedKey = new StringBuilder();
            for (Integer i : colList) {
                Cell cell = row.getCell(i);
                combinedKey.append(cell);
            }

            if (!seenElements.add(combinedKey.toString())) {
                if (checkType == 1) {
                    throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, combinedKey + " is not unique!");
                } else {
                    rowsToRemove.add(row);
                }
            }
        }

        for (Row rowToRemove : rowsToRemove) {
            sheet.removeRow(rowToRemove); // 删除重复元素较低的行
        }

        return sheet;
    }

    @Async("asyncTaskExecutor")
    public void processImageZip(MultipartFile file, Long relatedId, UploadImageVO importConfig, Integer importType, Long recordId) {
        try {
            Charset detectedCharset = detectZipEncoding(file);

            if (detectedCharset == null) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "无法检测 ZIP 文件的编码，尝试所有候选编码均失败");
            }
            System.out.println("Charset: " + detectedCharset);

            // 打开输入 Zip 文件流
            ZipInputStream zis = new ZipInputStream(file.getInputStream(), detectedCharset);
            ZipEntry entry;

            // 先获取签收记录检查签收记录，然后获取该批次的所有赔案
            SignRecord signRecord = signRecordRepository.findById(relatedId);
            if (signRecord == null) {
                throw new TpaBizException(BizErrorCode.NO_RECORD, "No sign record: " + relatedId);
            }
            // 如果不是待上传赔案或者待确认，该状态存在问题
            if (!signRecord.getSignStatus().equals(SignStatusEnum.WAIT_FOR_IMAGE.getCode()) &&
                    !signRecord.getSignStatus().equals(SignStatusEnum.WAIT_FOR_CONFIRMATION.getCode())) {
                throw new TpaBizException(BizErrorCode.BIZ_STAGE_ERROR, "Sign record: " + relatedId + " should not be in: " + signRecord.getSignStatus());
            }
            QueryListRequest queryListRequest = new QueryListRequest();
            queryListRequest.setId(String.valueOf(relatedId));
            queryListRequest.setTenantId(String.valueOf(signRecord.getTenantId()));
            queryListRequest.setBizIdentityCode(signRecord.getBizIdentityCode());
            List<Claim> claimList = claimService.getByQueryParam(queryListRequest);
            // 如果没有搜到生成的赔案，不需要继续了
            if (claimList.isEmpty()) {
                throw new TpaBizException(BizErrorCode.NO_RECORD, "No claim for sign record: " + relatedId);
            }

            // 然后将其转化为claimNo: Claim的map用于后续查询
            Map<String, Claim> claimMap = claimList.stream().collect(Collectors.toMap(Claim::getClaimNo, Claim -> Claim));

            List<String> updatedClaimNo = new ArrayList<>();

            //这里要根据上传配置检查文件夹数量是否超过
            Set<String> folders = new HashSet<>();
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');

                int slashIndex = entryName.indexOf('/');
                if (slashIndex != -1) {
                    String folder = entryName.substring(0, slashIndex);
                    if (!folder.isEmpty()) {
                        folders.add(folder);
                    }
                }
            }
            if (folders.size() > importConfig.getFolderMaxSize()) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "Zip文件中文件夹数量超过上限：" + folders.size());
            }

            //正式处理
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');

                // 检测图片文件夹的路径是否是赔案号
                String[] split = entryName.split("/");

                log.info(entry.getName());

                // 必须是 赔案号/文件名 的格式，不接受二层文件夹
                if (split.length > 2) {
                    continue;
                }

                // 若赔案号部分并不正确则无视
                if (!claimMap.containsKey(split[0])) {
                    continue;
                }

                if (!entry.isDirectory() && isImageFile(entryName)) {
                    // 记录这个操作了的赔案号，用于计数
                    updatedClaimNo.add(split[0]);

                    System.out.println("Processing image file: " + entryName);

                    // 创建临时文件（会自动清理）
                    File tempFile = Files.createTempFile("upload-", ".tmp").toFile();
                    file.transferTo(tempFile); // 直接写入磁盘

                    // 后续处理使用文件流操作

                    //处理和上传文件
                    processAndUploadImage(new FileInputStream(tempFile), split[1], claimMap.get(split[0]), SameFileRuleEnum.getByConfigCode(importConfig.getSameFileHandle()), importType, MAX_SIZE);

//                    byteArrayOutputStream.close();
                } else if (entry.isDirectory() && ImportTypeEnum.REPLACE.getConfigCode().equals(importType)) { // 检测是否是替换
                    System.out.println("Replacing image for claim: " + entryName);

                    Claim claim = claimMap.get(split[0]);

                    //查出这个赔案关联的所有影像件
                    QueryListRequest imageQueryListRequest = new QueryListRequest();
                    imageQueryListRequest.setId(String.valueOf(claim.getId()));
                    imageQueryListRequest.setTenantId(String.valueOf(claim.getTenantId()));
                    imageQueryListRequest.getQueryParams().add(new QueryParam("relatedId", claim.getId()));
                    List<ClaimImage> claimImageList = claimImageService.getByQueryParam(imageQueryListRequest);
                    List<Long> deleteIds = claimImageList.stream().map(ClaimImage::getId).toList();

                    //将其删除
                    claimImageService.deleteImage(deleteIds);

                    //更新一下赔案map的数据
                    claimMap.get(split[0]).setImageCount(BigDecimal.valueOf(0));
                    claimMap.get(split[0]).setImageUploadFlag("false");
                    claimMap.get(split[0]).setImageCount(claimMap.get(split[0]).getImageCount().add(BigDecimal.ONE));
                }
            }

            // 更新改动到赔案表和签收记录表
            Map<String, Long> claimNoCountMap = updatedClaimNo.stream()
                    .collect(Collectors.groupingBy(e -> e, Collectors.counting()));

            updateClaimAndSign(signRecord, claimMap, claimNoCountMap, importConfig, importType);

            //更新上传记录
            FileUploadRecord fileUploadRecord = fileUploadRecordRepository.findById(recordId);
            fileUploadRecord.setStatus(FileStatusEnum.IMAGE_COMPLETE.getCode());
            fileUploadRecord.setRemark(claimNoCountMap.keySet().toString());
            fileUploadRecordRepository.update(fileUploadRecord);

            zis.close();
        } catch (Exception e) {
            //更新上传记录
            FileUploadRecord fileUploadRecord = fileUploadRecordRepository.findById(recordId);
            fileUploadRecord.setStatus(FileStatusEnum.IMAGE_ERROR.getCode());
            fileUploadRecord.setRemark(e.getMessage());
            fileUploadRecordRepository.update(fileUploadRecord);

            e.printStackTrace();
        }
    }

    // 检测 ZIP 文件的编码
    private static Charset detectZipEncoding(MultipartFile zipFile) {
        for (Charset charset : ENCODING_CANDIDATES) {
            if (isEncodingValid(zipFile, charset)) {
                return charset;
            }
        }
        return null;
    }

    // 验证编码是否有效
    private static boolean isEncodingValid(MultipartFile zipFile, Charset charset) {
        try{
            ZipInputStream zis = new ZipInputStream(zipFile.getInputStream(), charset);

            // 尝试读取第一个条目
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                return false; // ZIP 文件为空
            }

            // 如果能读取到条目名称且无异常，则认为编码有效
            String entryName = entry.getName();
            return true;

        } catch (IllegalArgumentException | IOException e) {
            // 捕获因编码错误导致的异常
            return false;
        }
    }

    public String processPicture(MultipartFile file, Long relatedId, UploadPictureVO2 importConfig, Integer importType) {
        try {
            // 获取相关的赔案
            Claim claim = claimRepository.findById(relatedId);
            if (claim == null) {
                throw new TpaBizException(BizErrorCode.NO_RECORD, "No claim: " + relatedId);
            }
            // 如果不是待上传赔案或者待确认，该状态存在问题
            if (claim.getStage().equals(ClaimStageEnum.REVIEWING.getCode())) {
                throw new TpaBizException(BizErrorCode.BIZ_STAGE_ERROR, "Claim: " + relatedId + " should not be in: " + claim.getStage());
            }

            System.out.println("Processing image file: " + file.getOriginalFilename());

//            // 读取图片内容
//            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//            byte[] buffer = new byte[1024];
//            int bytesRead;
//            while ((bytesRead = file.getInputStream().read(buffer)) != -1) {
//                byteArrayOutputStream.write(buffer, 0, bytesRead);
//            }

            // 创建临时文件（会自动清理）
            File tempFile = Files.createTempFile("upload-", ".tmp").toFile();
            file.transferTo(tempFile); // 直接写入磁盘

            // 后续处理使用文件流操作

            //处理和上传文件
            processAndUploadImage(new FileInputStream(tempFile), file.getOriginalFilename(), claim, SameFileRuleEnum.KEEP, importType, importConfig.getSingleMaxSize());

//            byteArrayOutputStream.close();

            return claim.getClaimNo();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


//    /**
//     * 单张图片重新上传
//     * 用于处理以下情况:
//     * 1. 图片转向
//     */
//    public String imageReupload(ClaimImageDTO image) {
//        Integer angle = image.getAngle();
//        String oldUrl = image.getImagePath();
//        Claim claim = claimService.getById(image.getRelatedId());
//        try {
//            InputStream imageStream = OssUtil.downloadForIs(image.getImagePath());
//            BufferedImage rotatedImage = rotate(ImageIO.read(imageStream), angle);
//
//            String imageSuffix = oldUrl.substring(oldUrl.lastIndexOf(".") + 1);
//            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//            ImageIO.write(rotatedImage, imageSuffix, outputStream);
//            byte[] byteArray = outputStream.toByteArray();
//            InputStream inputStream = new ByteArrayInputStream(byteArray);
//
//            String path = ossUtil.upload(image.getImageName(), inputStream, claim.getClaimNo());
//
//            return path;
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }


    /**
     * 对图片进行旋转
     *
     * @param src   被旋转图片
     * @param angel 旋转角度
     * @return 旋转后的图片
     */
    public static BufferedImage rotate(Image src, int angel) {
        int src_width = src.getWidth(null);
        int src_height = src.getHeight(null);
        // 计算旋转后图片的尺寸
        Rectangle rect_des = calcRotatedSize(new Rectangle(new Dimension(
                src_width, src_height)), angel);
        BufferedImage res = null;
        res = new BufferedImage(rect_des.width, rect_des.height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = res.createGraphics();
        // 进行转换
        g2.translate((rect_des.width - src_width) / 2,
                (rect_des.height - src_height) / 2);
        g2.rotate(Math.toRadians(angel), src_width / 2.0, src_height / 2.0);

        g2.drawImage(src, null, null);
        return res;
    }

    /**
     * 计算旋转后的图片大小
     *
     * @param src   被旋转的图片
     * @param angel 旋转角度
     * @return 旋转后的图片
     */
    public static Rectangle calcRotatedSize(Rectangle src, int angel) {
        // 如果旋转的角度大于90度做相应的转换
        if (angel >= 90) {
            if (angel / 90 % 2 == 1) {
                int temp = src.height;
                src.height = src.width;
                src.width = temp;
            }
            angel = angel % 90;
        }

        double r = Math.sqrt(src.height * src.height + src.width * src.width) / 2;
        double len = 2 * Math.sin(Math.toRadians(angel) / 2) * r;
        double angel_alpha = (Math.PI - Math.toRadians(angel)) / 2;
        double angel_dalta_width = Math.atan((double) src.height / src.width);
        double angel_dalta_height = Math.atan((double) src.width / src.height);

        int len_dalta_width = (int) (len * Math.cos(Math.PI - angel_alpha
                - angel_dalta_width));
        int len_dalta_height = (int) (len * Math.cos(Math.PI - angel_alpha
                - angel_dalta_height));
        int des_width = src.width + len_dalta_width * 2;
        int des_height = src.height + len_dalta_height * 2;
        return new Rectangle(new Dimension(des_width, des_height));
    }


    /**
     * 处理图片上传
     * <p>
     * 新增且覆盖同名文件：上传之后，删除相应的影像件和影像件记录
     * 新增且保留同名文件：检查是否有同名影像件，如果有，则修改名称
     * 替换：该选项仅适用于压缩包，因此两处皆需判断。
     * 1.检查是否有空文件夹（如果需要的话）
     * 2.针对每个文件夹，删除该赔案全部影像件和记录
     * 3.然后正常上传（该条不在此处，在zip判断）
     */
    private void processAndUploadImage(InputStream inputStream, String imageName, Claim claim, SameFileRuleEnum sameFileRuleEnum, Integer importType, Integer maxSize) {
        try {
//            // 将图片内容读入 BufferedImage
//            ByteArrayInputStream byteArrayInputStream;
//
//            // 如果图片大小超过1MB，进行压缩
//            if (byteArrayOutputStream.size() > maxSize) {
//                byte[] newImage = compressImage(byteArrayOutputStream.toByteArray());
//                byteArrayInputStream = new ByteArrayInputStream(newImage);
//            } else {
//                byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
//            }

            // 如果这里的上传配置要求是新增且覆盖，则要对比影像件名称，删除旧的影像件
            if (ImportTypeEnum.ADD.getConfigCode().equals(importType) && sameFileRuleEnum != null && sameFileRuleEnum.equals(SameFileRuleEnum.REPLACE)) { // 检测是否是新增且覆盖 TODO：数据结构待改动
                QueryListRequest queryListRequest = new QueryListRequest();
                queryListRequest.setId(String.valueOf(claim.getId()));
                queryListRequest.setTenantId(String.valueOf(claim.getTenantId()));
                queryListRequest.getQueryParams().add(new QueryParam("imageName", imageName));
                List<ClaimImage> claimImageList = claimImageService.getByQueryParam(queryListRequest);
                if (claimImageList.size() > 1) {
                    throw new TpaBizException(BizErrorCode.INNER_PARAMETER_ERROR, "have multiple image called: " + imageName);
                }
                if (claimImageList.size() == 1) {
                    claimImageService.deleteImage(Collections.singletonList(claimImageList.get(0).getId()));
                }
            } else if (ImportTypeEnum.ADD.getConfigCode().equals(importType) && sameFileRuleEnum != null && sameFileRuleEnum.equals(SameFileRuleEnum.KEEP)) { // 检测是否是新增且保留两者
                // 如果这里的上传配置要求是新增且保留两者，则获取全部的影像件，然后依次比较名称
                QueryListRequest queryListRequest = new QueryListRequest();
                queryListRequest.setId(String.valueOf(claim.getId()));
                queryListRequest.setTenantId(String.valueOf(claim.getTenantId()));
                List<ClaimImage> claimImageList = claimImageService.getByQueryParam(queryListRequest);

                List<String> imageNameList = claimImageList.stream().map(ClaimImage::getImageName).toList();

                if (imageNameList.contains(imageName)) {
                    int i = 1;
                    while (imageNameList.contains(imageName + "(" + i + ")")) {
                        i++;
                    }
                    imageName = imageName + "(" + i + ")";
                }
            }

            // 上传图片
            String path = ossUtil.upload(imageName, inputStream, claim.getClaimNo());

            // 记录至赔案图片表
            ClaimImage claimImage = new ClaimImage();
            claimImage.setImageDetailId(String.valueOf(UUID.randomUUID()));
            claimImage.setImageName(imageName);
            claimImage.setImagePath(path);
            claimImage.setRelatedId(claim.getId());
            claimImage.setTenantId(claim.getTenantId());
            claimImage.setOcrFlag(0);
            claimImage.setSourceSystem(1);
            claimImage.setPushFlag(1);
            claimImageService.createNewClaimImage(claimImage);

            inputStream.close();
        } catch (IOException e) {
            // 处理异常
        }
    }

    /**
     * 到这一步已经将影像件表全部更新完成，替换和覆盖的情况也已经清除过，此时
     * 1. 赔案只需根据赔案号出现次数就可以知道上传了多少赔案
     */
    private void updateClaimAndSign(SignRecord signRecord, Map<String, Claim> claimMap, Map<String, Long> claimNoCountMap, UploadImageVO importConfig, Integer importType) {
        //遍历全赔案并且处理
        for (String claimNo : claimMap.keySet()) {
            if (claimNoCountMap.containsKey(claimNo)) {
                //如果该赔案本次有更新影像件，便更新各个字段
                claimMap.get(claimNo).setImageUploadFlag("true");
                claimMap.get(claimNo).setImageCount(claimMap.get(claimNo).getImageCount().add(BigDecimal.valueOf(claimNoCountMap.get(claimNo))));
                if (ImportTypeEnum.ADD.getConfigCode().equals(importType)) { // 检测是否不是替换
                    claimMap.get(claimNo).setImageCount(claimMap.get(claimNo).getImageCount().add(BigDecimal.valueOf(claimNoCountMap.get(claimNo))));
                }
                claimMap.get(claimNo).setImageUploadCount(claimMap.get(claimNo).getImageUploadCount().add(BigDecimal.ONE));
                claimMap.get(claimNo).setImageUploader(BizContextUtils.getUser());
                claimMap.get(claimNo).setImageUploadTime(new Date());
                claimService.updateClaim(claimMap.get(claimNo), false, "更新影像件");
            }
        }

        //此时要去取出所有的赔案列表来，然后检查是不是全部都提交了影像件
        Boolean allImage = true;
        for (Claim claim : claimMap.values()) {
            if (!"true".equals(claim.getImageUploadFlag())) {
                allImage = false;
                break;
            }
        }

        //如果有改动就上传签收记录
        if (signRecord.getSignStatus().equals(SignStatusEnum.WAIT_FOR_IMAGE.getCode()) || signRecord.getImageUploadFlag().equals("false")) {
            signRecord.setSignStatus(SignStatusEnum.WAIT_FOR_IMAGE.getCode());
            signRecord.setImageUploadFlag("true");
        }

        if (allImage) {
            signRecord.setSignStatus(SignStatusEnum.WAIT_FOR_CONFIRMATION.getCode());
        }

        signRecordRepository.update(signRecord);
    }

    private byte[] compressImage(byte[] imageBytes) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ByteArrayInputStream is = new ByteArrayInputStream(imageBytes);
        float quality = 1.0f; // 初始质量设为1

        // 逐步降低质量直到文件大小小于1MB
        while (os.size() > MAX_SIZE) { // 1MB = 1000000 bytes
            os.reset(); // 重置输出流
            quality -= 0.1f; // 逐步降低质量
            Thumbnails.of(is)
                    .scale(1.0)
                    .outputQuality(quality)
                    .toOutputStream(os);
        }

        // 将压缩后的图像数据转换为byte数组
        return os.toByteArray();
    }


    private static boolean isImageFile(String fileName) {
        return fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".jpeg")
                || fileName.toLowerCase().endsWith(".png") || fileName.toLowerCase().endsWith(".bmp");
    }
}
