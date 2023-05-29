package com.bone.infra.controller.admin.logger;

import com.bone.base.core.pojo.CommonResult;
import com.bone.base.core.pojo.PageResult;
import com.bone.base.excel.core.util.ExcelUtils;
import com.bone.base.operatelog.core.annotations.OperateLog;
import com.bone.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogExcelVO;
import com.bone.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogExportReqVO;
import com.bone.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogPageReqVO;
import com.bone.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogRespVO;
import com.bone.infra.convert.logger.ApiAccessLogConvert;
import com.bone.infra.dal.dataobject.logger.ApiAccessLogDO;
import com.bone.infra.service.logger.ApiAccessLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;

import static com.bone.base.core.pojo.CommonResult.success;
import static com.bone.base.operatelog.core.enums.OperateTypeEnum.EXPORT;

@Api(tags = "管理后台 - API 访问日志")
@RestController
@RequestMapping("/infra/api-access-log")
@Validated
public class ApiAccessLogController {

    @Resource
    private ApiAccessLogService apiAccessLogService;

    @GetMapping("/page")
    @ApiOperation("获得API 访问日志分页")
    @PreAuthorize("@ss.hasPermission('infra:api-access-log:query')")
    public CommonResult<PageResult<ApiAccessLogRespVO>> getApiAccessLogPage(@Valid ApiAccessLogPageReqVO pageVO) {
        PageResult<ApiAccessLogDO> pageResult = apiAccessLogService.getApiAccessLogPage(pageVO);
        return success(ApiAccessLogConvert.INSTANCE.convertPage(pageResult));
    }

    @GetMapping("/export-excel")
    @ApiOperation("导出API 访问日志 Excel")
    @PreAuthorize("@ss.hasPermission('infra:api-access-log:export')")
    @OperateLog(type = EXPORT)
    public void exportApiAccessLogExcel(@Valid ApiAccessLogExportReqVO exportReqVO,
                                        HttpServletResponse response) throws IOException {
        List<ApiAccessLogDO> list = apiAccessLogService.getApiAccessLogList(exportReqVO);
        // 导出 Excel
        List<ApiAccessLogExcelVO> datas = ApiAccessLogConvert.INSTANCE.convertList02(list);
        ExcelUtils.write(response, "API 访问日志.xls", "数据", ApiAccessLogExcelVO.class, datas);
    }

}
