package com.bone.tpa.test.claim;

import cn.hutool.core.lang.Assert;
import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.Result;
import com.bone.tpa.claim.adapter.ClaimImageController;
import com.bone.tpa.claim.adapter.FileUploadController;
import com.bone.tpa.claim.application.dto.ClaimImageDTO;
import com.bone.tpa.claim.application.dto.FileUploadRecordDTO;
import com.bone.tpa.claim.application.request.FileUploadRequest;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.test.BaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ExcelTest extends BaseTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private String appCode = "tpa";

    @Autowired
    private FileUploadController fileUploadController;

    @Autowired
    private ClaimImageController claimImageController;

    String filePath = "src/test/java/com/bone/tpa/test/claim/file/";

    @Test
    public void test() {
        Assert.notNull(jdbcTemplate, "metaTableRespository null");
    }

    @Test
    public void testAnalyzeExcel() {
        try {
            File file = new File(filePath + "test.xls");

            // 读取文件内容
            byte[] fileContent = new byte[(int) file.length()];

            InputStream inputStream = new FileInputStream(file);
            inputStream.read(fileContent);

            MockHttpServletRequest request = new MockHttpServletRequest();
            RequestAttributes requestAttributes = new ServletRequestAttributes(request);
            RequestContextHolder.setRequestAttributes(requestAttributes);

            // 创建 MockMultipartFile 对象
            MockMultipartFile mockMultipartFile = new MockMultipartFile("file", file.getName(), null, fileContent);

            FileUploadRequest fileUploadRequest = new FileUploadRequest();
            fileUploadRequest.setRelatedId(1111111222222222L);
            fileUploadRequest.setTenantId(null);
            fileUploadRequest.setConfigId(1L);
            fileUploadRequest.setImportType(1);
            fileUploadRequest.setType((byte) 1);

            Result<Boolean> result = fileUploadController.uploadFile(fileUploadRequest, mockMultipartFile);

            System.out.println(result.getData());
        } catch (FileNotFoundException ex) {
            System.out.println("File not found.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGetImage() {
        try {

            MockHttpServletRequest request = new MockHttpServletRequest();
            RequestAttributes requestAttributes = new ServletRequestAttributes(request);
            RequestContextHolder.setRequestAttributes(requestAttributes);

            QueryListRequest queryListRequest = new QueryListRequest();
            queryListRequest.setTenantId("1");
            queryListRequest.setId("245200398003");

            queryListRequest.setPageNo(0);
            queryListRequest.setPageSize(0);

            Result<List<ClaimImageDTO>> result = claimImageController.getClaimImage(queryListRequest);

            System.out.println(result.getData());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }


    @Test
    public void testTemplate() {
        try {

            MockHttpServletRequest request = new MockHttpServletRequest();
            RequestAttributes requestAttributes = new ServletRequestAttributes(request);
            RequestContextHolder.setRequestAttributes(requestAttributes);

//            Result<byte[]> result = fileUploadController.downloadTemplate(1L);

            QueryListRequest queryListRequest = new QueryListRequest();
            queryListRequest.setTenantId("123456");

            QueryParam queryParam = new QueryParam();
            queryParam.setField("fileType");
            queryParam.setValue("IMAGE");

            List<QueryParam> queryParamList = new ArrayList<>();
            queryParamList.add(queryParam);
            queryListRequest.setQueryParams(queryParamList);

            queryListRequest.setPageNo(1);
            queryListRequest.setPageSize(10);

            Result<PageResult<FileUploadRecordDTO>> result = fileUploadController.getFileUploadRecord(queryListRequest);

            //System.out.println(result.getData());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }
}
