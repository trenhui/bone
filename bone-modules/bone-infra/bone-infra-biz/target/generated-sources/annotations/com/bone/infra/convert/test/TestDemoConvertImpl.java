package com.bone.infra.convert.test;

import com.bone.base.core.pojo.PageResult;
import com.bone.infra.controller.admin.test.vo.TestDemoCreateReqVO;
import com.bone.infra.controller.admin.test.vo.TestDemoExcelVO;
import com.bone.infra.controller.admin.test.vo.TestDemoRespVO;
import com.bone.infra.controller.admin.test.vo.TestDemoUpdateReqVO;
import com.bone.infra.dal.dataobject.test.TestDemoDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:27:59+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class TestDemoConvertImpl implements TestDemoConvert {

    @Override
    public TestDemoDO convert(TestDemoCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        TestDemoDO.TestDemoDOBuilder testDemoDO = TestDemoDO.builder();

        testDemoDO.name( bean.getName() );
        testDemoDO.status( bean.getStatus() );
        testDemoDO.type( bean.getType() );
        testDemoDO.category( bean.getCategory() );
        testDemoDO.remark( bean.getRemark() );

        return testDemoDO.build();
    }

    @Override
    public TestDemoDO convert(TestDemoUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        TestDemoDO.TestDemoDOBuilder testDemoDO = TestDemoDO.builder();

        testDemoDO.id( bean.getId() );
        testDemoDO.name( bean.getName() );
        testDemoDO.status( bean.getStatus() );
        testDemoDO.type( bean.getType() );
        testDemoDO.category( bean.getCategory() );
        testDemoDO.remark( bean.getRemark() );

        return testDemoDO.build();
    }

    @Override
    public TestDemoRespVO convert(TestDemoDO bean) {
        if ( bean == null ) {
            return null;
        }

        TestDemoRespVO testDemoRespVO = new TestDemoRespVO();

        testDemoRespVO.setName( bean.getName() );
        testDemoRespVO.setStatus( bean.getStatus() );
        testDemoRespVO.setType( bean.getType() );
        testDemoRespVO.setCategory( bean.getCategory() );
        testDemoRespVO.setRemark( bean.getRemark() );
        testDemoRespVO.setId( bean.getId() );
        testDemoRespVO.setCreateTime( bean.getCreateTime() );

        return testDemoRespVO;
    }

    @Override
    public List<TestDemoRespVO> convertList(List<TestDemoDO> list) {
        if ( list == null ) {
            return null;
        }

        List<TestDemoRespVO> list1 = new ArrayList<TestDemoRespVO>( list.size() );
        for ( TestDemoDO testDemoDO : list ) {
            list1.add( convert( testDemoDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<TestDemoRespVO> convertPage(PageResult<TestDemoDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<TestDemoRespVO> pageResult = new PageResult<TestDemoRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public List<TestDemoExcelVO> convertList02(List<TestDemoDO> list) {
        if ( list == null ) {
            return null;
        }

        List<TestDemoExcelVO> list1 = new ArrayList<TestDemoExcelVO>( list.size() );
        for ( TestDemoDO testDemoDO : list ) {
            list1.add( testDemoDOToTestDemoExcelVO( testDemoDO ) );
        }

        return list1;
    }

    protected TestDemoExcelVO testDemoDOToTestDemoExcelVO(TestDemoDO testDemoDO) {
        if ( testDemoDO == null ) {
            return null;
        }

        TestDemoExcelVO testDemoExcelVO = new TestDemoExcelVO();

        testDemoExcelVO.setId( testDemoDO.getId() );
        testDemoExcelVO.setName( testDemoDO.getName() );
        testDemoExcelVO.setStatus( testDemoDO.getStatus() );
        testDemoExcelVO.setType( testDemoDO.getType() );
        testDemoExcelVO.setCategory( testDemoDO.getCategory() );
        testDemoExcelVO.setRemark( testDemoDO.getRemark() );
        testDemoExcelVO.setCreateTime( testDemoDO.getCreateTime() );

        return testDemoExcelVO;
    }
}
