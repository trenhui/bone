package com.bone.tpa.sdk.masterdb.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.tpa.sdk.masterdb.model.TbOverClaimExtend;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 每日赔案报表 Mapper 接口
 * </p>
 *
 * @author wangxiaokun
 * @since 2022-02-24
 */

public interface TbOverClaimExtendMapper extends BaseMapper<TbOverClaimExtend> {

    @Select("select policy_business_mode from review_policy_config where policy_no = #{policyNo}")
    String getPolicyBusinessMode(@Param("policyNo") String policyNo);
}
