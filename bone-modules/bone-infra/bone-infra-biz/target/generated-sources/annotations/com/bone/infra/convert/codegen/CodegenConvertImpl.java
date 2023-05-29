package com.bone.infra.convert.codegen;

import com.baomidou.mybatisplus.generator.config.po.TableField;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.config.rules.IColumnType;
import com.bone.base.core.pojo.PageResult;
import com.bone.infra.controller.admin.codegen.vo.CodegenUpdateReqVO;
import com.bone.infra.controller.admin.codegen.vo.column.CodegenColumnRespVO;
import com.bone.infra.controller.admin.codegen.vo.table.CodegenTableRespVO;
import com.bone.infra.controller.admin.codegen.vo.table.DatabaseTableRespVO;
import com.bone.infra.dal.dataobject.codegen.CodegenColumnDO;
import com.bone.infra.dal.dataobject.codegen.CodegenTableDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.apache.ibatis.type.JdbcType;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:27:59+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class CodegenConvertImpl implements CodegenConvert {

    @Override
    public CodegenTableDO convert(TableInfo bean) {
        if ( bean == null ) {
            return null;
        }

        CodegenTableDO codegenTableDO = new CodegenTableDO();

        codegenTableDO.setTableName( bean.getName() );
        codegenTableDO.setTableComment( bean.getComment() );

        return codegenTableDO;
    }

    @Override
    public List<CodegenColumnDO> convertList(List<TableField> list) {
        if ( list == null ) {
            return null;
        }

        List<CodegenColumnDO> list1 = new ArrayList<CodegenColumnDO>( list.size() );
        for ( TableField tableField : list ) {
            list1.add( convert( tableField ) );
        }

        return list1;
    }

    @Override
    public CodegenColumnDO convert(TableField bean) {
        if ( bean == null ) {
            return null;
        }

        CodegenColumnDO codegenColumnDO = new CodegenColumnDO();

        codegenColumnDO.setColumnName( bean.getName() );
        codegenColumnDO.setDataType( getDataType( beanMetaInfoJdbcType( bean ) ) );
        codegenColumnDO.setColumnComment( bean.getComment() );
        codegenColumnDO.setNullable( beanMetaInfoNullable( bean ) );
        codegenColumnDO.setPrimaryKey( bean.isKeyFlag() );
        codegenColumnDO.setAutoIncrement( bean.isKeyIdentityFlag() );
        codegenColumnDO.setJavaType( beanColumnTypeType( bean ) );
        codegenColumnDO.setJavaField( bean.getPropertyName() );

        return codegenColumnDO;
    }

    @Override
    public CodegenTableRespVO convert(CodegenTableDO bean) {
        if ( bean == null ) {
            return null;
        }

        CodegenTableRespVO codegenTableRespVO = new CodegenTableRespVO();

        codegenTableRespVO.setScene( bean.getScene() );
        codegenTableRespVO.setTableName( bean.getTableName() );
        codegenTableRespVO.setTableComment( bean.getTableComment() );
        codegenTableRespVO.setRemark( bean.getRemark() );
        codegenTableRespVO.setModuleName( bean.getModuleName() );
        codegenTableRespVO.setBusinessName( bean.getBusinessName() );
        codegenTableRespVO.setClassName( bean.getClassName() );
        codegenTableRespVO.setClassComment( bean.getClassComment() );
        codegenTableRespVO.setAuthor( bean.getAuthor() );
        codegenTableRespVO.setTemplateType( bean.getTemplateType() );
        codegenTableRespVO.setParentMenuId( bean.getParentMenuId() );
        codegenTableRespVO.setId( bean.getId() );
        if ( bean.getDataSourceConfigId() != null ) {
            codegenTableRespVO.setDataSourceConfigId( bean.getDataSourceConfigId().intValue() );
        }
        codegenTableRespVO.setCreateTime( bean.getCreateTime() );
        codegenTableRespVO.setUpdateTime( bean.getUpdateTime() );

        return codegenTableRespVO;
    }

    @Override
    public PageResult<CodegenTableRespVO> convertPage(PageResult<CodegenTableDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<CodegenTableRespVO> pageResult = new PageResult<CodegenTableRespVO>();

        pageResult.setList( codegenTableDOListToCodegenTableRespVOList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public List<CodegenColumnRespVO> convertList02(List<CodegenColumnDO> list) {
        if ( list == null ) {
            return null;
        }

        List<CodegenColumnRespVO> list1 = new ArrayList<CodegenColumnRespVO>( list.size() );
        for ( CodegenColumnDO codegenColumnDO : list ) {
            list1.add( codegenColumnDOToCodegenColumnRespVO( codegenColumnDO ) );
        }

        return list1;
    }

    @Override
    public CodegenTableDO convert(CodegenUpdateReqVO.Table bean) {
        if ( bean == null ) {
            return null;
        }

        CodegenTableDO codegenTableDO = new CodegenTableDO();

        codegenTableDO.setId( bean.getId() );
        codegenTableDO.setScene( bean.getScene() );
        codegenTableDO.setTableName( bean.getTableName() );
        codegenTableDO.setTableComment( bean.getTableComment() );
        codegenTableDO.setRemark( bean.getRemark() );
        codegenTableDO.setModuleName( bean.getModuleName() );
        codegenTableDO.setBusinessName( bean.getBusinessName() );
        codegenTableDO.setClassName( bean.getClassName() );
        codegenTableDO.setClassComment( bean.getClassComment() );
        codegenTableDO.setAuthor( bean.getAuthor() );
        codegenTableDO.setTemplateType( bean.getTemplateType() );
        codegenTableDO.setParentMenuId( bean.getParentMenuId() );

        return codegenTableDO;
    }

    @Override
    public List<CodegenColumnDO> convertList03(List<CodegenUpdateReqVO.Column> columns) {
        if ( columns == null ) {
            return null;
        }

        List<CodegenColumnDO> list = new ArrayList<CodegenColumnDO>( columns.size() );
        for ( CodegenUpdateReqVO.Column column : columns ) {
            list.add( columnToCodegenColumnDO( column ) );
        }

        return list;
    }

    @Override
    public List<DatabaseTableRespVO> convertList04(List<TableInfo> list) {
        if ( list == null ) {
            return null;
        }

        List<DatabaseTableRespVO> list1 = new ArrayList<DatabaseTableRespVO>( list.size() );
        for ( TableInfo tableInfo : list ) {
            list1.add( tableInfoToDatabaseTableRespVO( tableInfo ) );
        }

        return list1;
    }

    private JdbcType beanMetaInfoJdbcType(TableField tableField) {
        if ( tableField == null ) {
            return null;
        }
        TableField.MetaInfo metaInfo = tableField.getMetaInfo();
        if ( metaInfo == null ) {
            return null;
        }
        JdbcType jdbcType = metaInfo.getJdbcType();
        if ( jdbcType == null ) {
            return null;
        }
        return jdbcType;
    }

    private Boolean beanMetaInfoNullable(TableField tableField) {
        if ( tableField == null ) {
            return null;
        }
        TableField.MetaInfo metaInfo = tableField.getMetaInfo();
        if ( metaInfo == null ) {
            return null;
        }
        boolean nullable = metaInfo.isNullable();
        return nullable;
    }

    private String beanColumnTypeType(TableField tableField) {
        if ( tableField == null ) {
            return null;
        }
        IColumnType columnType = tableField.getColumnType();
        if ( columnType == null ) {
            return null;
        }
        String type = columnType.getType();
        if ( type == null ) {
            return null;
        }
        return type;
    }

    protected List<CodegenTableRespVO> codegenTableDOListToCodegenTableRespVOList(List<CodegenTableDO> list) {
        if ( list == null ) {
            return null;
        }

        List<CodegenTableRespVO> list1 = new ArrayList<CodegenTableRespVO>( list.size() );
        for ( CodegenTableDO codegenTableDO : list ) {
            list1.add( convert( codegenTableDO ) );
        }

        return list1;
    }

    protected CodegenColumnRespVO codegenColumnDOToCodegenColumnRespVO(CodegenColumnDO codegenColumnDO) {
        if ( codegenColumnDO == null ) {
            return null;
        }

        CodegenColumnRespVO codegenColumnRespVO = new CodegenColumnRespVO();

        codegenColumnRespVO.setTableId( codegenColumnDO.getTableId() );
        codegenColumnRespVO.setColumnName( codegenColumnDO.getColumnName() );
        codegenColumnRespVO.setDataType( codegenColumnDO.getDataType() );
        codegenColumnRespVO.setColumnComment( codegenColumnDO.getColumnComment() );
        codegenColumnRespVO.setNullable( codegenColumnDO.getNullable() );
        codegenColumnRespVO.setPrimaryKey( codegenColumnDO.getPrimaryKey() );
        if ( codegenColumnDO.getAutoIncrement() != null ) {
            codegenColumnRespVO.setAutoIncrement( String.valueOf( codegenColumnDO.getAutoIncrement() ) );
        }
        codegenColumnRespVO.setOrdinalPosition( codegenColumnDO.getOrdinalPosition() );
        codegenColumnRespVO.setJavaType( codegenColumnDO.getJavaType() );
        codegenColumnRespVO.setJavaField( codegenColumnDO.getJavaField() );
        codegenColumnRespVO.setDictType( codegenColumnDO.getDictType() );
        codegenColumnRespVO.setExample( codegenColumnDO.getExample() );
        codegenColumnRespVO.setCreateOperation( codegenColumnDO.getCreateOperation() );
        codegenColumnRespVO.setUpdateOperation( codegenColumnDO.getUpdateOperation() );
        codegenColumnRespVO.setListOperation( codegenColumnDO.getListOperation() );
        codegenColumnRespVO.setListOperationCondition( codegenColumnDO.getListOperationCondition() );
        codegenColumnRespVO.setListOperationResult( codegenColumnDO.getListOperationResult() );
        codegenColumnRespVO.setHtmlType( codegenColumnDO.getHtmlType() );
        codegenColumnRespVO.setId( codegenColumnDO.getId() );
        codegenColumnRespVO.setCreateTime( codegenColumnDO.getCreateTime() );

        return codegenColumnRespVO;
    }

    protected CodegenColumnDO columnToCodegenColumnDO(CodegenUpdateReqVO.Column column) {
        if ( column == null ) {
            return null;
        }

        CodegenColumnDO codegenColumnDO = new CodegenColumnDO();

        codegenColumnDO.setId( column.getId() );
        codegenColumnDO.setTableId( column.getTableId() );
        codegenColumnDO.setColumnName( column.getColumnName() );
        codegenColumnDO.setDataType( column.getDataType() );
        codegenColumnDO.setColumnComment( column.getColumnComment() );
        codegenColumnDO.setNullable( column.getNullable() );
        codegenColumnDO.setPrimaryKey( column.getPrimaryKey() );
        if ( column.getAutoIncrement() != null ) {
            codegenColumnDO.setAutoIncrement( Boolean.parseBoolean( column.getAutoIncrement() ) );
        }
        codegenColumnDO.setOrdinalPosition( column.getOrdinalPosition() );
        codegenColumnDO.setJavaType( column.getJavaType() );
        codegenColumnDO.setJavaField( column.getJavaField() );
        codegenColumnDO.setDictType( column.getDictType() );
        codegenColumnDO.setExample( column.getExample() );
        codegenColumnDO.setCreateOperation( column.getCreateOperation() );
        codegenColumnDO.setUpdateOperation( column.getUpdateOperation() );
        codegenColumnDO.setListOperation( column.getListOperation() );
        codegenColumnDO.setListOperationCondition( column.getListOperationCondition() );
        codegenColumnDO.setListOperationResult( column.getListOperationResult() );
        codegenColumnDO.setHtmlType( column.getHtmlType() );

        return codegenColumnDO;
    }

    protected DatabaseTableRespVO tableInfoToDatabaseTableRespVO(TableInfo tableInfo) {
        if ( tableInfo == null ) {
            return null;
        }

        DatabaseTableRespVO databaseTableRespVO = new DatabaseTableRespVO();

        databaseTableRespVO.setName( tableInfo.getName() );
        databaseTableRespVO.setComment( tableInfo.getComment() );

        return databaseTableRespVO;
    }
}
