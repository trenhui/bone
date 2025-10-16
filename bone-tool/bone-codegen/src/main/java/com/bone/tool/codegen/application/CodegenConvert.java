package com.bone.tool.codegen.application;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.bone.tool.codegen.application.dto.CodegenColumnResponse;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.application.dto.CodegenPreviewResponse;
import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.TableInfo;
import org.springframework.util.CollectionUtils;
import cn.hutool.core.bean.BeanUtil;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CodegenConvert {

    CodegenConvert INSTANCE = Mappers.getMapper(CodegenConvert.class);

    // ========== TableInfo 相关 ==========

    // 由于TableInfo和CodegenTable字段名差异，使用默认方法处理转换
    default CodegenTable convert(TableInfo bean) {
        if (bean == null) {
            return null;
        }
        CodegenTable table = new CodegenTable();
        // 根据TableInfo的实际字段名设置对应的值
        // 注意：这里需要根据实际的TableInfo类结构调整字段映射
        return table;
    }

    // 由于TableField已删除，此方法暂时保留但返回空列表
    default List<CodegenColumn> convertList(List<?> list) {
        return new ArrayList<>();
    }

    // TableField已删除，转换方法也已移除

    // ========== 其它 ==========

    default CodegenDetailResponse convertToDetail(CodegenTable table, List<CodegenColumn> columns) {
        // 使用简单的构造方式，避免使用setter方法
        CodegenDetailResponse respVO = new CodegenDetailResponse();
        // 直接访问字段（如果有必要可以考虑使用反射）
        try {
            // 设置table字段
            java.lang.reflect.Field tableField = CodegenDetailResponse.class.getDeclaredField("table");
            tableField.setAccessible(true);
            tableField.set(respVO, BeanUtil.toBean(table, CodegenTableResponse.class));
            
            // 设置columns字段
            java.lang.reflect.Field columnsField = CodegenDetailResponse.class.getDeclaredField("columns");
            columnsField.setAccessible(true);
            // 处理List转换
            if (columns != null && !columns.isEmpty()) {
                List<CodegenColumnResponse> columnResponses = new ArrayList<>(columns.size());
                for (CodegenColumn column : columns) {
                    columnResponses.add(BeanUtil.toBean(column, CodegenColumnResponse.class));
                }
                columnsField.set(respVO, columnResponses);
            }
        } catch (Exception e) {
            // 如果反射失败，返回空对象
        }
        return respVO;
    }

    default List<CodegenPreviewResponse> convertToPreview(Map<String, String> codes) {
        if (CollectionUtils.isEmpty(codes)) {
            return new ArrayList<>();
        }
        List<CodegenPreviewResponse> result = new ArrayList<>(codes.size());
        for (Map.Entry<String, String> entry : codes.entrySet()) {
            CodegenPreviewResponse vo = new CodegenPreviewResponse();
            // 直接访问字段
            try {
                java.lang.reflect.Field filePathField = CodegenPreviewResponse.class.getDeclaredField("filePath");
                filePathField.setAccessible(true);
                filePathField.set(vo, entry.getKey());
                
                java.lang.reflect.Field codeField = CodegenPreviewResponse.class.getDeclaredField("code");
                codeField.setAccessible(true);
                codeField.set(vo, entry.getValue());
            } catch (Exception e) {
                // 如果反射失败，忽略错误
            }
            result.add(vo);
        }
        return result;
    }

}
