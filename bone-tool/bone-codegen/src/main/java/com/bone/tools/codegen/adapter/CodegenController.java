package com.bone.tools.codegen.adapter;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ZipUtil;
import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.core.util.BeanUtils;
import com.bone.tools.codegen.application.CodegenConvert;
import com.bone.tools.codegen.domain.entity.CodegenColumnDO;
import com.bone.tools.codegen.domain.entity.CodegenTableDO;
import com.bone.tools.codegen.domain.entity.DataSourceConfigDO;
import com.bone.tools.codegen.domain.service.CodegenService;
import com.bone.tools.codegen.domain.service.DataSourceConfigService;
import com.bone.tools.codegen.infrastructure.util.ContextUtil;
import com.bone.tools.codegen.application.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bone.core.result.Result.success;


@Tag(name = "管理后台 - 代码生成器")
@RestController
@RequestMapping("/codegen")
@Validated
@Slf4j
public class CodegenController {

    @Resource
    private CodegenService codegenService;
    @Resource
    private DataSourceConfigService dataSourceConfigService;

    private static final DateTimeFormatter dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/db/table/list")
    @Operation(summary = "获得数据库自带的表定义列表", description = "会过滤掉已经导入 Codegen 的表")
    @Parameters({
            @Parameter(name = "dataSourceConfigId", description = "数据源配置的编号", required = true, example = "1"),
            @Parameter(name = "name", description = "表名，模糊匹配", example = "yudao"),
            @Parameter(name = "comment", description = "描述，模糊匹配", example = "芋道")
    })
    public Result<List<DatabaseTableResponse>> getDatabaseTableList(
            @RequestParam(value = "dataSourceConfigId") Long dataSourceConfigId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "comment", required = false) String comment) {
        return success(codegenService.getDatabaseTableList(dataSourceConfigId, name, comment));
    }

    @GetMapping("/table/list")
    @Operation(summary = "获得表定义列表")
    @Parameter(name = "dataSourceConfigId", description = "数据源配置的编号", required = true, example = "1")
    public Result<List<CodegenTableResponse>> getCodegenTableList(@RequestParam(value = "dataSourceConfigId") Long dataSourceConfigId) {
        List<CodegenTableResponse> result = BeanUtils.toBean(codegenService.getCodegenTableList(dataSourceConfigId), CodegenTableResponse.class);
        result.forEach(x -> x.setCreateTimeStr(x.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        result.forEach(x -> x.setUpdateTimeStr(x.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        return success(result);
    }

    @GetMapping("/table/page")
    @Operation(summary = "获得表定义分页")
    public Result<PageResult<CodegenTableResponse>> getCodegenTablePage(CodegenTablePageRequest pageReqVO) {
        PageResult<CodegenTableResponse> result = BeanUtils.toBean(codegenService.getCodegenTablePage(pageReqVO), CodegenTableResponse.class);
        if (CollectionUtil.isNotEmpty(result.getData())) {
            DataSourceConfigQueryRequest request = new DataSourceConfigQueryRequest();
            request.setIdList(result.getData().stream().map(CodegenTableResponse::getDataSourceConfigId).distinct().collect(Collectors.toList()));
            Map<Long, String> dataSourceConfigMap = dataSourceConfigService.getDataSourceConfigList(request).stream().collect(Collectors.toMap(DataSourceConfigDO::getId, DataSourceConfigDO::getName));
            result.getData().forEach(x -> x.setDataSourceConfigName(dataSourceConfigMap.get((long) x.getDataSourceConfigId())));
        }
        result.getData().forEach(x -> x.setCreateTimeStr(x.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        result.getData().forEach(x -> x.setUpdateTimeStr(x.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        return success(result);
    }

    @GetMapping("/detail")
    @Operation(summary = "获得表和字段的明细")
    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
    public Result<CodegenDetailResponse> getCodegenDetail(@RequestParam("tableId") Long tableId) {
        CodegenTableDO table = codegenService.getCodegenTable(tableId);
        List<CodegenColumnDO> columns = codegenService.getCodegenColumnListByTableId(tableId);
        // 拼装返回
        return success(CodegenConvert.INSTANCE.convert(table, columns));
    }

    @Operation(summary = "基于数据库的表结构，创建代码生成器的表和字段定义")
    @PostMapping("/create-list")
    public Result<List<Long>> createCodegenList(@Valid @RequestBody CodegenCreateListRequest reqVO) {
        return success(codegenService.createCodegenList(0L, reqVO));
    }

    @Operation(summary = "更新数据库的表和字段定义")
    @PutMapping("/update")
    public Result<Boolean> updateCodegen(@Valid @RequestBody CodegenUpdateRequest updateReqVO) {
        codegenService.updateCodegen(updateReqVO);
        return success(true);
    }

    @Operation(summary = "基于数据库的表结构，同步数据库的表和字段定义")
    @PutMapping("/sync-from-db")
    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
    public Result<Boolean> syncCodegenFromDB(@RequestParam("tableId") Long tableId) {
        codegenService.syncCodegenFromDB(tableId);
        return success(true);
    }

    @Operation(summary = "删除数据库的表和字段定义")
    @DeleteMapping("/delete")
    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
    public Result<Boolean> deleteCodegen(@RequestParam("tableId") Long tableId) {
        codegenService.deleteCodegen(tableId);
        return success(true);
    }

//    @Operation(summary = "预览生成代码")
//    @GetMapping("/preview")
//    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
//    public CommonResult<List<CodegenPreviewRespVO>> previewCodegen(@RequestParam("tableId") Long tableId) {
//        Map<String, String> codes = codegenService.generationCodes(tableId);
//        return success(CodegenConvert.INSTANCE.convert(codes));
//    }

    @Operation(summary = "下载生成代码")
    @GetMapping("/download")
    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
    public void downloadCodegen(@RequestParam("tableId") Long tableId,
                                @RequestParam(value = "modelType", defaultValue = "1", required = false) Integer modelType,
                                HttpServletResponse response) throws IOException {
        // 生成代码
        Map<String, String> codes = codegenService.generationCodes(tableId, modelType);
        // 构建 zip 包
        String[] paths = codes.keySet().toArray(new String[0]);
        ByteArrayInputStream[] ins = codes.values().stream().map(IoUtil::toUtf8Stream).toArray(ByteArrayInputStream[]::new);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipUtil.zip(outputStream, paths, ins);
        // 输出
        writeAttachment(response, "codegen.zip", outputStream.toByteArray());
    }

    @Operation(summary = "下载生成代码（多张表）")
    @GetMapping("/download2")
    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
    public void downloadCodegen(@RequestParam("tableId") List<Long> tableIdList,
                                @RequestParam("model") String model,
                                @RequestParam("basePackage") String basePackage,
                                @RequestParam(value = "groupId", required = false) String groupId,
                                @RequestParam(value = "modelType", defaultValue = "1", required = false) Integer modelType,
                                HttpServletResponse response) throws IOException {

        // 添加日志确认参数值
        log.info("下载参数 - model: {}, tableIds: {}", model, tableIdList);

        try {
            ContextUtil.setPackagePath(basePackage); // e.g. "com.bone.tpa.policy"

            // 生成代码
            Map<String, String> codes = codegenService.generationCodes(tableIdList, basePackage, model, groupId, modelType);

            // 构建 zip 包
            String[] paths = codes.keySet().toArray(new String[0]);
            ByteArrayInputStream[] ins = codes.values().stream()
                    .map(IoUtil::toUtf8Stream)
                    .toArray(ByteArrayInputStream[]::new);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ZipUtil.zip(outputStream, paths, ins);

            // 设置文件名并记录
            String fileName = model + "-boot.zip";
            log.info("生成文件名: {}", fileName);

            // 使用增强的附件写入方法
            writeAttachment(response, fileName, outputStream.toByteArray());
        } finally {
            // 4) 清理 ThreadLocal，避免线程复用导致串数据
            ContextUtil.clearPackagePath();
        }
    }

    // 修复的附件写入方法
    private void writeAttachment(HttpServletResponse response, String fileName, byte[] data) throws IOException {
        // 重置响应
        response.reset();

        // 设置正确的Content-Type
        response.setContentType("application/zip");

        // 处理文件名编码
        String sanitized = fileName.replace("\"", "");
        String encoded = URLEncoder.encode(sanitized, StandardCharsets.UTF_8)
                .replace("+", "%20");

        // 设置响应头 - 使用RFC 5987标准
        String headerValue = "attachment; filename=\"" + encoded + "\"";
        headerValue += "; filename*=utf-8''" + encoded;
        response.setHeader("Content-Disposition", headerValue);

        // === 关键修复：禁用缓存 ===
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        // 设置内容长度
        response.setContentLength(data.length);

        // 写入响应
        try (ServletOutputStream out = response.getOutputStream()) {
            out.write(data);
        }

        // 调试日志
        log.debug("响应头设置 - Content-Disposition: {}", headerValue);
    }
}
